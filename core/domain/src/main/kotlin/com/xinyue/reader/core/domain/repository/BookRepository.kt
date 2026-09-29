package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

/**
 * Stable domain boundary for book metadata and durable reading progress.
 *
 * The repository owns database state, not private TXT bytes. File publication and compensation are
 * coordinated by `core:data` before or around these calls. Progress anchors use normalized-content
 * UTF-16 offsets rather than page numbers.
 */
interface BookRepository {
    /** Observes the current library. */
    fun observeBooks(): Flow<List<Book>>

    /** Observes durable progress for all books. */
    fun observeProgress(): Flow<List<ReadingProgress>>

    /** Inserts [book]; duplicate identity or content policy is handled by the importing use case. */
    suspend fun addBook(book: Book)

    /** Returns [bookId], or `null` when it does not exist. */
    suspend fun getBook(bookId: String): Book?

    /** Finds a book by normalized-content SHA-256 for duplicate detection. */
    suspend fun findBySha256(contentSha256: String): Book?

    /** Upserts the stable reading position for one book. */
    suspend fun saveProgress(progress: ReadingProgress)

    /** Returns durable progress for [bookId], or `null` before the first save. */
    suspend fun getProgress(bookId: String): ReadingProgress?

    suspend fun renameBook(bookId: String, title: String)

    /**
     * Updates user-editable descriptive metadata without changing content identity or reading state.
     *
     * Implementations must normalize blank optional fields to `null` and reject invalid series orders.
     */
    suspend fun updateBookMetadata(
        bookId: String,
        title: String,
        author: String?,
        seriesName: String?,
        seriesOrder: Int?,
    ) {
        error("当前书籍仓库不支持编辑系列信息")
    }

    /** Deletes one database record and durably queues its private files for cleanup. */
    suspend fun deleteBook(bookId: String)

    /**
     * Deletes multiple records with the same cleanup guarantee as [deleteBook].
     * The default implementation fails explicitly so unsupported implementations cannot silently skip work.
     */
    suspend fun deleteBooks(bookIds: Set<String>) {
        error("当前书籍仓库不支持批量删除")
    }

    /** Replaces content metadata while preserving the stable identity of [existingBookId]. */
    suspend fun replaceBook(existingBookId: String, replacement: Book)

    /** Records the latest successful open time without changing reading progress. */
    suspend fun markOpened(bookId: String, epochMillis: Long)

    /**
     * Changes the finished flag for [bookIds].
     * The default implementation fails explicitly when an implementation lacks batch support.
     */
    suspend fun markFinished(bookIds: Set<String>, finished: Boolean) {
        error("当前书籍仓库不支持完成状态")
    }
}
