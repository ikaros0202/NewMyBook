package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.ReadingStatistics
import com.xinyue.reader.core.domain.repository.ReadingSessionRepository
import com.xinyue.reader.core.domain.time.EpochClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReadingSessionTrackerTest {
    @Test
    fun `foreground interaction switch background and repeated events serialize to one active session`() = runTest {
        val repository = FakeReadingSessionRepository()
        var now = 1L
        val tracker = ReadingSessionTracker(repository, EpochClock { now }, this)

        tracker.onReaderForeground("book-1")
        tracker.onReaderForeground("book-1")
        now = 2
        tracker.onInteraction()
        now = 3
        tracker.onReaderForeground("book-2")
        now = 4
        tracker.onReaderBackground()
        advanceUntilIdle()

        assertThat(repository.begunBooks).containsExactly("book-1", "book-2").inOrder()
        assertThat(repository.interactions).containsExactly("session-1" to 2L)
        assertThat(repository.ends).containsExactly("session-1" to 3L, "session-2" to 4L).inOrder()
    }

    @Test
    fun `repository failures are diagnostic cancellation propagates and later close remains safe`() = runTest {
        val repository = FakeReadingSessionRepository().apply { failure = IllegalStateException("stats failed") }
        val tracker = ReadingSessionTracker(repository, EpochClock { 10 }, this)

        tracker.onReaderForeground("book-1")
        advanceUntilIdle()
        assertThat(tracker.diagnostic.value).isEqualTo("阅读统计暂时不可用")

        repository.failure = CancellationException("cancelled")
        tracker.onReaderForeground("book-1")
        advanceUntilIdle()
        assertThat(tracker.diagnostic.value).isEqualTo("阅读统计暂时不可用")

        repository.failure = null
        tracker.onReaderForeground("book-1")
        advanceUntilIdle()
        tracker.flushAndClose()
        assertThat(repository.ends).containsExactly("session-1" to 10L)
    }

    private class FakeReadingSessionRepository : ReadingSessionRepository {
        val begunBooks = mutableListOf<String>()
        val interactions = mutableListOf<Pair<String, Long>>()
        val ends = mutableListOf<Pair<String, Long>>()
        var failure: Throwable? = null
        private var nextId = 0

        override suspend fun begin(bookId: String, nowEpochMillis: Long): String {
            failure?.let { throw it }
            begunBooks += bookId
            return "session-${++nextId}"
        }

        override suspend fun recordInteraction(sessionId: String, nowEpochMillis: Long) {
            failure?.let { throw it }
            interactions += sessionId to nowEpochMillis
        }

        override suspend fun end(sessionId: String, nowEpochMillis: Long) {
            failure?.let { throw it }
            ends += sessionId to nowEpochMillis
        }

        override suspend fun closeStaleSessions(nowEpochMillis: Long) = Unit
        override fun observeBookStatistics(bookId: String): Flow<ReadingStatistics> = flowOf(ReadingStatistics(bookId, 0, 0, 0, 0))
        override fun observeStatistics(rangeStartEpochMillis: Long, rangeEndEpochMillis: Long): Flow<ReadingStatistics> =
            flowOf(ReadingStatistics(null, 0, 0, rangeStartEpochMillis, rangeEndEpochMillis))
        override suspend fun clearStatistics() = Unit
    }
}
