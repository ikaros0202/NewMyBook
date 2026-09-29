package com.xinyue.reader.core.database.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.xinyue.reader.core.database.XinYueDatabase
import com.xinyue.reader.core.database.MIGRATION_7_8
import com.xinyue.reader.core.database.MIGRATION_8_9
import com.xinyue.reader.core.database.MIGRATION_9_10
import com.xinyue.reader.core.database.MIGRATION_10_11
import com.xinyue.reader.core.database.MIGRATION_11_12
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): XinYueDatabase =
        Room.databaseBuilder(context, XinYueDatabase::class.java, "xinyue.db")
            .setDriver(BundledSQLiteDriver())
            .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
            .build()

    @Provides
    fun provideBookDao(database: XinYueDatabase): BookDao = database.bookDao()

    @Provides
    fun provideBookGroupDao(database: XinYueDatabase): BookGroupDao = database.bookGroupDao()

    @Provides
    fun provideReadingSessionDao(database: XinYueDatabase): ReadingSessionDao = database.readingSessionDao()

    @Provides
    fun provideReadingProgressDao(database: XinYueDatabase): ReadingProgressDao = database.readingProgressDao()

    @Provides
    fun provideAnnotationDao(database: XinYueDatabase): AnnotationDao = database.annotationDao()

    @Provides
    fun provideImportTaskDao(database: XinYueDatabase): ImportTaskDao = database.importTaskDao()

    @Provides
    fun providePendingFileCleanupDao(database: XinYueDatabase): PendingFileCleanupDao =
        database.pendingFileCleanupDao()

    @Provides
    fun provideChapterDao(database: XinYueDatabase): ChapterDao = database.chapterDao()

    @Provides
    fun provideChapterIndexDao(database: XinYueDatabase): ChapterIndexDao = database.chapterIndexDao()

    @Provides
    fun provideSearchIndexDao(database: XinYueDatabase): SearchIndexDao = database.searchIndexDao()

    @Provides
    fun provideReaderSettingsDao(database: XinYueDatabase): ReaderSettingsDao = database.readerSettingsDao()

    @Provides
    fun provideReaderThemeDao(database: XinYueDatabase): ReaderThemeDao = database.readerThemeDao()

    @Provides
    fun provideImportedFontDao(database: XinYueDatabase): ImportedFontDao = database.importedFontDao()
}
