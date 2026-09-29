package com.xinyue.reader.feature.home

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReadingProgress
import com.xinyue.reader.core.domain.model.ReadingStatistics
import com.xinyue.reader.core.domain.model.TextAnchor
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.domain.repository.ReadingSessionRepository
import com.xinyue.reader.core.domain.time.EpochClock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `combines books progress and real day week month statistics`() =
        runTest(dispatcherRule.dispatcher) {
            val now = LocalDate.of(2026, 7, 19).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
            val books = listOf(book("hero", openedAt = now - 1_000), book("recent", openedAt = now - 2_000))
            val bookRepository = FakeBookRepository(books).apply {
                progress.value = listOf(
                    ReadingProgress("hero", TextAnchor(25, "context"), 100, now),
                )
            }
            val viewModel = HomeViewModel(
                bookRepository = bookRepository,
                sessionRepository = FakeSessionRepository(),
                clock = EpochClock { now },
                dispatcher = dispatcherRule.dispatcher,
                zoneIdProvider = { ZoneOffset.UTC },
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.hero?.book?.id).isEqualTo("hero")
            assertThat(state.heroProgressFraction).isEqualTo(0.25)
            assertThat(state.recent.map(Book::id)).containsExactly("recent")
            assertThat(state.today.activeMillis).isEqualTo(60_000)
            assertThat(state.today.sessionCount).isEqualTo(2)
            assertThat(state.week.activeMillis).isEqualTo(120_000)
            assertThat(state.month.activeMillis).isEqualTo(180_000)
        }

    private class FakeBookRepository(initial: List<Book>) : BookRepository {
        val books = MutableStateFlow(initial)
        val progress = MutableStateFlow<List<ReadingProgress>>(emptyList())
        override fun observeBooks(): Flow<List<Book>> = books
        override fun observeProgress(): Flow<List<ReadingProgress>> = progress
        override suspend fun addBook(book: Book) = error("unused")
        override suspend fun getBook(bookId: String): Book? = books.value.firstOrNull { it.id == bookId }
        override suspend fun findBySha256(contentSha256: String): Book? = null
        override suspend fun saveProgress(progress: ReadingProgress) = error("unused")
        override suspend fun getProgress(bookId: String): ReadingProgress? = progress.value.firstOrNull { it.bookId == bookId }
        override suspend fun renameBook(bookId: String, title: String) = error("unused")
        override suspend fun deleteBook(bookId: String) = error("unused")
        override suspend fun replaceBook(existingBookId: String, replacement: Book) = error("unused")
        override suspend fun markOpened(bookId: String, epochMillis: Long) = error("unused")
    }

    private class FakeSessionRepository : ReadingSessionRepository {
        override suspend fun begin(bookId: String, nowEpochMillis: Long): String = error("unused")
        override suspend fun recordInteraction(sessionId: String, nowEpochMillis: Long) = error("unused")
        override suspend fun end(sessionId: String, nowEpochMillis: Long) = error("unused")
        override suspend fun closeStaleSessions(nowEpochMillis: Long) = error("unused")
        override fun observeBookStatistics(bookId: String): Flow<ReadingStatistics> = error("unused")
        override fun observeStatistics(rangeStartEpochMillis: Long, rangeEndEpochMillis: Long): Flow<ReadingStatistics> {
            val days = (rangeEndEpochMillis - rangeStartEpochMillis) / 86_400_000L
            val active = when {
                days <= 1 -> 60_000L
                days <= 7 -> 120_000L
                else -> 180_000L
            }
            return flowOf(ReadingStatistics(null, active, if (days <= 1) 2 else 4, rangeStartEpochMillis, rangeEndEpochMillis))
        }
        override suspend fun clearStatistics() = error("unused")
    }

    private fun book(id: String, openedAt: Long?) = Book(
        id = id,
        title = id,
        author = "作者",
        originalFileName = "$id.txt",
        originalPath = "books/$id/original.txt",
        normalizedPath = "books/$id/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "hash-$id",
        contentLength = 100,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = openedAt,
    )

    class MainDispatcherRule(
        val dispatcher: TestDispatcher = StandardTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
        override fun finished(description: Description) = Dispatchers.resetMain()
    }
}
