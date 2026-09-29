package com.xinyue.reader.core.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.XinYueDatabase
import com.xinyue.reader.core.domain.model.Book
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryStatisticsRepositoryInstrumentedTest {
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
    fun closeDatabase() = database.close()

    @Test
    fun groupFinishedAndCommittedBatchDeleteCrossTheRealRoomBoundary() = runTest {
        val books = RoomBookRepository(database.bookDao(), database.readingProgressDao())
        val groups = RoomBookGroupRepository(database.bookGroupDao(), database.bookDao())
        books.addBook(book("book-1"))
        books.addBook(book("book-2"))

        val group = groups.create("  待读  ")
        groups.rename(group.id, "本月")
        groups.moveBooks(setOf("book-1", "book-2"), group.id)
        books.markFinished(setOf("book-1"), true)

        assertThat(groups.observeAll().first().single().name).isEqualTo("本月")
        assertThat(books.getBook("book-1")!!.finished).isTrue()
        assertThat(groups.observeMemberships().first().map { it.bookId to it.collectionId })
            .containsExactly(
                "book-1" to group.id,
                "book-2" to group.id,
            )

        groups.delete(group.id)
        assertThat(books.observeBooks().first()).hasSize(2)
        assertThat(groups.observeMemberships().first()).isEmpty()

        books.deleteBooks(setOf("book-1", "book-2"))
        assertThat(books.observeBooks().first()).isEmpty()
        assertThat(database.pendingFileCleanupDao().getAll().map { it.bookId })
            .containsExactly("book-1", "book-2")
    }

    @Test
    fun deterministicClockValuesCapAndSplitStatisticsInRealRoom() = runTest {
        val zone = ZoneId.of("UTC")
        val books = RoomBookRepository(database.bookDao(), database.readingProgressDao())
        books.addBook(book("book-1"))
        var nextId = 0
        val sessions = RoomReadingSessionRepository(
            database.readingSessionDao(),
            { zone },
            { "session-${++nextId}" },
        )
        val start = Instant.parse("2026-07-15T23:58:00Z").toEpochMilli()
        val session = sessions.begin("book-1", start)

        sessions.recordInteraction(session, start + 10 * 60_000L)
        sessions.end(session, start + 20 * 60_000L)

        val bookTotal = sessions.observeBookStatistics("book-1").first()
        assertThat(bookTotal.activeMillis).isEqualTo(10 * 60_000L)
        assertThat(bookTotal.sessionCount).isEqualTo(1)
        val rows = database.readingSessionDao().observeBookDailyStats("book-1").first()
        assertThat(rows.map { LocalDate.ofEpochDay(it.localEpochDay) })
            .containsExactly(LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 16))
            .inOrder()
        assertThat(rows.sumOf { it.activeMillis }).isEqualTo(10 * 60_000L)

        val dayStart = Instant.parse("2026-07-16T00:00:00Z").toEpochMilli()
        val dayTotal = sessions.observeStatistics(dayStart, dayStart + 24 * 60 * 60_000L).first()
        assertThat(dayTotal.activeMillis).isEqualTo(8 * 60_000L)
        assertThat(sessions.observeBookStatistics("book-1", dayStart, dayStart + 24 * 60 * 60_000L).first())
            .isEqualTo(dayTotal.copy(bookId = "book-1"))
    }

    private fun book(id: String) = Book(
        id = id,
        title = "公开测试书 $id",
        author = null,
        originalFileName = "$id.txt",
        originalPath = "books/$id/original.txt",
        normalizedPath = "books/$id/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "sha-$id",
        contentLength = 100,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = null,
    )
}
