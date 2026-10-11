package com.xinyue.reader.feature.reader

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal val READER_AUTO_PAGE_INTERVALS_SECONDS = listOf(10, 15, 20, 30, 60)
internal const val DEFAULT_READER_AUTO_PAGE_INTERVAL_SECONDS = 20
internal val READER_STOP_TIMER_MINUTES = listOf(15, 30, 60)

internal data class ReaderAutoPageState(
    val isRunning: Boolean = false,
    val intervalSeconds: Int = DEFAULT_READER_AUTO_PAGE_INTERVAL_SECONDS,
)

/**
 * Serializes timed page turns. A page turn must finish before the next interval starts, so a
 * slow animation or a temporarily unavailable page cannot create a backlog of turns.
 */
internal class ReaderAutoPageController(
    private val scope: CoroutineScope,
    private val advancePage: suspend () -> Boolean,
) {
    private val mutableState = MutableStateFlow(ReaderAutoPageState())
    val state: StateFlow<ReaderAutoPageState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var generation = 0L

    fun start(intervalSeconds: Int) {
        val safeInterval = intervalSeconds
            .takeIf { it in READER_AUTO_PAGE_INTERVALS_SECONDS }
            ?: DEFAULT_READER_AUTO_PAGE_INTERVAL_SECONDS
        val expectedGeneration = ++generation
        job?.cancel()
        mutableState.value = ReaderAutoPageState(isRunning = true, intervalSeconds = safeInterval)
        job = scope.launch {
            try {
                while (isActive) {
                    delay(safeInterval * 1_000L)
                    if (!isActive) return@launch
                    val advanced = try {
                        advancePage()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                    if (!advanced) {
                        stopInternal()
                        return@launch
                    }
                }
            } finally {
                if (expectedGeneration == generation && mutableState.value.isRunning) {
                    job = null
                    mutableState.value = mutableState.value.copy(isRunning = false)
                }
            }
        }
    }

    fun pause() {
        generation += 1
        stopInternal()
    }

    fun close() {
        generation += 1
        stopInternal()
    }

    private fun stopInternal() {
        job?.cancel()
        job = null
        if (mutableState.value.isRunning) {
            mutableState.value = mutableState.value.copy(isRunning = false)
        }
    }
}

internal data class ReaderStopTimerState(
    val deadlineElapsedRealtime: Long? = null,
    val remainingMillis: Long? = null,
    val isForeground: Boolean = true,
) {
    val isActive: Boolean
        get() = deadlineElapsedRealtime != null
}

/**
 * A foreground-aware deadline. The deadline uses elapsed realtime, not wall-clock time. The
 * timer remains scheduled in the background so it can stop TTS on time; returning to the
 * foreground checks the same deadline again before rescheduling its wake-up.
 */
internal class ReaderStopTimerController(
    private val scope: CoroutineScope,
    private val nowElapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
    private val onExpired: () -> Unit,
) {
    private val mutableState = MutableStateFlow(ReaderStopTimerState())
    val state: StateFlow<ReaderStopTimerState> = mutableState.asStateFlow()

    private var foreground = true
    private var deadlineElapsedRealtime: Long? = null
    private var generation = 0L
    private var job: Job? = null

    fun start(durationMinutes: Int) {
        val safeDuration = durationMinutes
            .takeIf { it in READER_STOP_TIMER_MINUTES }
            ?: error("不支持的定时停止时长")
        generation += 1
        job?.cancel()
        deadlineElapsedRealtime = nowElapsedRealtime() + safeDuration * 60_000L
        publishState()
        schedule(generation)
    }

    fun cancel() {
        generation += 1
        job?.cancel()
        job = null
        deadlineElapsedRealtime = null
        publishState()
    }

    fun onBackground() {
        foreground = false
        publishState()
    }

    fun onForeground() {
        foreground = true
        generation += 1
        job?.cancel()
        job = null
        val deadline = deadlineElapsedRealtime
        if (deadline != null && nowElapsedRealtime() >= deadline) {
            expire()
        } else {
            schedule(generation)
            publishState()
        }
    }

    /** Refreshes the displayed remaining time and also closes an overdue foreground timer. */
    fun refresh() {
        val deadline = deadlineElapsedRealtime
        if (foreground && deadline != null && nowElapsedRealtime() >= deadline) {
            expire()
        } else {
            publishState()
        }
    }

    fun close() {
        generation += 1
        job?.cancel()
        job = null
        deadlineElapsedRealtime = null
        foreground = false
        publishState()
    }

    private fun schedule(expectedGeneration: Long) {
        val deadline = deadlineElapsedRealtime ?: return
        val remaining = (deadline - nowElapsedRealtime()).coerceAtLeast(0L)
        job = scope.launch {
            delay(remaining)
            if (!isActive || expectedGeneration != generation) return@launch
            val currentDeadline = deadlineElapsedRealtime ?: return@launch
            if (nowElapsedRealtime() >= currentDeadline) {
                expire()
            } else {
                schedule(expectedGeneration)
            }
        }
    }

    private fun expire() {
        if (deadlineElapsedRealtime == null) return
        generation += 1
        job?.cancel()
        job = null
        deadlineElapsedRealtime = null
        publishState()
        onExpired()
    }

    private fun publishState() {
        val deadline = deadlineElapsedRealtime
        mutableState.value = ReaderStopTimerState(
            deadlineElapsedRealtime = deadline,
            remainingMillis = deadline?.let { (it - nowElapsedRealtime()).coerceAtLeast(0L) },
            isForeground = foreground,
        )
    }
}
