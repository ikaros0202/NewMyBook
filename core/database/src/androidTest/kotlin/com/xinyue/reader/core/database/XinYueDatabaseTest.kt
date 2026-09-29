package com.xinyue.reader.core.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.entity.BookEntity
import com.xinyue.reader.core.database.entity.AnnotationEntity
import com.xinyue.reader.core.database.entity.ReadingProgressEntity
import com.xinyue.reader.core.database.entity.ImportTaskEntity
import com.xinyue.reader.core.database.entity.PendingFileCleanupEntity
import com.xinyue.reader.core.database.entity.ChapterEntity
import com.xinyue.reader.core.database.entity.SearchChunkEntity
import com.xinyue.reader.core.database.entity.SearchIndexStateEntity
import com.xinyue.reader.core.database.entity.BookReaderOverridesEntity
import com.xinyue.reader.core.database.entity.BookGroupEntity
import com.xinyue.reader.core.database.entity.BookGroupMembershipEntity
import com.xinyue.reader.core.database.entity.ImportedFontEntity
import com.xinyue.reader.core.database.entity.ReaderGlobalSettingsEntity
import com.xinyue.reader.core.database.entity.ReaderThemeEntity
import com.xinyue.reader.core.database.entity.ReadingDailyStatEntity
import com.xinyue.reader.core.database.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFails

@RunWith(AndroidJUnit4::class)
class XinYueDatabaseTest {
    private lateinit var database: XinYueDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, XinYueDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun bookAndProgressSurviveARealRoomRoundTrip() = runTest {
        val book = sampleBook()
        database.bookDao().insert(book)
        database.readingProgressDao().upsert(
            ReadingProgressEntity(
                bookId = book.id,
                offset = 128,
                contextHash = "context",
                contentLength = book.contentLength,
                updatedAtEpochMillis = 30,
            ),
        )

        assertThat(database.bookDao().observeAll().first()).containsExactly(book)
        assertThat(database.readingProgressDao().get(book.id)?.offset).isEqualTo(128)

        database.annotationDao().upsert(
            AnnotationEntity(
                id = "bookmark-1",
                bookId = book.id,
                kind = "BOOKMARK",
                startOffset = 128,
                endOffset = 128,
                prefix = "",
                suffix = "",
                selectedSha256 = null,
                color = null,
                note = null,
                createdAtEpochMillis = 31,
                updatedAtEpochMillis = 31,
            ),
        )
        assertThat(database.annotationDao().observeForBookAndKind(book.id, "BOOKMARK").first().single().startOffset)
            .isEqualTo(128)
    }

    @Test
    fun readerPersonalizationDaosRoundTripAndBookOverrideCascades() = runTest {
        val book = sampleBook()
        database.bookDao().insert(book)
        val settingsDao = database.readerSettingsDao()
        val global = ReaderGlobalSettingsEntity(
            settingsJson = "{\"fontSizeSp\":24}",
            scheduleJson = "{\"mode\":\"FIXED_TIME\"}",
            updatedAtEpochMillis = 10,
        )
        val overrides = BookReaderOverridesEntity(
            bookId = book.id,
            overridesJson = "{\"fontSizeSp\":28}",
            updatedAtEpochMillis = 11,
        )
        val theme = ReaderThemeEntity("theme-1", "夜读", "{}", false, 12)
        val font = ImportedFontEntity(
            id = "font-1",
            displayName = "测试字体",
            privateRelativePath = "fonts/font-1/font.ttf",
            contentSha256 = "font-sha-256",
            sizeBytes = 2048,
            createdAtEpochMillis = 13,
        )

        settingsDao.upsertGlobal(global)
        settingsDao.upsertBookOverrides(overrides)
        database.readerThemeDao().upsert(theme)
        database.importedFontDao().insert(font)

        assertThat(settingsDao.observeGlobal().first()).isEqualTo(global)
        assertThat(settingsDao.observeBookOverrides(book.id).first()).isEqualTo(overrides)
        assertThat(database.readerThemeDao().observeAll().first()).containsExactly(theme)
        assertThat(database.importedFontDao().observeAll().first()).containsExactly(font)
        assertThat(database.importedFontDao().findBySha256(font.contentSha256)).isEqualTo(font)

        database.bookDao().deleteAndQueueFileCleanup(book.id, queuedAtEpochMillis = 20)
        assertThat(settingsDao.observeBookOverrides(book.id).first()).isNull()
        assertThat(settingsDao.observeGlobal().first()).isEqualTo(global)
    }

