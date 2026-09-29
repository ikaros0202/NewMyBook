package com.xinyue.reader.core.data

import com.xinyue.reader.core.database.dao.BookDao
import com.xinyue.reader.core.database.dao.ReadingProgressDao
import com.xinyue.reader.core.database.entity.BookEntity
import com.xinyue.reader.core.database.mapper.toDomain
import com.xinyue.reader.core.database.mapper.toEntity
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReadingProgress
import com.xinyue.reader.core.domain.repository.BookRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomBookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val coverModelResolver: BookCoverModelResolver? = null,
) : BookRepository {
    override fun observeBooks(): Flow<List<Book>> =
        bookDao.observeAll().map { books -> books.map { it.toSafeDomain() } }

    override fun observeProgress(): Flow<List<ReadingProgress>> =
        readingProgressDao.observeAll().map { progress -> progress.map { it.toDomain() } }

    override suspend fun addBook(book: Book) {
        bookDao.insert(book.toEntity())
    }

    override suspend fun getBook(bookId: String): Book? = bookDao.get(bookId)?.toSafeDomain()

    override suspend fun findBySha256(contentSha256: String): Book? =
        bookDao.findBySha256(contentSha256)?.toSafeDomain()

    override suspend fun saveProgress(progress: ReadingProgress) {
        readingProgressDao.upsert(progress.toEntity())
    }

    override suspend fun getProgress(bookId: String): ReadingProgress? =
        readingProgressDao.get(bookId)?.toDomain()

    override suspend fun renameBook(bookId: String, title: String) {
        bookDao.rename(bookId, title)
    }

    override suspend fun updateBookMetadata(
        bookId: String,
        title: String,
        author: String?,
        seriesName: String?,
        seriesOrder: Int?,
    ) {
        val normalizedTitle = title.trim()
        val normalizedAuthor = author?.trim()?.takeIf(String::isNotEmpty)
        val normalizedSeriesName = seriesName?.trim()?.takeIf(String::isNotEmpty)
        require(normalizedTitle.isNotEmpty()) { "书名不能为空" }
        require(seriesOrder == null || seriesOrder in 0..9_999) { "系列序号必须介于 0 和 9999 之间" }
        require(normalizedSeriesName != null || seriesOrder == null) { "设置系列序号前必须填写系列名称" }
        bookDao.updateMetadata(
            bookId = bookId,
            title = normalizedTitle,
            author = normalizedAuthor,
            seriesName = normalizedSeriesName,
            seriesOrder = seriesOrder,
        )
    }

    override suspend fun deleteBook(bookId: String) {
        bookDao.deleteAndQueueFileCleanup(bookId, System.currentTimeMillis())
    }

    override suspend fun deleteBooks(bookIds: Set<String>) {
        bookDao.deleteManyAndQueueFileCleanup(bookIds, System.currentTimeMillis())
    }

    override suspend fun replaceBook(existingBookId: String, replacement: Book) {
        val existing = bookDao.get(existingBookId) ?: return
        bookDao.updateAndInvalidateChangedContent(
            replacement.copy(
                id = existingBookId,
                groupId = existing.groupId,
                customCoverPath = existing.customCoverPath,
                finished = existing.finished,
            ).toEntity(),
        )
    }

    override suspend fun markOpened(bookId: String, epochMillis: Long) {
        bookDao.markOpened(bookId, epochMillis)
    }

    override suspend fun markFinished(bookIds: Set<String>, finished: Boolean) {
        bookDao.markFinished(bookIds, finished)
    }

    private fun BookEntity.toSafeDomain(): Book = toDomain().copy(
        customCoverModel = coverModelResolver?.resolve(this),
    )
}
