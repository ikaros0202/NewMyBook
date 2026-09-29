package com.xinyue.reader.core.data.di

import android.content.Context
import com.xinyue.reader.core.data.BookFileStore
import com.xinyue.reader.core.data.BookFileCleanupScheduler
import com.xinyue.reader.core.data.AndroidImportSourceFactory
import com.xinyue.reader.core.data.AndroidImportPreflightService
import com.xinyue.reader.core.data.AnchorRepairCoordinator
import com.xinyue.reader.core.data.BookImporter
import com.xinyue.reader.core.data.BookManager
import com.xinyue.reader.core.data.TextSource
import com.xinyue.reader.core.data.ImportBookUseCase
import com.xinyue.reader.core.data.ImportSourceFactory
import com.xinyue.reader.core.data.ImportPreflightService
import com.xinyue.reader.core.data.ImportTaskScheduler
import com.xinyue.reader.core.data.WorkManagerImportTaskScheduler
import com.xinyue.reader.core.data.WorkManagerBookFileCleanupScheduler
import com.xinyue.reader.core.data.LocalBookFileStore
import com.xinyue.reader.core.data.LocalBookManager
import com.xinyue.reader.core.data.PendingBookFileCleanup
import com.xinyue.reader.core.data.DurablePendingBookFileCleanup
import com.xinyue.reader.core.data.ChapterIndexStore
import com.xinyue.reader.core.data.RoomChapterIndexStore
import com.xinyue.reader.core.data.RoomBookSearchRepository
import com.xinyue.reader.core.data.SearchIndexScheduler
import com.xinyue.reader.core.data.WorkManagerSearchIndexScheduler
import com.xinyue.reader.core.data.LocalTextSource
import com.xinyue.reader.core.data.RoomBookRepository
import com.xinyue.reader.core.data.RoomBookGroupRepository
import com.xinyue.reader.core.data.RoomReadingSessionRepository
import com.xinyue.reader.core.data.LocalBookCoverRepository
import com.xinyue.reader.core.data.RoomBookmarkRepository
import com.xinyue.reader.core.data.RoomAnnotationRepository
import com.xinyue.reader.core.data.SystemEpochClock
import com.xinyue.reader.core.data.LocalBackupService
import com.xinyue.reader.core.data.LocalReadingHandoffService
import com.xinyue.reader.core.data.LocalAnnotationExportService
import com.xinyue.reader.core.data.DefaultRestorePlanResolver
import com.xinyue.reader.core.data.RestoreDatabaseGateway
import com.xinyue.reader.core.data.RestoreJournalStore
import com.xinyue.reader.core.data.RestorePlanResolver
import com.xinyue.reader.core.data.RestorePublisher
import com.xinyue.reader.core.data.RestoreRecovery
import com.xinyue.reader.core.data.RestoreSnapshotStore
import com.xinyue.reader.core.data.RoomRestoreDatabaseGateway
import com.xinyue.reader.core.data.StagedRestorePlanRegistry
import com.xinyue.reader.core.data.AndroidBackupUriPermissionManager
import com.xinyue.reader.core.data.BackupTaskScheduler
import com.xinyue.reader.core.data.BackupUriPermissionManager
import com.xinyue.reader.core.data.WorkManagerBackupTaskScheduler
import com.xinyue.reader.core.data.HandoffTaskScheduler
import com.xinyue.reader.core.data.WorkManagerHandoffTaskScheduler
import com.xinyue.reader.core.data.RoomReaderSettingsRepository
import com.xinyue.reader.core.data.RoomReaderThemeRepository
import com.xinyue.reader.core.data.RoomReaderThemeScheduleRepository
import com.xinyue.reader.core.data.SharedPreferencesLibraryLayoutPreferences
import com.xinyue.reader.core.data.readerDataJson
import com.xinyue.reader.core.data.AndroidFontValidator
import com.xinyue.reader.core.data.FontValidator
import com.xinyue.reader.core.data.FontReferenceCounter
import com.xinyue.reader.core.data.ImportedFontReferenceCounter
import com.xinyue.reader.core.data.LocalImportedFontRepository
import com.xinyue.reader.core.data.AndroidFontFileWarningReporter
import com.xinyue.reader.core.data.FontFileWarningReporter
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.domain.repository.BookGroupRepository
import com.xinyue.reader.core.domain.repository.BookCoverRepository
import com.xinyue.reader.core.domain.repository.ReadingSessionRepository
import com.xinyue.reader.core.domain.repository.BookmarkRepository
import com.xinyue.reader.core.domain.repository.AnnotationRepository
import com.xinyue.reader.core.domain.repository.AnnotationExportService
import com.xinyue.reader.core.domain.repository.ReaderSettingsRepository
import com.xinyue.reader.core.domain.repository.ReaderThemeRepository
import com.xinyue.reader.core.domain.repository.ReaderThemeScheduleRepository
import com.xinyue.reader.core.domain.repository.LibraryLayoutPreferences
import com.xinyue.reader.core.domain.repository.ImportedFontRepository
import com.xinyue.reader.core.domain.repository.BookSearchRepository
import com.xinyue.reader.core.domain.repository.BackupService
import com.xinyue.reader.core.domain.repository.ReadingHandoffService
import com.xinyue.reader.core.domain.time.EpochClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import androidx.work.WorkManager
import java.util.UUID
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindBackupTaskScheduler(implementation: WorkManagerBackupTaskScheduler): BackupTaskScheduler

    @Binds
    @Singleton
    abstract fun bindHandoffTaskScheduler(
        implementation: WorkManagerHandoffTaskScheduler,
    ): HandoffTaskScheduler

    @Binds
    @Singleton
    abstract fun bindBackupUriPermissionManager(
        implementation: AndroidBackupUriPermissionManager,
    ): BackupUriPermissionManager

    @Binds
    @Singleton
    abstract fun bindRestoreDatabaseGateway(implementation: RoomRestoreDatabaseGateway): RestoreDatabaseGateway

    @Binds
    @Singleton
    abstract fun bindRestorePlanResolver(implementation: DefaultRestorePlanResolver): RestorePlanResolver

    @Binds
    @Singleton
    abstract fun bindBackupService(implementation: LocalBackupService): BackupService

    @Binds
    @Singleton
    abstract fun bindReadingHandoffService(
        implementation: LocalReadingHandoffService,
    ): ReadingHandoffService

    @Binds
    @Singleton
    abstract fun bindBookRepository(implementation: RoomBookRepository): BookRepository

    @Binds
    @Singleton
    abstract fun bindBookGroupRepository(implementation: RoomBookGroupRepository): BookGroupRepository

    @Binds
    @Singleton
    abstract fun bindBookCoverRepository(implementation: LocalBookCoverRepository): BookCoverRepository

    @Binds
    @Singleton
    abstract fun bindReadingSessionRepository(implementation: RoomReadingSessionRepository): ReadingSessionRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(implementation: RoomBookmarkRepository): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindAnnotationRepository(implementation: RoomAnnotationRepository): AnnotationRepository

    @Binds
    @Singleton
    abstract fun bindAnnotationExportService(
        implementation: LocalAnnotationExportService,
    ): AnnotationExportService

    @Binds
    abstract fun bindBookImporter(implementation: ImportBookUseCase): BookImporter

    @Binds
    @Singleton
    abstract fun bindImportSourceFactory(implementation: AndroidImportSourceFactory): ImportSourceFactory

    @Binds
    @Singleton
    abstract fun bindImportPreflightService(implementation: AndroidImportPreflightService): ImportPreflightService

    @Binds
    @Singleton
    abstract fun bindImportTaskScheduler(implementation: WorkManagerImportTaskScheduler): ImportTaskScheduler

    @Binds
    @Singleton
    abstract fun bindEpochClock(implementation: SystemEpochClock): EpochClock

    @Binds
    @Singleton
    abstract fun bindLibraryLayoutPreferences(
        implementation: SharedPreferencesLibraryLayoutPreferences,
    ): LibraryLayoutPreferences

    @Binds
    @Singleton
    abstract fun bindReaderSettingsRepository(
        implementation: RoomReaderSettingsRepository,
    ): ReaderSettingsRepository

    @Binds
    @Singleton
    abstract fun bindReaderThemeRepository(implementation: RoomReaderThemeRepository): ReaderThemeRepository

    @Binds
    @Singleton
    abstract fun bindReaderThemeScheduleRepository(
        implementation: RoomReaderThemeScheduleRepository,
    ): ReaderThemeScheduleRepository

    @Binds
    @Singleton
    abstract fun bindImportedFontRepository(implementation: LocalImportedFontRepository): ImportedFontRepository

    @Binds
    abstract fun bindFontValidator(implementation: AndroidFontValidator): FontValidator

    @Binds
    abstract fun bindFontReferenceCounter(implementation: ImportedFontReferenceCounter): FontReferenceCounter

    @Binds
    abstract fun bindFontFileWarningReporter(
        implementation: AndroidFontFileWarningReporter,
    ): FontFileWarningReporter

    @Binds
    abstract fun bindBookManager(implementation: LocalBookManager): BookManager

    @Binds
    @Singleton
    abstract fun bindPendingBookFileCleanup(
        implementation: DurablePendingBookFileCleanup,
    ): PendingBookFileCleanup

    @Binds
    @Singleton
    abstract fun bindBookFileCleanupScheduler(
        implementation: WorkManagerBookFileCleanupScheduler,
    ): BookFileCleanupScheduler

    @Binds
    @Singleton
    abstract fun bindChapterIndexStore(implementation: RoomChapterIndexStore): ChapterIndexStore

    @Binds
    @Singleton
    abstract fun bindBookSearchRepository(implementation: RoomBookSearchRepository): BookSearchRepository

    @Binds
    @Singleton
    abstract fun bindSearchIndexScheduler(
        implementation: WorkManagerSearchIndexScheduler,
    ): SearchIndexScheduler
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideRestoreJournalStore(@ApplicationContext context: Context): RestoreJournalStore =
        RestoreJournalStore(context.filesDir)

    @Provides
    @Singleton
    fun provideRestoreSnapshotStore(@ApplicationContext context: Context): RestoreSnapshotStore =
        RestoreSnapshotStore(context.filesDir)

    @Provides
    @Singleton
    fun provideRestorePublisher(
        @ApplicationContext context: Context,
        database: RestoreDatabaseGateway,
        resolver: RestorePlanResolver,
        journals: RestoreJournalStore,
        snapshots: RestoreSnapshotStore,
    ): RestorePublisher = RestorePublisher(context.filesDir, database, resolver, journals, snapshots)

    @Provides
    @Singleton
    fun provideRestoreRecovery(
        @ApplicationContext context: Context,
        database: RestoreDatabaseGateway,
        resolver: RestorePlanResolver,
        journals: RestoreJournalStore,
        snapshots: RestoreSnapshotStore,
        stagedPlans: StagedRestorePlanRegistry,
    ): RestoreRecovery = RestoreRecovery(context.filesDir, database, resolver, journals, snapshots, stagedPlans)

    @Provides
    @Singleton
    fun provideReaderDataJson(): Json = readerDataJson

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideBookFileStore(@ApplicationContext context: Context): BookFileStore =
        LocalBookFileStore(context.filesDir)

    @Provides
    @Singleton
    fun provideTextSource(@ApplicationContext context: Context): TextSource =
        LocalTextSource(context.filesDir)

    @Provides
    fun provideImportBookUseCase(
        repository: BookRepository,
        fileStore: BookFileStore,
        anchorRepairCoordinator: AnchorRepairCoordinator,
    ): ImportBookUseCase = ImportBookUseCase(
        repository = repository,
        fileStore = fileStore,
        idFactory = { UUID.randomUUID().toString() },
        nowEpochMillis = System::currentTimeMillis,
        anchorRepairCoordinator = anchorRepairCoordinator,
    )
}