    @Test
    fun groupsNormalizeUniqueNamesAndExposeDeterministicOrdering() = runTest {
        val dao = database.bookGroupDao()
        dao.insert(BookGroupEntity("group-z", "  置顶  ", 0, 30, 30))
        dao.insert(BookGroupEntity("group-b", "乙组", 1, 20, 20))
        dao.insert(BookGroupEntity("group-a", "甲组", 1, 10, 10))

        assertThat(dao.observeAll().first().map(BookGroupEntity::id))
            .containsExactly("group-z", "group-a", "group-b").inOrder()
        assertThat(dao.get("group-z")?.name).isEqualTo("置顶")
        assertFails {
            dao.insert(BookGroupEntity("group-duplicate", " 置顶 ", 2, 40, 40))
        }
    }

    @Test
    fun collectionMembershipsAreManyToManyAndDeletingACollectionNeverDeletesBooks() = runTest {
        val firstBook = sampleBook()
        val secondBook = sampleBook().copy(id = "book-2", title = "另一本书")
        database.bookDao().insert(firstBook)
        database.bookDao().insert(secondBook)
        val dao = database.bookGroupDao()
        val scienceFiction = BookGroupEntity("group-1", "科幻", 0, 10, 10)
        val favorites = BookGroupEntity("group-2", "收藏", 1, 11, 11)
        dao.insert(scienceFiction)
        dao.insert(favorites)

        dao.addMemberships(
            listOf(
                BookGroupMembershipEntity(firstBook.id, scienceFiction.id),
                BookGroupMembershipEntity(firstBook.id, favorites.id),
                BookGroupMembershipEntity(secondBook.id, favorites.id),
            ),
        )
        assertThat(dao.getAllMembershipsForBackup())
            .containsExactly(
                BookGroupMembershipEntity(firstBook.id, scienceFiction.id),
                BookGroupMembershipEntity(firstBook.id, favorites.id),
                BookGroupMembershipEntity(secondBook.id, favorites.id),
            )

        dao.deleteAndUnassign(favorites.id)
        assertThat(dao.get(favorites.id)).isNull()
        assertThat(dao.getAllMembershipsForBackup())
            .containsExactly(BookGroupMembershipEntity(firstBook.id, scienceFiction.id))
        assertThat(database.bookDao().get(firstBook.id)).isNotNull()
        assertThat(database.bookDao().get(secondBook.id)).isNotNull()
    }

    @Test
    fun readingSessionsAndDailyStatisticsCascadeWithTheirBook() = runTest {
        val book = sampleBook()
        database.bookDao().insert(book)
        val dao = database.readingSessionDao()
        val session = ReadingSessionEntity(
            id = "session-1",
            bookId = book.id,
            startedAtEpochMillis = 1_000,
            lastInteractionAtEpochMillis = 2_000,
            endedAtEpochMillis = 3_000,
            activeMillis = 2_000,
        )
        dao.insertSession(session)
        dao.upsertDailyStat(
            ReadingDailyStatEntity(
                bookId = book.id,
                localEpochDay = 20_000,
                activeMillis = 2_000,
                sessionCount = 1,
            ),
        )
        dao.upsertDailyStat(
            ReadingDailyStatEntity(
                bookId = book.id,
                localEpochDay = 20_000,
                activeMillis = 3_000,
                sessionCount = 2,
            ),
        )

        assertThat(dao.getSession(session.id)).isEqualTo(session)
        assertThat(dao.getDailyStat(book.id, 20_000)).isEqualTo(
            ReadingDailyStatEntity(book.id, 20_000, 3_000, 2),
        )
        assertThat(dao.countDailyStats(book.id, 20_000)).isEqualTo(1)

        database.bookDao().delete(book.id)
        assertThat(dao.getSession(session.id)).isNull()
        assertThat(dao.getDailyStat(book.id, 20_000)).isNull()
    }

