package com.xinyue.reader.core.data

import androidx.room3.withWriteTransaction
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

/** Replaces the complete logical backup surface in one Room transaction. */
@Singleton
class RoomRestoreDatabaseGateway internal constructor(
    private val database: XinYueDatabase,
    private val catalogDataSource: RoomBackupCatalogDataSource,
    private val faultInjector: RestoreFaultInjector,
) : RestoreDatabaseGateway {
    @Inject
    constructor(
        database: XinYueDatabase,
        catalogDataSource: RoomBackupCatalogDataSource,
    ) : this(database, catalogDataSource, RestoreFaultInjector.NONE)

    override suspend fun snapshot(): BackupCatalogSnapshot = catalogDataSource.snapshot()

    override suspend fun replaceAll(snapshot: BackupCatalogSnapshot) {
        val canonicalExpected = snapshot.canonicalForRestore()
        database.withWriteTransaction {
            clearLogicalBackupSurface()
            insertLogicalBackupSurface(canonicalExpected)
            faultInjector.hit(RestoreFaultPoint.DURING_DATABASE_TRANSACTION)
            val actual = catalogDataSource.snapshotInCurrentTransaction().canonicalForRestore()
            require(BackupCatalogDigest.sha256(actual) == BackupCatalogDigest.sha256(canonicalExpected)) {
                "Room 恢复事务内等价性校验失败"
            }
        }
    }

    private suspend fun clearLogicalBackupSurface() {
        val dao = database.backupRestoreDao()
        dao.deleteDailyStats()
        dao.deleteSessions()
        dao.deleteAnnotations()
        dao.deleteProgress()
        dao.deleteBookSettings()
        dao.deleteMemberships()
        dao.deleteBooks()
        dao.deleteGroups()
        dao.deleteFonts()
        dao.deleteThemes()
        dao.deleteGlobalSettings()
    }

    private suspend fun insertLogicalBackupSurface(snapshot: BackupCatalogSnapshot) {
        val dao = database.backupRestoreDao()
        snapshot.groups.forEach { group ->
            dao.upsertGroup(
                BookGroupEntity(group.id, group.name, group.sortOrder, group.createdAtEpochMillis, group.updatedAtEpochMillis),
            )
        }
        snapshot.fonts.forEach { font ->
            val source = requireNotNull(snapshot.fontSources.singleOrNull { it.fontId == font.id }) { "字体私有路径缺失" }
            dao.upsertFont(
                ImportedFontEntity(
                    font.id, font.displayName, source.relativePath, font.contentSha256,
                    font.sizeBytes, font.createdAtEpochMillis,
                ),
            )
        }
        snapshot.themes.forEach { theme ->
            dao.upsertTheme(
                ReaderThemeEntity(theme.id, theme.name, theme.settingsJson, theme.builtIn, theme.updatedAtEpochMillis),
            )
        }
        snapshot.globalSettings?.let { global ->
            dao.upsertGlobalSettings(
                ReaderGlobalSettingsEntity(
                    settingsJson = global.settingsJson,
                    scheduleJson = global.scheduleJson,
                    updatedAtEpochMillis = global.updatedAtEpochMillis,
                ),
            )
        }
        snapshot.books.forEach { book ->
            val source = requireNotNull(snapshot.bookSources.singleOrNull { it.bookId == book.id }) { "书籍私有路径缺失" }
            dao.upsertBook(
                BookEntity(
                    id = book.id,
                    title = book.title,
                    author = book.author,
                    originalFileName = book.originalFileName,
                    originalPath = source.originalRelativePath,
                    normalizedPath = source.normalizedRelativePath,
                    charsetName = book.charsetName,
                    contentSha256 = book.contentSha256,
                    contentLength = book.contentLength,
                    createdAtEpochMillis = book.createdAtEpochMillis,
                    lastOpenedAtEpochMillis = book.lastOpenedAtEpochMillis,
                    seriesName = book.seriesName,
                    seriesOrder = book.seriesOrder,
                    customCoverPath = source.customCoverRelativePath,
                    finished = book.finished,
                ),
            )
        }
        snapshot.memberships.forEach { membership ->
            dao.upsertMembership(BookGroupMembershipEntity(membership.bookId, membership.groupId))
        }
        snapshot.progress.forEach { value ->
            dao.upsertProgress(
                ReadingProgressEntity(
                    value.bookId, value.offset, value.contextHash, value.prefix, value.suffix,
                    value.contentLength, value.updatedAtEpochMillis,
                ),
            )
        }
        snapshot.annotations.forEach { value ->
            dao.upsertAnnotation(
                AnnotationEntity(
                    value.id, value.bookId, value.kind, value.startOffset, value.endOffset,
                    value.prefix, value.suffix, value.selectedSha256, value.color, value.note,
                    value.createdAtEpochMillis, value.updatedAtEpochMillis,
                ),
            )
        }
        snapshot.bookSettings.forEach { value ->
            dao.upsertBookSettings(
                BookReaderOverridesEntity(value.bookId, value.overridesJson, value.updatedAtEpochMillis),
            )
        }
        snapshot.sessions.forEach { value ->
            dao.upsertSession(
                ReadingSessionEntity(
                    value.id, value.bookId, value.startedAtEpochMillis, value.lastInteractionAtEpochMillis,
                    value.endedAtEpochMillis, value.activeMillis,
                ),
            )
        }
        snapshot.dailyStats.forEach { value ->
            dao.upsertDailyStat(
                ReadingDailyStatEntity(value.bookId, value.localEpochDay, value.activeMillis, value.sessionCount),
            )
        }
    }
}

internal fun BackupCatalogSnapshot.canonicalForRestore(): BackupCatalogSnapshot = copy(
    books = books.sortedBy { it.id },
    groups = groups.sortedBy { it.id },
    memberships = memberships.sortedWith(compareBy({ it.bookId }, { it.groupId })),
    progress = progress.sortedBy { it.bookId },
    annotations = annotations.sortedBy { it.id },
    bookSettings = bookSettings.sortedBy { it.bookId },
    themes = themes.sortedBy { it.id },
    fonts = fonts.sortedBy { it.id },
    sessions = sessions.sortedBy { it.id },
    dailyStats = dailyStats.sortedWith(compareBy({ it.bookId }, { it.localEpochDay })),
    bookSources = bookSources.sortedBy { it.bookId },
    fontSources = fontSources.sortedBy { it.fontId },
).withV2Collections()
