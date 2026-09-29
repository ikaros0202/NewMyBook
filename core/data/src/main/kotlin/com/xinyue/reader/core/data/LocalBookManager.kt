package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.repository.BookRepository
import javax.inject.Inject

class LocalBookManager @Inject constructor(
    private val repository: BookRepository,
    private val cleanupScheduler: BookFileCleanupScheduler,
) : BookManager {
    override suspend fun get(bookId: String) = repository.getBook(bookId)

    override suspend fun rename(bookId: String, title: String) {
        val book = repository.getBook(bookId) ?: error("找不到这本书")
        updateMetadata(bookId, title, book.author, book.seriesName, book.seriesOrder)
    }

    override suspend fun updateMetadata(bookId: String, title: String, author: String?) {
        val book = repository.getBook(bookId) ?: error("找不到这本书")
        updateMetadata(bookId, title, author, book.seriesName, book.seriesOrder)
    }

    override suspend fun updateMetadata(
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
        require(normalizedTitle.length <= 100) { "书名不能超过 100 个字符" }
        require((normalizedAuthor?.length ?: 0) <= 80) { "作者名不能超过 80 个字符" }
        require((normalizedSeriesName?.length ?: 0) <= 100) { "系列名称不能超过 100 个字符" }
        repository.updateBookMetadata(
            bookId = bookId,
            title = normalizedTitle,
            author = normalizedAuthor,
            seriesName = normalizedSeriesName,
            seriesOrder = seriesOrder,
        )
    }

    override suspend fun delete(bookId: String) {
        if (repository.getBook(bookId) == null) return
        deleteBatch(setOf(bookId))
    }

    override suspend fun deleteBatch(bookIds: Set<String>) {
        if (bookIds.isEmpty()) return
        repository.deleteBooks(bookIds)
        runCatching(cleanupScheduler::enqueue)
    }

    override suspend fun markFinished(bookIds: Set<String>, finished: Boolean) {
        repository.markFinished(bookIds, finished)
    }
}
