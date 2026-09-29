package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ImportExecutionGateTest {
    @Test
    fun `does not let two import publications overlap`() = runTest {
        val gate = ImportExecutionGate()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val entries = mutableListOf<String>()

        val first = async {
            gate.run {
                entries += "first-enter"
                firstEntered.complete(Unit)
                releaseFirst.await()
                entries += "first-exit"
            }
        }
        firstEntered.await()
        val second = async {
            gate.run {
                entries += "second-enter"
            }
        }

        runCurrent()
        assertThat(entries).containsExactly("first-enter").inOrder()

        releaseFirst.complete(Unit)
        first.await()
        second.await()
        assertThat(entries).containsExactly("first-enter", "first-exit", "second-enter").inOrder()
    }
}
