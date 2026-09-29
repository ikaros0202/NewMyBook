package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.dao.BookDao
import com.xinyue.reader.core.database.entity.BookEntity
import com.xinyue.reader.core.database.entity.PendingFileCleanupEntity
import com.xinyue.reader.core.domain.model.ImportSource
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.FileInputStream
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

class LocalBookCoverRepositoryTest {
    @Test
    fun `actual reads are capped at twenty MiB and zero byte sources are rejected`() = runTest {
        val root = Files.createTempDirectory("cover-limit").toFile()
        val dao = FakeBookDao(sampleBook())
        val decoder = FakeCoverDecoder()
        val repository = LocalBookCoverRepository(root, dao, decoder, UnconfinedTestDispatcher(testScheduler))

        assertFailsWith<IllegalArgumentException> {
            repository.importCover("book-1", source(size = 0, bytes = 0))
        }
        assertFailsWith<IllegalArgumentException> {
            repository.importCover(
                "book-1",
                ImportSource("oversized.png", ImportSource.UNKNOWN_SIZE_BYTES) {
                    RepeatingInputStream(LocalBookCoverRepository.MAX_COVER_BYTES + 1)
                },
            )
        }

        assertThat(decoder.decodeCount).isEqualTo(0)
        assertThat(root.walkTopDown().filter { it.name.startsWith(".cover-") }.toList()).isEmpty()
        root.deleteRecursively()
    }

    @Test
    fun `valid cover is privately encoded and all staging files are removed`() = runTest {
        val root = Files.createTempDirectory("cover-success").toFile()
        val dao = FakeBookDao(sampleBook())
        val decoder = FakeCoverDecoder(encodedText = "encoded-cover")
        val repository = LocalBookCoverRepository(root, dao, decoder, UnconfinedTestDispatcher(testScheduler))

        val path = repository.importCover("book-1", source(size = 8, bytes = 8))

        assertThat(path).isEqualTo("books/book-1/cover.webp")
        assertThat(File(root, path).readText()).isEqualTo("encoded-cover")
        assertThat(dao.book.customCoverPath).isEqualTo(path)
        assertThat(root.walkTopDown().filter { it.name.startsWith(".cover-") }.toList()).isEmpty()
        root.deleteRecursively()
    }

    @Test
    fun `private cover survives source deletion and a new resolver instance`() = runTest {
        val root = Files.createTempDirectory("cover-restart").toFile()
        val externalSource = Files.createTempFile("cover-source", ".png").toFile().apply {
            writeBytes(ByteArray(8) { 7 })
        }
        val dao = FakeBookDao(sampleBook())
        val repository = LocalBookCoverRepository(
            root,
            dao,
            FakeCoverDecoder(encodedText = "private-copy"),
            UnconfinedTestDispatcher(testScheduler),
        )

        repository.importCover(
            "book-1",
            ImportSource(externalSource.name, externalSource.length()) { FileInputStream(externalSource) },
        )
        assertThat(externalSource.delete()).isTrue()

        val resolvedAfterRestart = BookCoverModelResolver(root).resolve(dao.book)
        assertThat(resolvedAfterRestart).isNotNull()
        assertThat(resolvedAfterRestart!!.readText()).isEqualTo("private-copy")
        root.deleteRecursively()
    }

    @Test
    fun `decode failure and database failure clean staging and restore the previous cover`() = runTest {
        val root = Files.createTempDirectory("cover-rollback").toFile()
        val previousPath = "books/book-1/cover.webp"
        val previousFile = File(root, previousPath).apply {
            parentFile?.mkdirs()
            writeText("old-cover")
        }
        val dao = FakeBookDao(sampleBook().copy(customCoverPath = previousPath))
        val decoder = FakeCoverDecoder(failure = IllegalArgumentException("decode failed"))
        val repository = LocalBookCoverRepository(root, dao, decoder, UnconfinedTestDispatcher(testScheduler))

        assertFailsWith<IllegalArgumentException> {
            repository.importCover("book-1", source(size = 8, bytes = 8))
        }
        assertThat(previousFile.readText()).isEqualTo("old-cover")

        decoder.failure = null
        dao.failCoverUpdate = true
        assertFailsWith<IllegalStateException> {
            repository.importCover("book-1", source(size = 8, bytes = 8))
        }
        assertThat(previousFile.readText()).isEqualTo("old-cover")
        assertThat(dao.book.customCoverPath).isEqualTo(previousPath)
        assertThat(root.walkTopDown().filter { it.name.startsWith(".cover-") }.toList()).isEmpty()
        root.deleteRecursively()
    }

