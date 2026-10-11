package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderAutomationControllersTest {
    @Test
    fun `auto page waits for the current turn and pauses when the next turn fails`() = runTest {
        var calls = 0
        var inFlight = 0
        var maxInFlight = 0
        val controller = ReaderAutoPageController(this) {
            calls += 1
            inFlight += 1
            maxInFlight = maxOf(maxInFlight, inFlight)
            delay(1_000L)
            inFlight -= 1
            calls < 2
        }

        controller.start(10)
        advanceTimeBy(11_000L)
        runCurrent()
        advanceTimeBy(10_000L)
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        assertThat(calls).isEqualTo(2)
        assertThat(maxInFlight).isEqualTo(1)
        assertThat(controller.state.value.isRunning).isFalse()
    }

    @Test
    fun `stop timer expires in background and checks the same deadline on return`() = runTest {
        var now = 0L
        var expired = 0
        val controller = ReaderStopTimerController(
            scope = this,
            nowElapsedRealtime = { now },
            onExpired = { expired += 1 },
        )

        controller.start(15)
        controller.onBackground()
        now = 15 * 60_000L
        advanceTimeBy(15 * 60_000L)
        runCurrent()

        assertThat(expired).isEqualTo(1)
        assertThat(controller.state.value.isActive).isFalse()
        assertThat(controller.state.value.isForeground).isFalse()

        controller.start(15)
        controller.onBackground()
        now = 30 * 60_000L
        controller.onForeground()
        runCurrent()

        assertThat(expired).isEqualTo(2)
        assertThat(controller.state.value.isActive).isFalse()
        assertThat(controller.state.value.isForeground).isTrue()
    }

    @Test
    fun `pausing auto page does not replay missed intervals`() = runTest {
        var calls = 0
        val controller = ReaderAutoPageController(this) {
            calls += 1
            true
        }

        controller.start(10)
        advanceTimeBy(5_000L)
        controller.pause()
        advanceTimeBy(30_000L)
        runCurrent()
        assertThat(calls).isEqualTo(0)

        controller.start(10)
        advanceTimeBy(9_999L)
        runCurrent()
        assertThat(calls).isEqualTo(0)
        advanceTimeBy(1L)
        runCurrent()
        assertThat(calls).isEqualTo(1)
        controller.close()
    }
}
