package com.xinyue.reader.feature.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProgressWriteCoordinator<T>(
    private val scope: CoroutineScope,
    private val delayMillis: Long = 500,
    private val write: suspend (T) -> Unit,
) {
    private var pending: T? = null
    private var scheduled: Job? = null
    private var lastWritten: T? = null

    fun submit(value: T) {
        pending = value
        scheduled?.cancel()
        scheduled = scope.launch {
            delay(delayMillis)
            scheduled = null
            writePending()
        }
    }

    suspend fun flushNow() {
        val job = scheduled
        scheduled = null
        job?.cancelAndJoin()
        writePending()
    }

    private suspend fun writePending() {
        val value = pending ?: return
        if (value == lastWritten) {
            if (pending == value) pending = null
            return
        }
        write(value)
        lastWritten = value
        if (pending == value) pending = null
    }
}
