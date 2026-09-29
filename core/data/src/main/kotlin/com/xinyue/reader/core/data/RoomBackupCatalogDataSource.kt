package com.xinyue.reader.core.data

import androidx.room3.withReadTransaction
import com.xinyue.reader.core.database.XinYueDatabase
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
import javax.inject.Inject
import javax.inject.Singleton

internal fun interface BackupReadTransaction {
    suspend fun run(block: suspend () -> BackupCatalogSnapshot): BackupCatalogSnapshot
}

internal interface BackupCatalogQueries {
    suspend fun books(): List<BookEntity>
    suspend fun groups(): List<BookGroupEntity>
    suspend fun memberships(): List<BookGroupMembershipEntity>
    suspend fun progress(): List<ReadingProgressEntity>
    suspend fun annotations(): List<AnnotationEntity>
    suspend fun globalSettings(): ReaderGlobalSettingsEntity?
    suspend fun bookSettings(): List<BookReaderOverridesEntity>
    suspend fun themes(): List<ReaderThemeEntity>
    suspend fun fonts(): List<ImportedFontEntity>
    suspend fun sessions(): List<ReadingSessionEntity>
    suspend fun dailyStats(): List<ReadingDailyStatEntity>
}

@Singleton
class RoomBackupCatalogDataSource internal constructor(
    private val readTransaction: BackupReadTransaction,
    private val queries: BackupCatalogQueries,
    private val afterBooksRead: suspend () -> Unit,
) : BackupCatalogDataSource {
    @Inject
    constructor(database: XinYueDatabase) : this(
        readTransaction = BackupReadTransaction { block -> database.withReadTransaction { block() } },
        queries = RoomBackupCatalogQueries(database),
        afterBooksRead = {},
    )

    override suspend fun snapshot(): BackupCatalogSnapshot = readTransaction.run { readSnapshot() }

    /** Used only from a surrounding Room write transaction during restore verification. */
    internal suspend fun snapshotInCurrentTransaction(): BackupCatalogSnapshot = readSnapshot()

    private suspend fun readSnapshot(): BackupCatalogSnapshot {
        val books = queries.books().sortedBy { it.id }
        afterBooksRead()
        val fonts = queries.fonts().sortedBy { it.id }
        return BackupCatalogSnapshot(
            books = books.map { book ->
                BackupBookRecord(
                    id = book.id,
                    title = book.title,
                    author = book.author,
                    originalFileName = book.originalFileName,
                    charsetName = book.charsetName,
                    contentSha256 = book.contentSha256,
                    contentLength = book.contentLength,
                    createdAtEpochMillis = book.createdAtEpochMillis,
                    lastOpenedAtEpochMillis = book.lastOpenedAtEpochMillis,
                    seriesName = book.seriesName,
                    seriesOrder = book.seriesOrder,
                    finished = book.finished,
                    originalAssetPath = "assets/books/${book.id}/original.txt",
                    normalizedAssetPath = "assets/books/${book.id}/content.txt",
                    offsetIndexAssetPath = "assets/books/${book.id}/offsets.xidx",
                    customCoverAssetPath = book.customCoverPath?.let { "assets/books/${book.id}/cover.webp" },
                )
            },
            groups = queries.groups().sortedBy { it.id }.map {
                BackupGroupRecord(it.id, it.name, it.sortOrder, it.createdAtEpochMillis, it.updatedAtEpochMillis)
            },
            memberships = queries.memberships()
                .sortedWith(compareBy({ it.bookId }, { it.groupId }))
                .map { BackupBookMembershipRecord(it.bookId, it.groupId) },
            progress = queries.progress().sortedBy { it.bookId }.map {
                BackupProgressRecord(it.bookId, it.offset, it.contextHash, it.prefix, it.suffix, it.contentLength, it.updatedAtEpochMillis)
            },
            annotations = queries.annotations().sortedBy { it.id }.map {
                BackupAnnotationRecord(
                    it.id, it.bookId, it.kind, it.startOffset, it.endOffset, it.prefix, it.suffix,
                    it.selectedSha256, it.color, it.note, it.createdAtEpochMillis, it.updatedAtEpochMillis,
                )
            },
            globalSettings = queries.globalSettings()?.let {
                BackupGlobalSettingsRecord(it.settingsJson, it.scheduleJson, it.updatedAtEpochMillis)
            },
            bookSettings = queries.bookSettings().sortedBy { it.bookId }.map {
                BackupBookSettingsRecord(it.bookId, it.overridesJson, it.updatedAtEpochMillis)
            },
            themes = queries.themes().sortedBy { it.id }.map {
                BackupThemeRecord(it.id, it.name, it.settingsJson, it.builtIn, it.updatedAtEpochMillis)
            },
            fonts = fonts.map {
                BackupFontRecord(
                    it.id, it.displayName, it.contentSha256, it.sizeBytes, it.createdAtEpochMillis,
                    "assets/fonts/${it.id}/font.bin",
                )
            },
            sessions = queries.sessions().sortedBy { it.id }.map {
                BackupSessionRecord(
                    it.id, it.bookId, it.startedAtEpochMillis, it.lastInteractionAtEpochMillis,
                    it.endedAtEpochMillis, it.activeMillis,
                )
            },
            dailyStats = queries.dailyStats().sortedWith(compareBy({ it.bookId }, { it.localEpochDay })).map {
                BackupDailyStatRecord(it.bookId, it.localEpochDay, it.activeMillis, it.sessionCount)
            },
            bookSources = books.map { book ->
                BackupBookSource(
                    bookId = book.id,
                    originalRelativePath = book.originalPath,
                    normalizedRelativePath = book.normalizedPath,
                    offsetIndexRelativePath = book.normalizedPath.substringBeforeLast('/', "") + "/offsets.xidx",
                    customCoverRelativePath = book.customCoverPath,
                )
            },
            fontSources = fonts.map { BackupFontSource(it.id, it.privateRelativePath) },
        )
    }

    internal companion object {
        fun forTest(
            queries: BackupCatalogQueries,
            readTransaction: BackupReadTransaction = BackupReadTransaction { block -> block() },
            afterBooksRead: suspend () -> Unit = {},
        ) = RoomBackupCatalogDataSource(readTransaction, queries, afterBooksRead)
    }
}

private class RoomBackupCatalogQueries(private val database: XinYueDatabase) : BackupCatalogQueries {
    override suspend fun books() = database.bookDao().getAllForBackup()
    override suspend fun groups() = database.bookGroupDao().getAllForBackup()
    override suspend fun memberships() = database.bookGroupDao().getAllMembershipsForBackup()
    override suspend fun progress() = database.readingProgressDao().getAllForBackup()
    override suspend fun annotations() = database.annotationDao().getAllForBackup()
    override suspend fun globalSettings() = database.readerSettingsDao().getGlobal()
    override suspend fun bookSettings() = database.readerSettingsDao().getAllBookOverrides()
    override suspend fun themes() = database.readerThemeDao().getAll()
    override suspend fun fonts() = database.importedFontDao().getAllForBackup()
    override suspend fun sessions() = database.readingSessionDao().getAllForBackup()
    override suspend fun dailyStats() = database.readingSessionDao().getAllDailyStatsForBackup()
}
