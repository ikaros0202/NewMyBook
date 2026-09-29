package com.xinyue.reader.feature.reader

import com.xinyue.reader.core.domain.repository.ReadingSessionRepository
import com.xinyue.reader.core.domain.time.EpochClock
import com.xinyue.reader.core.domain.model.ReadingStatistics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes reader lifecycle and interaction events into at most one active statistics session.
 * Statistics failures are deliberately isolated from reading and progress persistence.
 */
internal class ReadingSessionTracker(
    private val repository: ReadingSessionRepository,
    private val clock: EpochClock,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var activeSessionId: String? = null
    private var activeBookId: String? = null
    private val _diagnostic = MutableStateFlow<String?>(null)
    val diagnostic: StateFlow<String?> = _diagnostic.asStateFlow()

    fun onReaderForeground(bookId: String) {
        val now = clock.nowEpochMillis()
        submit {
            if (activeSessionId != null && activeBookId == bookId) return@submit
            closeActive(now)
            activeSessionId = repository.begin(bookId, now)
            activeBookId = bookId
        }
    }

    fun onInteraction() {
        val now = clock.nowEpochMillis()
        submit {
            activeSessionId?.let { repository.recordInteraction(it, now) }
        }
    }

    fun onReaderBackground() {
        val now = clock.nowEpochMillis()
        submit { closeActive(now) }
    }

    suspend fun flushAndClose() {
        val now = clock.nowEpochMillis()
        mutex.withLock {
            try {
                closeActive(now)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _diagnostic.value = "阅读统计暂时不可用"
            }
        }
    }

    private fun submit(operation: suspend () -> Unit) {
        scope.launch {
            mutex.withLock {
                try {
                    operation()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    _diagnostic.value = "阅读统计暂时不可用"
                }
            }
        }
    }

    private suspend fun closeActive(nowEpochMillis: Long) {
        val sessionId = activeSessionId ?: return
        activeSessionId = null
        activeBookId = null
        repository.end(sessionId, nowEpochMillis)
    }
}

internal object EmptyReadingSessionRepository : ReadingSessionRepository {
    override suspend fun begin(bookId: String, nowEpochMillis: Long): String = ""
    override suspend fun recordInteraction(sessionId: String, nowEpochMillis: Long) = Unit
    override suspend fun end(sessionId: String, nowEpochMillis: Long) = Unit
    override suspend fun closeStaleSessions(nowEpochMillis: Long) = Unit
    override fun observeBookStatistics(bookId: String): Flow<ReadingStatistics> =
        flowOf(ReadingStatistics(bookId, 0, 0, 0, 0))
    override fun observeStatistics(
        rangeStartEpochMillis: Long,
        rangeEndEpochMillis: Long,
    ): Flow<ReadingStatistics> = flowOf(
        ReadingStatistics(null, 0, 0, rangeStartEpochMillis, rangeEndEpochMillis),
    )
    override suspend fun clearStatistics() = Unit
}
