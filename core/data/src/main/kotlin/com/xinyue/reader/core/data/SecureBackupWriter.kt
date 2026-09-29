package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupArchiveType
import com.xinyue.reader.core.domain.model.BackupManifest
import com.xinyue.reader.core.domain.model.BackupManifestEntry
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.BackupPhase
import com.xinyue.reader.core.domain.model.BackupProgress
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.zip.UnixStat
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream

data class BackupWrittenArchive(
    val manifest: BackupManifest,
    val archiveSha256: String,
    val bytesWritten: Long,
)

interface BackupArchiveWriter {
    suspend fun write(
        archive: File,
        catalog: BackupCatalogSnapshot,
        assets: List<BackupAsset>,
        options: BackupOptions,
        appVersion: String,
        createdAtEpochMillis: Long,
        onProgress: (BackupProgress) -> Unit = {},
    ): BackupWrittenArchive
}

class SecureBackupWriter(
    private val inventory: BackupAssetInventory,
) : BackupArchiveWriter {
    override suspend fun write(
        archive: File,
        catalog: BackupCatalogSnapshot,
        assets: List<BackupAsset>,
        options: BackupOptions,
        appVersion: String,
        createdAtEpochMillis: Long,
        onProgress: (BackupProgress) -> Unit,
    ): BackupWrittenArchive = writeArchive(
        archive, catalog, assets, options, appVersion, createdAtEpochMillis,
        formatVersion = 2,
        archiveType = BackupArchiveType.BACKUP,
        rootBookId = null,
        onProgress = onProgress,
    )

    suspend fun writeHandoff(
        archive: File,
        catalog: BackupCatalogSnapshot,
        assets: List<BackupAsset>,
        options: BackupOptions,
        appVersion: String,
        createdAtEpochMillis: Long,
        rootBookId: String,
        onProgress: (BackupProgress) -> Unit = {},
    ): BackupWrittenArchive = writeArchive(
        archive, catalog, assets, options, appVersion, createdAtEpochMillis,
        formatVersion = 1,
        archiveType = BackupArchiveType.HANDOFF,
        rootBookId = rootBookId,
        onProgress = onProgress,
    )

    private suspend fun writeArchive(
        archive: File,
        catalog: BackupCatalogSnapshot,
        assets: List<BackupAsset>,
        options: BackupOptions,
        appVersion: String,
        createdAtEpochMillis: Long,
        formatVersion: Int,
        archiveType: BackupArchiveType,
        rootBookId: String?,
        onProgress: (BackupProgress) -> Unit,
    ): BackupWrittenArchive {
        require(!archive.exists() || archive.length() == 0L) { "备份临时文件必须为空" }
        val catalogs = BackupCatalogCodec.encode(catalog)
        val total = catalogs.values.sumOf { it.size.toLong() } + assets.sumOf { it.expectedSize } * 2
        var completed = 0L
        val hashedAssets = assets.sortedBy { it.archivePath }.map { asset ->
            val coroutine = currentCoroutineContext()
            coroutine.ensureActive()
            onProgress(BackupProgress(BackupPhase.HASHING, completed, total, "hashing-start"))
            inventory.hash(asset) { count ->
                coroutine.ensureActive()
                completed += count
                onProgress(BackupProgress(BackupPhase.HASHING, completed, total, "hashing-chunk"))
            }
        }
        val entries = buildList {
            catalogs.forEach { (path, bytes) ->
                add(BackupManifestEntry(path, BackupEntryKind.CATALOG, bytes.size.toLong(), sha256(bytes)))
            }
            hashedAssets.forEach { hashed ->
                add(BackupManifestEntry(hashed.asset.archivePath, hashed.asset.kind, hashed.asset.expectedSize, hashed.sha256))
            }
        }.sortedBy { it.path }
        val manifest = BackupManifest(
            formatVersion = formatVersion,
            archiveType = archiveType,
            rootBookId = rootBookId,
            appVersion = appVersion,
            createdAtEpochMillis = createdAtEpochMillis,
            options = options,
            entries = entries,
        )
        val manifestBytes = BackupManifestCodec.encode(manifest)

        archive.parentFile?.mkdirs()
        ZipArchiveOutputStream(archive).use { output ->
            output.setEncoding("UTF-8")
            writeBytes(output, BackupManifestCodec.MANIFEST_PATH, manifestBytes, stored = false)
            catalogs.forEach { (path, bytes) ->
                writeBytes(output, path, bytes, stored = false)
                completed += bytes.size
                onProgress(BackupProgress(BackupPhase.WRITING, completed, total, "writing-catalog"))
            }
            hashedAssets.forEach { hashed ->
                currentCoroutineContext().ensureActive()
                onProgress(BackupProgress(BackupPhase.WRITING, completed, total, "writing-start"))
                writeAsset(output, hashed) { count ->
                    completed += count
                    onProgress(BackupProgress(BackupPhase.WRITING, completed, total, "writing-chunk"))
                }
            }
            output.finish()
        }
        onProgress(BackupProgress(BackupPhase.VALIDATING, completed, total, "validating-archive"))
        validateWrittenArchive(archive, manifest)
        return BackupWrittenArchive(manifest, sha256(archive), archive.length())
    }

    private suspend fun writeAsset(
        output: ZipArchiveOutputStream,
        hashed: HashedBackupAsset,
        onBytes: (Long) -> Unit,
    ) {
        val stored = hashed.asset.kind == BackupEntryKind.CUSTOM_COVER || hashed.asset.kind == BackupEntryKind.FONT
        val entry = regularEntry(hashed.asset.archivePath, stored)
        if (stored) {
            entry.size = hashed.asset.expectedSize
            entry.compressedSize = hashed.asset.expectedSize
            entry.crc = hashed.crc32
        }
        output.putArchiveEntry(entry)
        val digest = MessageDigest.getInstance("SHA-256")
        var observed = 0L
        inventory.open(hashed.asset).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                observed += count
                if (observed > hashed.asset.expectedSize) throw BackupSourceChangedException()
                digest.update(buffer, 0, count)
                output.write(buffer, 0, count)
                onBytes(count.toLong())
            }
        }
        output.closeArchiveEntry()
        if (observed != hashed.asset.expectedSize || hashed.asset.sourceFile.length() != hashed.asset.expectedSize || digest.digest().hex() != hashed.sha256) {
            throw BackupSourceChangedException()
        }
    }

    private fun writeBytes(output: ZipArchiveOutputStream, path: String, bytes: ByteArray, stored: Boolean) {
        val entry = regularEntry(path, stored)
        if (stored) {
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            entry.crc = java.util.zip.CRC32().also { it.update(bytes) }.value
        }
        output.putArchiveEntry(entry)
        output.write(bytes)
        output.closeArchiveEntry()
    }

    private fun regularEntry(path: String, stored: Boolean) = ZipArchiveEntry(path).apply {
        time = 0L
        method = if (stored) ZipEntry.STORED else ZipEntry.DEFLATED
        unixMode = UnixStat.FILE_FLAG or 0x1A4
    }

    private fun validateWrittenArchive(archive: File, expected: BackupManifest) {
        ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().toList()
            require(entries.isNotEmpty() && entries.first().name == BackupManifestCodec.MANIFEST_PATH) { "备份自检失败" }
            require(entries.none { it.isDirectory } && entries.map { it.name }.toSet().size == entries.size) { "备份自检失败" }
            require(entries.map { it.name }.toSet() == expected.entries.map { it.path }.toSet() + BackupManifestCodec.MANIFEST_PATH) { "备份自检失败" }
            val decoded = zip.getInputStream(zip.getEntry(BackupManifestCodec.MANIFEST_PATH)).use { BackupManifestCodec.decode(it.readBytes()) }
            require(decoded == expected) { "备份自检失败" }
            expected.entries.forEach { item ->
                val entry = zip.getEntry(item.path) ?: throw IOException("备份自检失败")
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        require(count <= item.uncompressedSize) { "备份自检失败" }
                        digest.update(buffer, 0, read)
                    }
                }
                require(count == item.uncompressedSize && digest.digest().hex() == item.sha256) { "备份自检失败" }
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    private fun sha256(file: File): String = file.inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        digest.digest().hex()
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}

class BackupSourceChangedException : IOException("数据在备份过程中发生变化，请重试")
