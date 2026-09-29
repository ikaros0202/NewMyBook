package com.xinyue.reader.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.xinyue.reader.core.database.entity.ImportTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportTaskDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(tasks: List<ImportTaskEntity>)

    @Update
    suspend fun update(task: ImportTaskEntity)

    @Query("SELECT * FROM import_tasks WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ImportTaskEntity?

    @Query(
        """
        SELECT * FROM import_tasks
        WHERE batchId = (
            SELECT batchId FROM import_tasks
            ORDER BY createdAtEpochMillis DESC, batchId DESC
            LIMIT 1
        )
        ORDER BY itemIndex ASC, attempt ASC
        """,
    )
    fun observeLatestBatch(): Flow<List<ImportTaskEntity>>
}
