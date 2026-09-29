package com.xinyue.reader.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.xinyue.reader.core.database.entity.AnnotationEntity
import com.xinyue.reader.core.database.entity.BookEntity
import com.xinyue.reader.core.database.entity.BookGroupEntity
import com.xinyue.reader.core.database.entity.BookGroupMembershipEntity
import com.xinyue.reader.core.database.entity.BookReaderOverridesEntity
import com.xinyue.reader.core.database.entity.ImportedFontEntity
import com.xinyue.reader.core.database.entity.ReaderGlobalSettingsEntity
import com.xinyue.reader.core.database.entity.ReaderThemeEntity
import com.xinyue.reader.core.database.entity.ReadingDailyStatEntity
import com.xinyue.reader.core.database.entity.ReadingProgressEntity
import com.xinyue.reader.core.database.entity.ReadingSessionEntity

/** Internal DAO for the schema-12 backup surface; callers must wrap operations in one transaction. */
@Dao
interface BackupRestoreDao {
    @Query("DELETE FROM reading_daily_stats") suspend fun deleteDailyStats()
    @Query("DELETE FROM reading_sessions") suspend fun deleteSessions()
    @Query("DELETE FROM annotations") suspend fun deleteAnnotations()
    @Query("DELETE FROM reading_progress") suspend fun deleteProgress()
    @Query("DELETE FROM book_reader_overrides") suspend fun deleteBookSettings()
    @Query("DELETE FROM book_group_memberships") suspend fun deleteMemberships()
    @Query("DELETE FROM books") suspend fun deleteBooks()
    @Query("DELETE FROM book_groups") suspend fun deleteGroups()
    @Query("DELETE FROM imported_fonts") suspend fun deleteFonts()
    @Query("DELETE FROM reader_themes") suspend fun deleteThemes()
    @Query("DELETE FROM reader_global_settings") suspend fun deleteGlobalSettings()

    @Upsert suspend fun upsertGroup(value: BookGroupEntity)
    @Upsert suspend fun upsertFont(value: ImportedFontEntity)
    @Upsert suspend fun upsertTheme(value: ReaderThemeEntity)
    @Upsert suspend fun upsertGlobalSettings(value: ReaderGlobalSettingsEntity)
    @Upsert suspend fun upsertBook(value: BookEntity)
    @Upsert suspend fun upsertMembership(value: BookGroupMembershipEntity)
    @Upsert suspend fun upsertProgress(value: ReadingProgressEntity)
    @Upsert suspend fun upsertAnnotation(value: AnnotationEntity)
    @Upsert suspend fun upsertBookSettings(value: BookReaderOverridesEntity)
    @Upsert suspend fun upsertSession(value: ReadingSessionEntity)
    @Upsert suspend fun upsertDailyStat(value: ReadingDailyStatEntity)
}
