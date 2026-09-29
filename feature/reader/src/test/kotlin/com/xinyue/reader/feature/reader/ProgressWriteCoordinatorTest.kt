package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProgressWriteCoordinatorTest {
    @Test
    fun `rapid submissions coalesce and flush writes the latest value`() = runTest {
        val writes = mutableListOf<Int>()
        val coordinator = ProgressWriteCoordinator<Int>(this) { writes += it }

        coordinator.submit(1)
        advanceTimeBy(300)
        coordinator.submit(2)
        advanceTimeBy(499)
        assertThat(writes).isEmpty()
        advanceUntilIdle()
        assertThat(writes).containsExactly(2)

        coordinator.submit(3)
        coordinator.flushNow()
        assertThat(writes).containsExactly(2, 3).inOrder()
    }

    @Test
    fun `failed writes stay pending for a later flush and duplicates do not rewrite`() = runTest {
        var fail = true
        val writes = mutableListOf<Int>()
        val coordinator = ProgressWriteCoordinator<Int>(this) { value ->
            if (fail) error("temporary")
            writes += value
        }
        coordinator.submit(9)
        runCatching { coordinator.flushNow() }
        fail = false
        coordinator.flushNow()
        coordinator.submit(9)
        advanceUntilIdle()

        assertThat(writes).containsExactly(9)
    }
}
