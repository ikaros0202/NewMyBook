package com.xinyue.reader.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.xinyue.reader.core.database.entity.ReaderThemeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderThemeDao {
    @Query("SELECT * FROM reader_themes ORDER BY builtIn DESC, updatedAtEpochMillis DESC, name, id")
    fun observeAll(): Flow<List<ReaderThemeEntity>>

    @Query("SELECT * FROM reader_themes ORDER BY id")
    suspend fun getAll(): List<ReaderThemeEntity>

    @Query("SELECT * FROM reader_themes WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ReaderThemeEntity?

    @Query("SELECT * FROM reader_themes WHERE name = :name COLLATE NOCASE AND id != :excludingId LIMIT 1")
    suspend fun findDuplicateName(name: String, excludingId: String): ReaderThemeEntity?

    @Upsert
    suspend fun upsert(entity: ReaderThemeEntity)

    @Query("DELETE FROM reader_themes WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustom(id: String)

    @Query("SELECT scheduleJson FROM reader_global_settings WHERE id = 0 LIMIT 1")
    suspend fun getScheduleJson(): String?

    @Query(
        "UPDATE reader_global_settings SET scheduleJson = :scheduleJson, " +
            "updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = 0",
    )
    suspend fun updateScheduleJson(scheduleJson: String, updatedAtEpochMillis: Long)

    @Transaction
    suspend fun deleteCustomWithScheduleFallback(
        id: String,
        updatedAtEpochMillis: Long,
        transformSchedule: (String?) -> String?,
    ) {
        val scheduleJson = transformSchedule(getScheduleJson())
        if (scheduleJson != null) updateScheduleJson(scheduleJson, updatedAtEpochMillis)
        deleteCustom(id)
    }
}
