package com.xinyue.reader.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import com.xinyue.reader.core.database.entity.PendingFileCleanupEntity

@Dao
interface PendingFileCleanupDao {
    @Query("SELECT * FROM pending_file_cleanup WHERE bookId = :bookId LIMIT 1")
    suspend fun get(bookId: String): PendingFileCleanupEntity?

    @Query("SELECT * FROM pending_file_cleanup ORDER BY queuedAtEpochMillis ASC")
    suspend fun getAll(): List<PendingFileCleanupEntity>

    @Query("DELETE FROM pending_file_cleanup WHERE bookId = :bookId")
    suspend fun delete(bookId: String)
}
