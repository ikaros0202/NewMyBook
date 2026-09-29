package com.xinyue.reader.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.xinyue.reader.core.database.entity.BookEntity
import com.xinyue.reader.core.database.entity.PendingFileCleanupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity)

    @Query("SELECT * FROM books ORDER BY COALESCE(lastOpenedAtEpochMillis, createdAtEpochMillis) DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY id")
    suspend fun getAllForBackup(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun get(bookId: String): BookEntity?

    @Query("SELECT * FROM books WHERE id IN (:bookIds)")
    suspend fun getMany(bookIds: Set<String>): List<BookEntity>

    @Query("SELECT * FROM books WHERE contentSha256 = :contentSha256 LIMIT 1")
    suspend fun findBySha256(contentSha256: String): BookEntity?

    @Query("SELECT COUNT(*) FROM books WHERE id IN (:bookIds)")
    suspend fun countByIds(bookIds: Set<String>): Int

    @Query("UPDATE books SET title = :title WHERE id = :bookId")
    suspend fun rename(bookId: String, title: String)

    @Query("UPDATE books SET customCoverPath = :customCoverPath WHERE id = :bookId")
    suspend fun setCustomCoverPath(bookId: String, customCoverPath: String?): Int

    @Query("UPDATE books SET finished = :finished WHERE id IN (:bookIds)")
    suspend fun updateFinished(bookIds: Set<String>, finished: Boolean)

    @Transaction
    suspend fun markFinished(bookIds: Set<String>, finished: Boolean) {
        if (bookIds.isEmpty()) return
        require(countByIds(bookIds) == bookIds.size) { "包含不存在的书籍" }
        updateFinished(bookIds, finished)
    }

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun delete(bookId: String)

    @Query("DELETE FROM books WHERE id IN (:bookIds)")
    suspend fun deleteMany(bookIds: Set<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun queueFileCleanup(cleanup: PendingFileCleanupEntity)

    @Transaction
    suspend fun deleteAndQueueFileCleanup(bookId: String, queuedAtEpochMillis: Long) {
        if (get(bookId) == null) return
        deleteManyAndQueueFileCleanup(setOf(bookId), queuedAtEpochMillis)
    }

    @Transaction
    suspend fun deleteManyAndQueueFileCleanup(bookIds: Set<String>, queuedAtEpochMillis: Long) {
        if (bookIds.isEmpty()) return
        val books = getMany(bookIds)
        require(books.size == bookIds.size) { "部分书籍已经不存在" }
        books.forEach { book ->
            queueFileCleanup(
                PendingFileCleanupEntity(
                    bookId = book.id,
                    originalPath = book.originalPath,
                    normalizedPath = book.normalizedPath,
                    queuedAtEpochMillis = queuedAtEpochMillis,
                ),
            )
        }
        deleteMany(bookIds)
    }

    @Update
    suspend fun update(book: BookEntity)

    @Transaction
    suspend fun updateMetadata(
        bookId: String,
        title: String,
        author: String?,
        seriesName: String?,
        seriesOrder: Int?,
    ) {
        val existing = requireNotNull(get(bookId)) { "书籍不存在" }
        update(
            existing.copy(
                title = title,
                author = author,
                seriesName = seriesName,
                seriesOrder = seriesOrder,
            ),
        )
    }

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteChapterIndex(bookId: String)

    @Transaction
    suspend fun updateAndInvalidateChangedContent(book: BookEntity) {
        val previous = get(book.id)
        update(book)
        if (
            previous != null &&
            (previous.contentSha256 != book.contentSha256 || previous.normalizedPath != book.normalizedPath)
        ) {
            deleteChapterIndex(book.id)
        }
    }

    @Query("UPDATE books SET lastOpenedAtEpochMillis = :epochMillis WHERE id = :bookId")
    suspend fun markOpened(bookId: String, epochMillis: Long)
}