    @Test
    fun `successful replacement removes the old format and clear nulls database before cleanup`() = runTest {
        val root = Files.createTempDirectory("cover-clear").toFile()
        val oldPath = "books/book-1/cover.png"
        val oldFile = File(root, oldPath).apply {
            parentFile?.mkdirs()
            writeText("old-png")
        }
        val dao = FakeBookDao(sampleBook().copy(customCoverPath = oldPath))
        val repository = LocalBookCoverRepository(
            root,
            dao,
            FakeCoverDecoder(encodedText = "new-webp"),
            UnconfinedTestDispatcher(testScheduler),
        )

        repository.importCover("book-1", source(size = 8, bytes = 8))
        assertThat(oldFile.exists()).isFalse()
        repository.clearCover("book-1")

        assertThat(dao.book.customCoverPath).isNull()
        assertThat(File(root, "books/book-1/cover.webp").exists()).isFalse()
        root.deleteRecursively()
    }

    @Test
    fun `cancellation propagates and leaves no staged cover`() = runTest {
        val root = Files.createTempDirectory("cover-cancel").toFile()
        val cancellation = CancellationException("cancel decode")
        val repository = LocalBookCoverRepository(
            root,
            FakeBookDao(sampleBook()),
            FakeCoverDecoder(failure = cancellation),
            UnconfinedTestDispatcher(testScheduler),
        )

        assertThat(assertFailsWith<CancellationException> {
            repository.importCover("book-1", source(size = 8, bytes = 8))
        }).isSameInstanceAs(cancellation)
        assertThat(root.walkTopDown().filter { it.name.startsWith(".cover-") }.toList()).isEmpty()
        root.deleteRecursively()
    }

    private class FakeCoverDecoder(
        var failure: Throwable? = null,
        private val encodedText: String = "encoded",
    ) : CoverDecoder {
        var decodeCount = 0
        override fun decode(source: File, output: File): CoverEncoding {
            decodeCount++
            failure?.let { throw it }
            require(source.length() > 0)
            output.writeText(encodedText)
            return CoverEncoding.WEBP
        }
    }

    private class FakeBookDao(initialBook: BookEntity) : BookDao {
        var book = initialBook
        var failCoverUpdate = false

        override suspend fun setCustomCoverPath(bookId: String, customCoverPath: String?): Int {
            if (failCoverUpdate) error("database update failed")
            if (book.id != bookId) return 0
            book = book.copy(customCoverPath = customCoverPath)
            return 1
        }
        override suspend fun updateFinished(bookIds: Set<String>, finished: Boolean) = Unit

        override suspend fun countByIds(bookIds: Set<String>) = if (book.id in bookIds) 1 else 0
        override suspend fun insert(book: BookEntity) { this.book = book }
        override fun observeAll(): Flow<List<BookEntity>> = flowOf(listOf(book))
        override suspend fun getAllForBackup(): List<BookEntity> = listOf(book)
        override suspend fun get(bookId: String): BookEntity? = book.takeIf { it.id == bookId }
        override suspend fun getMany(bookIds: Set<String>): List<BookEntity> = listOfNotNull(book.takeIf { it.id in bookIds })
        override suspend fun findBySha256(contentSha256: String): BookEntity? = null
        override suspend fun rename(bookId: String, title: String) = Unit
        override suspend fun delete(bookId: String) = Unit
        override suspend fun deleteMany(bookIds: Set<String>) = Unit
        override suspend fun queueFileCleanup(cleanup: PendingFileCleanupEntity) = Unit
        override suspend fun update(book: BookEntity) { this.book = book }
        override suspend fun deleteChapterIndex(bookId: String) = Unit
        override suspend fun markOpened(bookId: String, epochMillis: Long) = Unit
    }

    private fun sampleBook() = BookEntity(
        id = "book-1",
        title = "测试书",
        author = null,
        originalFileName = "fixture.txt",
        originalPath = "books/book-1/original.txt",
        normalizedPath = "books/book-1/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "hash",
        contentLength = 100,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = null,
    )

    private fun source(size: Long, bytes: Int) = ImportSource(
        displayName = "cover.png",
        sizeBytes = size,
        openStream = { ByteArrayInputStream(ByteArray(bytes) { 1 }) },
    )

    private class RepeatingInputStream(private var remaining: Long) : InputStream() {
        override fun read(): Int = if (remaining-- > 0) 0 else -1
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return -1
            val count = minOf(length.toLong(), remaining).toInt()
            buffer.fill(0, offset, offset + count)
            remaining -= count
            return count
        }
    }
}
