package com.xinyue.reader.core.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.XinYueDatabase
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRestoreDatabaseGatewayInstrumentedTest {
    private lateinit var database: XinYueDatabase
    private lateinit var source: RoomBackupCatalogDataSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, XinYueDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()
        source = RoomBackupCatalogDataSource(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun replaceAll_roundTripsEveryLogicalUserRecord() = runBlocking {
        val expected = completeCatalog("new")

        RoomRestoreDatabaseGateway(database, source, RestoreFaultInjector.NONE).replaceAll(expected)

        assertThat(source.snapshot().canonicalForRestore()).isEqualTo(expected.canonicalForRestore())
    }

    @Test
    fun injectedFailure_rollsBackWholeRoomTransaction() = runBlocking {
        val normal = RoomRestoreDatabaseGateway(database, source, RestoreFaultInjector.NONE)
        val old = completeCatalog("old")
        normal.replaceAll(old)
        val failing = RoomRestoreDatabaseGateway(
            database,
            source,
            RestoreFaultInjector { point ->
                if (point == RestoreFaultPoint.DURING_DATABASE_TRANSACTION) throw IOException("injected")
            },
        )

        assertThat(runCatching { failing.replaceAll(completeCatalog("new")) }.exceptionOrNull())
            .isInstanceOf(IOException::class.java)
        assertThat(source.snapshot().canonicalForRestore()).isEqualTo(old.canonicalForRestore())
    }

    private fun completeCatalog(seed: String): BackupCatalogSnapshot {
        val bookId = "$seed-book"
        val groupId = "$seed-group"
        val fontId = "$seed-font"
        return BackupCatalogSnapshot(
            books = listOf(
                BackupBookRecord(
                    id = bookId,
                    title = "公开书名-$seed",
                    author = "公开作者",
                    originalFileName = "public.txt",
                    charsetName = "UTF-8",
                    contentSha256 = if (seed == "old") "a".repeat(64) else "b".repeat(64),
                    contentLength = 100,
                    createdAtEpochMillis = 1,
                    lastOpenedAtEpochMillis = 2,
                    seriesName = "公开系列",
                    seriesOrder = 2,
                    finished = true,
                    originalAssetPath = "assets/books/$bookId/original.txt",
                    normalizedAssetPath = "assets/books/$bookId/content.txt",
                    offsetIndexAssetPath = "assets/books/$bookId/offsets.xidx",
                    customCoverAssetPath = "assets/books/$bookId/cover.webp",
                ),
            ),
            groups = listOf(BackupGroupRecord(groupId, "公开分组-$seed", 0, 1, 2)),
            memberships = listOf(BackupBookMembershipRecord(bookId, groupId)),
            progress = listOf(BackupProgressRecord(bookId, 10, "anchor", "prefix", "suffix", 100, 3)),
            annotations = listOf(
                BackupAnnotationRecord(
                    "$seed-note", bookId, "NOTE", 1, 2, "prefix", "suffix", "c".repeat(64),
                    "yellow", "公开批注", 1, 2,
                ),
            ),
            globalSettings = BackupGlobalSettingsRecord("{}", "{}", 4),
            bookSettings = listOf(BackupBookSettingsRecord(bookId, "{}", 4)),
            themes = listOf(BackupThemeRecord("$seed-theme", "公开主题-$seed", "{}", false, 4)),
            fonts = listOf(
                BackupFontRecord(fontId, "公开字体-$seed", "d".repeat(64), 10, 1, "assets/fonts/$fontId/font.bin"),
            ),
            sessions = listOf(BackupSessionRecord("$seed-session", bookId, 1, 2, 3, 1)),
            dailyStats = listOf(BackupDailyStatRecord(bookId, 20_000, 1, 1)),
            bookSources = listOf(
                BackupBookSource(
                    bookId, "books/$bookId/original.txt", "books/$bookId/content.txt",
                    "books/$bookId/offsets.xidx", "books/$bookId/cover.webp",
                ),
            ),
            fontSources = listOf(BackupFontSource(fontId, "fonts/$fontId/font.bin")),
        )
    }
}
