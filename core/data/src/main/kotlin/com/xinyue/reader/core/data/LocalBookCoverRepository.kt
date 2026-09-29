package com.xinyue.reader.core.data

import android.content.Context
import com.xinyue.reader.core.database.dao.BookDao
import com.xinyue.reader.core.database.entity.BookEntity
import com.xinyue.reader.core.domain.model.ImportSource
import com.xinyue.reader.core.domain.repository.BookCoverRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class LocalBookCoverRepository internal constructor(
    rootDirectory: File,
    private val bookDao: BookDao,
    private val decoder: CoverDecoder,
    private val ioDispatcher: CoroutineDispatcher,
) : BookCoverRepository {
    private val root = rootDirectory.canonicalFile
    private val booksRoot = File(root, BOOKS_DIRECTORY).canonicalFile
    private val storageMutex = Mutex()

    @Inject
    constructor(
        @ApplicationContext context: Context,
        bookDao: BookDao,
        decoder: AndroidCoverDecoder,
    ) : this(context.filesDir, bookDao, decoder, Dispatchers.IO)

    override suspend fun importCover(bookId: String, source: ImportSource): String = storageMutex.withLock {
        withContext(ioDispatcher) {
            validateSource(source)
            val book = requireNotNull(bookDao.get(bookId)) { "找不到这本书" }
            val bookDirectory = safeBookDirectory(book)
            check(bookDirectory.exists() || bookDirectory.mkdirs()) { "无法创建封面私有目录" }
            val token = UUID.randomUUID().toString()
            val sourceStage = File(bookDirectory, ".cover-$token-source.tmp")
            val encodedStage = File(bookDirectory, ".cover-$token-encoded.tmp")
            val previousBackup = File(bookDirectory, ".cover-$token-previous.tmp")
            var publishedTarget: File? = null
            var replacementTarget: File? = null
            var previousWasBackedUp = false
            try {
                val copiedBytes = copyBounded(source, sourceStage)
                require(copiedBytes > 0) { "封面文件不能为空" }
                val encoding = decoder.decode(sourceStage, encodedStage)
                require(encodedStage.isFile && encodedStage.length() > 0) { "封面编码结果为空" }

                val target = File(bookDirectory, "cover.${encoding.extension}")
                replacementTarget = target
                val previous = resolveSafeCover(book.id, book.customCoverPath)
                if (target.exists()) {
                    moveAtomically(target, previousBackup)
                    previousWasBackedUp = true
                }
                moveAtomically(encodedStage, target)
                publishedTarget = target
                val relativePath = target.relativeTo(root).invariantSeparatorsPath
                try {
                    check(bookDao.setCustomCoverPath(book.id, relativePath) == 1) { "无法保存封面引用" }
                } catch (failure: Throwable) {
                    target.delete()
                    if (previousWasBackedUp && previousBackup.exists()) {
                        moveAtomically(previousBackup, target)
                        previousWasBackedUp = false
                    }
                    throw failure
                }

                if (previous != null && previous != target) deleteWithRetries(previous)
                if (previousWasBackedUp) deleteWithRetries(previousBackup)
                deleteUnreferencedCover(bookDirectory, target)
                relativePath
            } catch (failure: Throwable) {
                val target = replacementTarget
                if (target != null && previousWasBackedUp && previousBackup.exists()) {
                    publishedTarget?.delete()
                    moveAtomically(previousBackup, target)
                    previousWasBackedUp = false
                }
                throw failure
            } finally {
                sourceStage.delete()
                encodedStage.delete()
                if (previousWasBackedUp) previousBackup.delete()
            }
        }
    }

    override suspend fun clearCover(bookId: String) = storageMutex.withLock {
        withContext(ioDispatcher) {
            val book = requireNotNull(bookDao.get(bookId)) { "找不到这本书" }
            val previous = resolveSafeCover(book.id, book.customCoverPath)
            check(bookDao.setCustomCoverPath(book.id, null) == 1) { "无法清除封面引用" }
            previous?.let(::deleteWithRetries)
            Unit
        }
    }

    private fun validateSource(source: ImportSource) {
        require(
            source.sizeBytes == ImportSource.UNKNOWN_SIZE_BYTES || source.sizeBytes in 1..MAX_COVER_BYTES,
        ) { if (source.sizeBytes == 0L) "封面文件不能为空" else "封面文件不能超过 20 MiB" }
    }

    private suspend fun copyBounded(source: ImportSource, target: File): Long {
        var total = 0L
        source.openStream().use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total += count
                    require(total <= MAX_COVER_BYTES) { "封面文件不能超过 20 MiB" }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        return total
    }

    private fun safeBookDirectory(book: BookEntity): File {
        val expected = File(booksRoot, book.id).canonicalFile
        require(expected.toPath().startsWith(booksRoot.toPath())) { "非法书籍封面目录" }
        val original = File(root, book.originalPath).canonicalFile
        require(original.parentFile == expected) { "书籍私有路径与书籍 ID 不一致" }
        return expected
    }

    private fun resolveSafeCover(bookId: String, relativePath: String?): File? {
        relativePath ?: return null
        val expectedDirectory = File(booksRoot, bookId).canonicalFile
        val resolved = File(root, relativePath).canonicalFile
        val allowed = setOf(File(expectedDirectory, "cover.webp"), File(expectedDirectory, "cover.png"))
            .map(File::getCanonicalFile)
        return resolved.takeIf { it in allowed && it.toPath().startsWith(expectedDirectory.toPath()) }
    }

    private fun deleteUnreferencedCover(bookDirectory: File, current: File) {
        listOf(File(bookDirectory, "cover.webp"), File(bookDirectory, "cover.png"))
            .filter { it != current }
            .forEach(::deleteWithRetries)
    }

    private fun deleteWithRetries(file: File): Boolean {
        repeat(FILE_DELETE_ATTEMPTS) {
            if (!file.exists() || file.delete()) return true
            Thread.yield()
        }
        return !file.exists()
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (failure: AtomicMoveNotSupportedException) {
            throw IllegalStateException("私有封面目录不支持原子发布", failure)
        }
    }

    companion object {
        const val MAX_COVER_BYTES = 20L * 1024L * 1024L
        private const val BOOKS_DIRECTORY = "books"
        private const val FILE_DELETE_ATTEMPTS = 3
    }
}
