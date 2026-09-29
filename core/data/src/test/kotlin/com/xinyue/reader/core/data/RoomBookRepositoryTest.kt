package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.dao.BookDao
import com.xinyue.reader.core.database.dao.ReadingProgressDao
import com.xinyue.reader.core.database.entity.BookEntity
import com.xinyue.reader.core.database.entity.ReadingProgressEntity
import com.xinyue.reader.core.database.entity.PendingFileCleanupEntity
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReadingProgress
import com.xinyue.reader.core.domain.model.TextAnchor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomBookRepositoryTest {
    @Test
    fun `repository exposes domain books and persists stable progress`() = runTest {
        val bookDao = FakeBookDao()
        val progressDao = FakeProgressDao()
        val repository = RoomBookRepository(bookDao, progressDao)
        val book = sampleBook()

        repository.addBook(book)
        repository.saveProgress(
            ReadingProgress(
                bookId = book.id,
                anchor = TextAnchor(offset = 72, contextHash = "context"),
                contentLength = book.contentLength,
                updatedAtEpochMillis = 99,
            ),
        )

        assertThat(repository.observeBooks().first().single().title).isEqualTo("测试小说")
        assertThat(repository.getProgress(book.id)?.anchor?.offset).isEqualTo(72)
    }

    @Test
    fun `repository deletion queues the exact private file paths before removing the book`() = runTest {
        val bookDao = FakeBookDao()
        val repository = RoomBookRepository(bookDao, FakeProgressDao())
        val book = sampleBook()
        repository.addBook(book)

        repository.deleteBook(book.id)

        assertThat(repository.getBook(book.id)).isNull()
        assertThat(bookDao.queuedCleanups).hasSize(1)
        assertThat(bookDao.queuedCleanups.single().originalPath).isEqualTo(book.originalPath)
        assertThat(bookDao.queuedCleanups.single().normalizedPath).isEqualTo(book.normalizedPath)
    }

    @Test
    fun `batch deletion queues every exact private directory before removing all books`() = runTest {
        val bookDao = FakeBookDao()
        val repository = RoomBookRepository(bookDao, FakeProgressDao())
        repository.addBook(sampleBook().copy(id = "book-1"))
        repository.addBook(
            sampleBook().copy(
                id = "book-2",
                originalPath = "books/book-2/original.txt",
                normalizedPath = "books/book-2/content.txt",
            ),
        )

        repository.deleteBooks(setOf("book-1", "book-2"))

        assertThat(repository.observeBooks().first()).isEmpty()
        assertThat(bookDao.queuedCleanups.map { it.bookId }).containsExactly("book-1", "book-2")
    }

    @Test
    fun `duplicate replacement retains library organization and custom cover`() = runTest {
        val bookDao = FakeBookDao()
        val repository = RoomBookRepository(bookDao, FakeProgressDao())
        val existing = sampleBook().copy(
            groupId = "group-1",
            customCoverPath = "books/book-1/cover/thumb.webp",
            finished = true,
        )
        repository.addBook(existing)

        repository.replaceBook(
            existingBookId = existing.id,
            replacement = sampleBook().copy(
                id = "temporary-import-id",
                title = "替换后的书名",
                contentSha256 = "replacement-hash",
                contentLength = 400,
            ),
        )

        assertThat(repository.getBook(existing.id)).isEqualTo(
            sampleBook().copy(
                title = "替换后的书名",
                contentSha256 = "replacement-hash",
                contentLength = 400,
                groupId = "group-1",
                customCoverPath = "books/book-1/cover/thumb.webp",
                finished = true,
            ),
        )
    }

    @Test
    fun `mark finished validates and updates the exact batch`() = runTest {
        val bookDao = FakeBookDao()
        val repository = RoomBookRepository(bookDao, FakeProgressDao())
        repository.addBook(sampleBook().copy(id = "book-1"))
        repository.addBook(sampleBook().copy(id = "book-2"))

        repository.markFinished(setOf("book-1", "book-2"), true)

        assertThat(repository.getBook("book-1")!!.finished).isTrue()
        assertThat(repository.getBook("book-2")!!.finished).isTrue()
    }

    @Test
    fun `updates editable title author and series metadata together`() = runTest {
        val bookDao = FakeBookDao()
        val repository = RoomBookRepository(bookDao, FakeProgressDao())
        repository.addBook(sampleBook())

        repository.updateBookMetadata(
            bookId = "book-1",
            title = "长夜列车",
            author = "林川",
            seriesName = "星海纪事",
            seriesOrder = 3,
        )

        assertThat(repository.getBook("book-1")).isEqualTo(
            sampleBook().copy(
                title = "长夜列车",
                author = "林川",
                seriesName = "星海纪事",
                seriesOrder = 3,
            ),
        )
    }

    private class FakeBookDao : BookDao {
        private val books = MutableStateFlow<List<BookEntity>>(emptyList())
        val queuedCleanups = mutableListOf<PendingFileCleanupEntity>()

        override suspend fun insert(book: BookEntity) {
            books.value = books.value + book
        }

        override fun observeAll(): Flow<List<BookEntity>> = books

        override suspend fun getAllForBackup(): List<BookEntity> = books.value

        override suspend fun get(bookId: String): BookEntity? = books.value.firstOrNull { it.id == bookId }

        override suspend fun getMany(bookIds: Set<String>): List<BookEntity> =
            books.value.filter { it.id in bookIds }

        override suspend fun findBySha256(contentSha256: String): BookEntity? =
            books.value.firstOrNull { it.contentSha256 == contentSha256 }

        override suspend fun countByIds(bookIds: Set<String>): Int = books.value.count { it.id in bookIds }

        override suspend fun rename(bookId: String, title: String) {
            books.value = books.value.map { if (it.id == bookId) it.copy(title = title) else it }
        }

        override suspend fun setCustomCoverPath(bookId: String, customCoverPath: String?): Int {
            val exists = books.value.any { it.id == bookId }
            books.value = books.value.map {
                if (it.id == bookId) it.copy(customCoverPath = customCoverPath) else it
            }
            return if (exists) 1 else 0
        }

        override suspend fun updateFinished(bookIds: Set<String>, finished: Boolean) {
            books.value = books.value.map { if (it.id in bookIds) it.copy(finished = finished) else it }
        }

        override suspend fun delete(bookId: String) {
            books.value = books.value.filterNot { it.id == bookId }
        }

        override suspend fun deleteMany(bookIds: Set<String>) {
            books.value = books.value.filterNot { it.id in bookIds }
        }

        override suspend fun queueFileCleanup(cleanup: PendingFileCleanupEntity) {
            queuedCleanups += cleanup
        }

        override suspend fun update(book: BookEntity) {
            books.value = books.value.map { if (it.id == book.id) book else it }
        }

        override suspend fun deleteChapterIndex(bookId: String) = Unit

        override suspend fun markOpened(bookId: String, epochMillis: Long) {
            books.value = books.value.map {
                if (it.id == bookId) it.copy(lastOpenedAtEpochMillis = epochMillis) else it
            }
        }
    }

    private class FakeProgressDao : ReadingProgressDao {
        private val progressByBook = mutableMapOf<String, ReadingProgressEntity>()

        override fun observeAll(): Flow<List<ReadingProgressEntity>> =
            kotlinx.coroutines.flow.flowOf(progressByBook.values.toList())

        override suspend fun getAllForBackup(): List<ReadingProgressEntity> = progressByBook.values.toList()

        override suspend fun upsert(progress: ReadingProgressEntity) {
            progressByBook[progress.bookId] = progress
        }

        override suspend fun get(bookId: String): ReadingProgressEntity? = progressByBook[bookId]
    }

    private fun sampleBook() = Book(
        id = "book-1",
        title = "测试小说",
        author = null,
        originalFileName = "测试.txt",
        originalPath = "books/book-1/original.txt",
        normalizedPath = "books/book-1/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "hash",
        contentLength = 200,
        createdAtEpochMillis = 10,
        lastOpenedAtEpochMillis = null,
    )
}