    @Test
    fun importTasksExposeNewestPersistedBatchAndAcceptProgressUpdates() = runTest {
        val old = sampleImportTask(id = "old-0", batchId = "0001-old", createdAt = 1)
        val current = sampleImportTask(id = "new-0", batchId = "0002-new", createdAt = 2)
        database.importTaskDao().insertAll(listOf(old, current))

        database.importTaskDao().update(current.copy(status = "RUNNING", progressPercent = 45))

        val tasks = database.importTaskDao().observeLatestBatch().first()
        assertThat(tasks).containsExactly(current.copy(status = "RUNNING", progressPercent = 45))
    }

    @Test
    fun chapterIndexReplacementIsOrderedAndTiedToBookLifecycle() = runTest {
        val book = sampleBook()
        database.bookDao().insert(book)
        database.chapterDao().replaceForBook(
            book.id,
            listOf(
                ChapterEntity(book.id, startOffset = 20, title = "第二章"),
                ChapterEntity(book.id, startOffset = 0, title = "第一章"),
            ),
        )

        assertThat(database.chapterDao().getForBook(book.id).map(ChapterEntity::title))
            .containsExactly("第一章", "第二章").inOrder()

        database.bookDao().updateAndInvalidateChangedContent(book.copy(title = "只改书名"))
        assertThat(database.chapterDao().getForBook(book.id)).hasSize(2)

        database.bookDao().updateAndInvalidateChangedContent(book.copy(contentSha256 = "new-hash"))
        assertThat(database.chapterDao().getForBook(book.id)).isEmpty()
        database.chapterDao().replaceForBook(
            book.id,
            listOf(ChapterEntity(book.id, startOffset = 0, title = "新正文")),
        )

        database.bookDao().deleteAndQueueFileCleanup(book.id, queuedAtEpochMillis = 42)
        assertThat(database.chapterDao().getForBook(book.id)).isEmpty()
    }

    @Test
    fun manualChapterEditsAreProtectedUntilExplicitRuleChange() = runTest {
        val book = sampleBook()
        database.bookDao().insert(book)
        val dao = database.chapterIndexDao()

        assertThat(
            dao.replaceAutomatically(
                bookId = book.id,
                chapters = listOf(ChapterEntity(book.id, 0, "第一章")),
                ruleSet = "STANDARD",
                updatedAtEpochMillis = 10,
            ),
        ).isTrue()
        dao.replaceManually(
            bookId = book.id,
            chapters = listOf(ChapterEntity(book.id, 12, "我整理的开篇")),
            fallbackRuleSet = "STANDARD",
            updatedAtEpochMillis = 20,
        )

        assertThat(
            dao.replaceAutomatically(
                bookId = book.id,
                chapters = listOf(ChapterEntity(book.id, 30, "自动分析结果")),
                ruleSet = "BROAD",
                updatedAtEpochMillis = 30,
            ),
        ).isFalse()
        assertThat(dao.getChapters(book.id).single().title).isEqualTo("我整理的开篇")
        assertThat(dao.getConfig(book.id)?.manuallyEdited).isTrue()
        assertThat(dao.getConfig(book.id)?.ruleSet).isEqualTo("STANDARD")

        dao.replaceForRuleChange(
            bookId = book.id,
            chapters = listOf(ChapterEntity(book.id, 30, "第三十章")),
            ruleSet = "BROAD",
            updatedAtEpochMillis = 40,
        )
        assertThat(dao.getChapters(book.id).single().title).isEqualTo("第三十章")
        assertThat(dao.getConfig(book.id)?.manuallyEdited).isFalse()
        assertThat(dao.getConfig(book.id)?.ruleSet).isEqualTo("BROAD")

        database.bookDao().deleteAndQueueFileCleanup(book.id, queuedAtEpochMillis = 50)
        assertThat(dao.getConfig(book.id)).isNull()
        assertThat(dao.getChapters(book.id)).isEmpty()
    }

