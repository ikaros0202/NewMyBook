package com.xinyue.reader.feature.library

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReadingProgress
import com.xinyue.reader.core.domain.model.ReadingStatistics
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.domain.repository.ReadingSessionRepository
import com.xinyue.reader.core.domain.time.EpochClock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `period and book selection replace old aggregates and ignore missing books`() = runTest(dispatcher) {
        val sessions = FakeSessions()
        val books = MutableStateFlow(listOf(book("book-1", "甲"), book("book-2", "乙")))
        val viewModel = StatisticsViewModel(
            FakeBooks(books), sessions, EpochClock { NOW }, dispatcher, { ZoneId.of("UTC") },
        )
        advanceUntilIdle()

        viewModel.selectPeriod(StatisticsPeriod.WEEK)
        viewModel.selectBook("book-1")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.period).isEqualTo(StatisticsPeriod.WEEK)
        assertThat(viewModel.uiState.value.selectedBook?.id).isEqualTo("book-1")
        assertThat(viewModel.uiState.value.total.activeMillis).isEqualTo(1_000)

        books.value = listOf(book("book-2", "乙"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.selectedBook).isNull()
    }

    @Test
    fun `book destination survives until the first books emission`() = runTest(dispatcher) {
        val books = MutableSharedFlow<List<Book>>()
        val viewModel = StatisticsViewModel(
            FakeBooks(books), FakeSessions(), EpochClock { NOW }, dispatcher, { ZoneId.of("UTC") },
        )

        viewModel.selectBook("book-1")
        books.emit(listOf(book("book-1", "甲")))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedBook?.id).isEqualTo("book-1")
    }

    @Test
    fun `clear requires confirmation keeps data on failure and reaches zero on success`() = runTest(dispatcher) {
        val sessions = FakeSessions()
        val viewModel = StatisticsViewModel(
            FakeBooks(MutableStateFlow(listOf(book("book-1", "甲")))),
            sessions,
            EpochClock { NOW },
            dispatcher,
            { ZoneId.of("UTC") },
        )
        advanceUntilIdle()
        viewModel.requestClear()
        assertThat(viewModel.uiState.value.clearConfirmationVisible).isTrue()

        sessions.clearFailure = IllegalStateException("database is locked")
        viewModel.confirmClear()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo("清除阅读统计失败")
        assertThat(viewModel.uiState.value.total.activeMillis).isGreaterThan(0)

        sessions.clearFailure = null
        viewModel.requestClear()
        viewModel.confirmClear()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.total.activeMillis).isEqualTo(0)
    }

    private class FakeSessions : ReadingSessionRepository {
        val value = MutableStateFlow(ReadingStatistics(null, 1_000, 2, 0, Long.MAX_VALUE))
        var clearFailure: Throwable? = null
        override suspend fun begin(bookId: String, nowEpochMillis: Long) = "session"
        override suspend fun recordInteraction(sessionId: String, nowEpochMillis: Long) = Unit
        override suspend fun end(sessionId: String, nowEpochMillis: Long) = Unit
        override suspend fun closeStaleSessions(nowEpochMillis: Long) = Unit
        override fun observeBookStatistics(bookId: String): Flow<ReadingStatistics> =
            valueFor(bookId, 0, Long.MAX_VALUE)
        override fun observeBookStatistics(bookId: String, rangeStartEpochMillis: Long, rangeEndEpochMillis: Long) =
            valueFor(bookId, rangeStartEpochMillis, rangeEndEpochMillis)
        override fun observeStatistics(rangeStartEpochMillis: Long, rangeEndEpochMillis: Long): Flow<ReadingStatistics> = value
        override suspend fun clearStatistics() {
            clearFailure?.let { throw it }
            value.value = ReadingStatistics(null, 0, 0, 0, Long.MAX_VALUE)
        }
        private fun valueFor(bookId: String, start: Long, end: Long): Flow<ReadingStatistics> =
            value.map { it.copy(bookId = bookId, rangeStartEpochMillis = start, rangeEndEpochMillis = end) }
    }

    private class FakeBooks(private val books: Flow<List<Book>>) : BookRepository {
        override fun observeBooks() = books
        override fun observeProgress(): Flow<List<ReadingProgress>> = flowOf(emptyList())
        override suspend fun addBook(book: Book) = Unit
        override suspend fun getBook(bookId: String): Book? = null
        override suspend fun findBySha256(contentSha256: String): Book? = null
        override suspend fun saveProgress(progress: ReadingProgress) = Unit
        override suspend fun getProgress(bookId: String): ReadingProgress? = null
        override suspend fun renameBook(bookId: String, title: String) = Unit
        override suspend fun deleteBook(bookId: String) = Unit
        override suspend fun replaceBook(existingBookId: String, replacement: Book) = Unit
        override suspend fun markOpened(bookId: String, epochMillis: Long) = Unit
    }

    private fun book(id: String, title: String) = Book(
        id, title, null, "$id.txt", "books/$id/original.txt", "books/$id/content.txt", "UTF-8", id, 1, 1, null,
    )

    private companion object {
        val NOW = Instant.parse("2026-07-16T12:00:00Z").toEpochMilli()
    }
}
