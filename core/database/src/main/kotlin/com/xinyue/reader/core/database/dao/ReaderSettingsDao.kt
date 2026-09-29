package com.xinyue.reader.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import com.xinyue.reader.core.database.entity.BookReaderOverridesEntity
import com.xinyue.reader.core.database.entity.ReaderGlobalSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderSettingsDao {
    @Query("SELECT * FROM reader_global_settings WHERE id = 0 LIMIT 1")
    fun observeGlobal(): Flow<ReaderGlobalSettingsEntity?>

    @Query("SELECT * FROM reader_global_settings WHERE id = 0 LIMIT 1")
    suspend fun getGlobal(): ReaderGlobalSettingsEntity?

    @Upsert
    suspend fun upsertGlobal(entity: ReaderGlobalSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGlobalIfAbsent(entity: ReaderGlobalSettingsEntity): Long

    @Query("SELECT * FROM book_reader_overrides WHERE bookId = :bookId LIMIT 1")
    fun observeBookOverrides(bookId: String): Flow<BookReaderOverridesEntity?>

    @Query("SELECT * FROM book_reader_overrides WHERE bookId = :bookId LIMIT 1")
    suspend fun getBookOverrides(bookId: String): BookReaderOverridesEntity?

    @Query("SELECT * FROM book_reader_overrides ORDER BY bookId")
    suspend fun getAllBookOverrides(): List<BookReaderOverridesEntity>

    @Upsert
    suspend fun upsertBookOverrides(entity: BookReaderOverridesEntity)

    @Query("DELETE FROM book_reader_overrides WHERE bookId = :bookId")
    suspend fun deleteBookOverrides(bookId: String)

}
