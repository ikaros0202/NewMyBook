package com.xinyue.reader.core.data

import android.content.Context
import com.xinyue.reader.core.database.dao.ImportedFontDao
import com.xinyue.reader.core.database.entity.ImportedFontEntity
import com.xinyue.reader.core.domain.model.FontRemovalResult
import com.xinyue.reader.core.domain.model.ImportSource
import com.xinyue.reader.core.domain.model.ImportedFont
import com.xinyue.reader.core.domain.repository.ImportedFontRepository
import com.xinyue.reader.core.domain.time.EpochClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

@Singleton
class LocalImportedFontRepository private constructor(
    private val rootDirectory: File,
    private val dao: ImportedFontDao,
    private val validator: FontValidator,
    private val referenceCounter: FontReferenceCounter,
    private val idFactory: () -> String,
    private val clock: EpochClock,
    private val fileStore: ImportedFontFileStore?,
) : ImportedFontRepository {
    private val storageMutex = Mutex()
    @Inject
    constructor(
        @ApplicationContext context: Context,
        dao: ImportedFontDao,
        validator: FontValidator,
        referenceCounter: FontReferenceCounter,
        clock: EpochClock,
        fileStore: ImportedFontFileStore,
    ) : this(
        context.filesDir,
        dao,
        validator,
        referenceCounter,
        { UUID.randomUUID().toString() },
        clock,
        fileStore,
    )

    internal constructor(
        rootDirectory: File,
        dao: ImportedFontDao,
        validator: FontValidator,
        referenceCounter: FontReferenceCounter,
        idFactory: () -> String,
        clock: EpochClock,
    ) : this(rootDirectory, dao, validator, referenceCounter, idFactory, clock, null)

    override fun observeAll(): Flow<List<ImportedFont>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun importFont(source: ImportSource): ImportedFont = storageMutex.withLock {
        withContext(Dispatchers.IO) {
        validateSource(source)
        val stagingDirectory = File(rootDirectory, "$STAGING_DIRECTORY/${UUID.randomUUID()}")
        val stagingFile = File(stagingDirectory, FONT_FILE_NAME)
        try {
            check(stagingDirectory.mkdirs()) { "Unable to create font staging directory" }
            val (sizeBytes, sha256) = copyBoundedAndHash(source, stagingFile)
            require(sizeBytes > 0) { "Font file is empty" }
            dao.findBySha256(sha256)?.let { return@withContext it.toDomain() }
            validator.validate(stagingFile)

            val fontId = idFactory()
            require(fontId.isNotBlank()) { "Generated font ID is blank" }
            val finalDirectory = File(rootDirectory, "${ImportedFontFileStore.FONTS_DIRECTORY}/$fontId")
            val finalFile = File(finalDirectory, FONT_FILE_NAME)
            check(finalDirectory.mkdirs()) { "Unable to create private font directory" }
            try {
                moveAtomically(stagingFile, finalFile)
            } catch (failure: Throwable) {
                finalDirectory.deleteRecursively()
                throw failure
            }
            val entity = ImportedFontEntity(
                id = fontId,
                displayName = source.displayName.trim(),
                privateRelativePath = "${ImportedFontFileStore.FONTS_DIRECTORY}/$fontId/$FONT_FILE_NAME",
                contentSha256 = sha256,
                sizeBytes = sizeBytes,
                createdAtEpochMillis = clock.nowEpochMillis(),
            )
            try {
                dao.insert(entity)
            } catch (failure: Throwable) {
                finalDirectory.deleteRecursively()
                throw failure
            }
            fileStore?.record(entity)
            entity.toDomain()
        } finally {
            stagingDirectory.deleteRecursively()
        }
        }
    }

    override suspend fun remove(fontId: String): FontRemovalResult = storageMutex.withLock {
        withContext(Dispatchers.IO) {
        val entity = dao.get(fontId) ?: return@withContext FontRemovalResult.NotFound
        val referenceCount = referenceCounter.count(fontId)
        if (referenceCount > 0) return@withContext FontRemovalResult.InUse(referenceCount)

        val fontDirectory = resolveSafeFontDirectory(entity)
        val trashDirectory = File(rootDirectory, "$TRASH_DIRECTORY/${UUID.randomUUID()}")
        if (fontDirectory?.exists() == true) {
            trashDirectory.parentFile?.mkdirs()
            moveAtomically(fontDirectory, trashDirectory)
        }
        try {
            dao.delete(fontId)
        } catch (failure: Throwable) {
            if (trashDirectory.exists() && fontDirectory != null) {
                fontDirectory.parentFile?.mkdirs()
                moveAtomically(trashDirectory, fontDirectory)
            }
            throw failure
        }
        trashDirectory.deleteRecursively()
        fileStore?.forget(fontId)
        FontRemovalResult.Removed
        }
    }

    private fun validateSource(source: ImportSource) {
        require(source.displayName.isNotBlank()) { "Font name is blank" }
        val extension = source.displayName.substringAfterLast('.', "").lowercase()
        require(extension in SUPPORTED_EXTENSIONS) { "Only TTF, OTF, and TTC fonts are supported" }
        require(
            source.sizeBytes == ImportSource.UNKNOWN_SIZE_BYTES || source.sizeBytes in 0..MAX_FONT_BYTES,
        ) { "Font is larger than 64 MiB" }
    }

    private suspend fun copyBoundedAndHash(source: ImportSource, target: File): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        source.openStream().use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read
                    require(total <= MAX_FONT_BYTES) { "Font is larger than 64 MiB" }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
        }
        return total to digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun resolveSafeFontDirectory(entity: ImportedFontEntity): File? {
        val fontsRoot = File(rootDirectory, ImportedFontFileStore.FONTS_DIRECTORY).canonicalFile
        val file = File(rootDirectory, entity.privateRelativePath).canonicalFile
        if (!file.toPath().startsWith(fontsRoot.toPath())) return null
        return file.parentFile?.takeIf { it.toPath().startsWith(fontsRoot.toPath()) }
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (failure: AtomicMoveNotSupportedException) {
            throw IllegalStateException("Private font storage does not support atomic publish", failure)
        }
    }

    private fun ImportedFontEntity.toDomain() = ImportedFont(
        id = id,
        displayName = displayName,
        contentSha256 = contentSha256,
        sizeBytes = sizeBytes,
        createdAtEpochMillis = createdAtEpochMillis,
    )

    companion object {
        const val MAX_FONT_BYTES = 64L * 1024L * 1024L
        private const val STAGING_DIRECTORY = "font-staging"
        private const val TRASH_DIRECTORY = "font-trash"
        private const val FONT_FILE_NAME = "font.bin"
        private val SUPPORTED_EXTENSIONS = setOf("ttf", "otf", "ttc")
    }
}
