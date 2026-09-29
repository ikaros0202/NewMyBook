package com.xinyue.reader.core.database

import androidx.room3.Database
import androidx.room3.AutoMigration
import androidx.room3.RoomDatabase
import com.xinyue.reader.core.database.dao.BookDao
import com.xinyue.reader.core.database.dao.BookGroupDao
import com.xinyue.reader.core.database.dao.ReadingSessionDao
import com.xinyue.reader.core.database.dao.ReadingProgressDao
import com.xinyue.reader.core.database.dao.AnnotationDao
import com.xinyue.reader.core.database.dao.ImportTaskDao
import com.xinyue.reader.core.database.dao.PendingFileCleanupDao
import com.xinyue.reader.core.database.dao.ChapterDao
import com.xinyue.reader.core.database.dao.ChapterIndexDao
import com.xinyue.reader.core.database.dao.SearchIndexDao
import com.xinyue.reader.core.database.dao.ReaderSettingsDao
import com.xinyue.reader.core.database.dao.ReaderThemeDao
import com.xinyue.reader.core.database.dao.ImportedFontDao
import com.xinyue.reader.core.database.dao.BackupRestoreDao
import com.xinyue.reader.core.database.entity.BookEntity
import com.xinyue.reader.core.database.entity.BookGroupEntity
import com.xinyue.reader.core.database.entity.BookGroupMembershipEntity
import com.xinyue.reader.core.database.entity.ReadingSessionEntity
import com.xinyue.reader.core.database.entity.ReadingDailyStatEntity
import com.xinyue.reader.core.database.entity.ReadingProgressEntity
import com.xinyue.reader.core.database.entity.AnnotationEntity
import com.xinyue.reader.core.database.entity.ImportTaskEntity
import com.xinyue.reader.core.database.entity.PendingFileCleanupEntity
import com.xinyue.reader.core.database.entity.ChapterEntity
import com.xinyue.reader.core.database.entity.ChapterConfigEntity
import com.xinyue.reader.core.database.entity.SearchChunkEntity
import com.xinyue.reader.core.database.entity.SearchChunkFtsEntity
import com.xinyue.reader.core.database.entity.SearchIndexStateEntity
import com.xinyue.reader.core.database.entity.ReaderGlobalSettingsEntity
import com.xinyue.reader.core.database.entity.BookReaderOverridesEntity
import com.xinyue.reader.core.database.entity.ReaderThemeEntity
import com.xinyue.reader.core.database.entity.ImportedFontEntity

@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        AnnotationEntity::class,
        ImportTaskEntity::class,
        PendingFileCleanupEntity::class,
        ChapterEntity::class,
        ChapterConfigEntity::class,
        SearchChunkEntity::class,
        SearchChunkFtsEntity::class,
        SearchIndexStateEntity::class,
        ReaderGlobalSettingsEntity::class,
        BookReaderOverridesEntity::class,
        ReaderThemeEntity::class,
        ImportedFontEntity::class,
        BookGroupEntity::class,
        BookGroupMembershipEntity::class,
        ReadingSessionEntity::class,
        ReadingDailyStatEntity::class,
    ],
    version = 12,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
    exportSchema = true,
)
abstract class XinYueDatabase : RoomDatabase() {
    abstract fun backupRestoreDao(): BackupRestoreDao

    abstract fun bookDao(): BookDao

    abstract fun bookGroupDao(): BookGroupDao

    abstract fun readingSessionDao(): ReadingSessionDao

    abstract fun readingProgressDao(): ReadingProgressDao

    abstract fun annotationDao(): AnnotationDao

    abstract fun importTaskDao(): ImportTaskDao

    abstract fun pendingFileCleanupDao(): PendingFileCleanupDao

    abstract fun chapterDao(): ChapterDao

    abstract fun chapterIndexDao(): ChapterIndexDao

    abstract fun searchIndexDao(): SearchIndexDao

    abstract fun readerSettingsDao(): ReaderSettingsDao

    abstract fun readerThemeDao(): ReaderThemeDao

    abstract fun importedFontDao(): ImportedFontDao
}