    @Test
    fun trigramAndShortSearchUseOnlyTheActiveBookGeneration() = runTest {
        val book = sampleBook()
        val otherBook = sampleBook().copy(id = "book-2", title = "另一本书")
        database.bookDao().insert(book)
        database.bookDao().insert(otherBook)
        val dao = database.searchIndexDao()
        dao.beginBuild(searchState(book.id, active = null, building = "gen-1", status = "BUILDING"))
        dao.insertChunks(
            listOf(
                SearchChunkEntity(
                    bookId = book.id,
                    generationId = "gen-1",
                    chapterStartOffset = 0,
                    startOffset = 0,
                    content = "第一章 星河璀璨，故人重逢。百分%和下划_也保留。",
                ),
                SearchChunkEntity(
                    bookId = book.id,
                    generationId = "gen-2",
                    chapterStartOffset = 100,
                    startOffset = 100,
                    content = "尚未激活的新一代独有内容",
                ),
                SearchChunkEntity(
                    bookId = otherBook.id,
                    generationId = "other-gen",
                    chapterStartOffset = 0,
                    startOffset = 0,
                    content = "另一本书的秘密内容",
                ),
            ),
        )
        dao.activate(
            searchState(book.id, active = "gen-1", building = null, status = "READY"),
            generationId = "gen-1",
        )
        dao.beginBuild(searchState(otherBook.id, active = null, building = "other-gen", status = "BUILDING"))
        dao.activate(
            searchState(otherBook.id, active = "other-gen", building = null, status = "READY"),
            generationId = "other-gen",
        )

        assertThat(dao.searchTrigram(book.id, "\"星河璀璨\"", 20).map { it.startOffset })
            .containsExactly(0L)
        assertThat(dao.searchShort(book.id, "%故人%", 20).map { it.startOffset })
            .containsExactly(0L)
        assertThat(dao.searchShort(book.id, "%百分\\%%", 20)).hasSize(1)
        assertThat(dao.searchShort(book.id, "%下划\\_%", 20)).hasSize(1)
        assertThat(dao.searchTrigram(book.id, "\"新一代\"", 20)).isEmpty()
        assertThat(dao.searchTrigram(book.id, "\"秘密内容\"", 20)).isEmpty()

        database.bookDao().deleteAndQueueFileCleanup(book.id, queuedAtEpochMillis = 99)
        assertThat(dao.getState(book.id)).isNull()
        assertThat(dao.searchTrigram(book.id, "\"星河璀璨\"", 20)).isEmpty()
    }

    @Test
    fun deletingABookAtomicallyQueuesItsPrivateFilesForDurableCleanup() = runTest {
        val book = sampleBook()
        database.bookDao().insert(book)
        database.readingProgressDao().upsert(
            ReadingProgressEntity(
                bookId = book.id,
                offset = 12,
                contextHash = "context",
                contentLength = book.contentLength,
                updatedAtEpochMillis = 40,
            ),
        )
        database.annotationDao().upsert(
            AnnotationEntity(
                id = "bookmark-delete",
                bookId = book.id,
                kind = "BOOKMARK",
                startOffset = 12,
                endOffset = 12,
                prefix = "",
                suffix = "",
                selectedSha256 = null,
                color = null,
                note = null,
                createdAtEpochMillis = 41,
                updatedAtEpochMillis = 41,
            ),
        )

        database.bookDao().deleteAndQueueFileCleanup(book.id, queuedAtEpochMillis = 42)

        assertThat(database.bookDao().get(book.id)).isNull()
        assertThat(database.readingProgressDao().get(book.id)).isNull()
        assertThat(database.annotationDao().observeForBook(book.id).first()).isEmpty()
        assertThat(database.pendingFileCleanupDao().get(book.id)).isEqualTo(
            PendingFileCleanupEntity(
                bookId = book.id,
                originalPath = book.originalPath,
                normalizedPath = book.normalizedPath,
                queuedAtEpochMillis = 42,
            ),
        )
    }

