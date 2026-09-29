package com.xinyue.reader.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.xinyue.reader.core.database.entity.ImportedFontEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportedFontDao {
    @Query("SELECT * FROM imported_fonts ORDER BY createdAtEpochMillis DESC, displayName, id")
    fun observeAll(): Flow<List<ImportedFontEntity>>

    @Query("SELECT * FROM imported_fonts ORDER BY id")
    suspend fun getAllForBackup(): List<ImportedFontEntity>

    @Query("SELECT * FROM imported_fonts WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ImportedFontEntity?

    @Query("SELECT * FROM imported_fonts WHERE contentSha256 = :sha256 LIMIT 1")
    suspend fun findBySha256(sha256: String): ImportedFontEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ImportedFontEntity)

    @Query("DELETE FROM imported_fonts WHERE id = :id")
    suspend fun delete(id: String)
}