    @Test
    fun failedBookDeletionRollsBackTheQueuedFileCleanupRecord() = runTest {
        val book = sampleBook()
        database.bookDao().insert(book)
        database.useWriterConnection { connection ->
            connection.executeSQL(
                """
                CREATE TRIGGER fail_book_delete
                BEFORE DELETE ON books
                BEGIN
                    SELECT RAISE(ABORT, 'blocked for rollback test');
                END
                """.trimIndent(),
            )
        }

        assertFails {
            database.bookDao().deleteAndQueueFileCleanup(book.id, queuedAtEpochMillis = 42)
        }

        assertThat(database.bookDao().get(book.id)).isEqualTo(book)
        assertThat(database.pendingFileCleanupDao().get(book.id)).isNull()
    }

    @Test
    fun batchDeletionQueuesAllRowsAndDeletesAllBooksInOneTransaction() = runTest {
        val first = sampleBook().copy(id = "book-1")
        val second = sampleBook().copy(
            id = "book-2",
            originalPath = "books/book-2/original.txt",
            normalizedPath = "books/book-2/content.txt",
        )
        database.bookDao().insert(first)
        database.bookDao().insert(second)

        database.bookDao().deleteManyAndQueueFileCleanup(setOf(first.id, second.id), 100)

        assertThat(database.bookDao().observeAll().first()).isEmpty()
        assertThat(database.pendingFileCleanupDao().getAll().map { it.bookId })
            .containsExactly("book-1", "book-2")
    }

    @Test
    fun batchDeletionRollsBackEarlierCleanupRowsWhenLaterQueueingFails() = runTest {
        val first = sampleBook().copy(id = "book-1")
        val second = sampleBook().copy(
            id = "book-2",
            originalPath = "books/book-2/original.txt",
            normalizedPath = "books/book-2/content.txt",
        )
        database.bookDao().insert(first)
        database.bookDao().insert(second)
        database.useWriterConnection { connection ->
            connection.executeSQL(
                """
                CREATE TRIGGER fail_second_cleanup
                BEFORE INSERT ON pending_file_cleanup
                WHEN NEW.bookId = 'book-2'
                BEGIN
                    SELECT RAISE(ABORT, 'blocked second cleanup');
                END
                """.trimIndent(),
            )
        }

        assertFails {
            database.bookDao().deleteManyAndQueueFileCleanup(setOf(first.id, second.id), 100)
        }

        assertThat(database.bookDao().observeAll().first().map { it.id })
            .containsExactly("book-1", "book-2")
        assertThat(database.pendingFileCleanupDao().getAll()).isEmpty()
    }

    private fun sampleBook() = BookEntity(
        id = "book-1",
        title = "测试小说",
        author = null,
        originalFileName = "测试.txt",
        originalPath = "books/book-1/original.txt",
        normalizedPath = "books/book-1/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "hash",
        contentLength = 200,
        createdAtEpochMillis = 10,
        lastOpenedAtEpochMillis = null,
    )

    private fun sampleImportTask(id: String, batchId: String, createdAt: Long) = ImportTaskEntity(
        id = id,
        batchId = batchId,
        itemIndex = 0,
        totalItems = 1,
        attempt = 0,
        uriString = "content://books/$id",
        displayName = "$id.txt",
        preferredCharsetName = null,
        duplicateResolution = "ASK",
        status = "QUEUED",
        progressPercent = 0,
        bookId = null,
        existingBookId = null,
        existingBookTitle = null,
        errorMessage = null,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = createdAt,
    )

    private fun searchState(
        bookId: String,
        active: String?,
        building: String?,
        status: String,
    ) = SearchIndexStateEntity(
        bookId = bookId,
        activeGenerationId = active,
        buildingGenerationId = building,
        status = status,
        contentSha256 = "hash",
        indexedUtf16Length = if (status == "READY") 200 else 0,
        updatedAtEpochMillis = 10,
        errorMessage = null,
    )
}
