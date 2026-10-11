package com.xinyue.reader.feature.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One foreground speech session. Stops invalidate both delayed callbacks and pending navigation. */
internal class ReaderSpeechSession {
    var active by mutableStateOf(false)
    var requestedOffset by mutableStateOf<Int?>(null)
    var spokenRange by mutableStateOf<IntRange?>(null)
    private var generation = 0L

    fun start(offset: Int) {
        generation++
        active = true
        requestedOffset = offset
        spokenRange = null
    }

    fun stop() {
        generation++
        active = false
        requestedOffset = null
        spokenRange = null
    }

    fun beginSegment(segment: ReaderSpeechSegment): () -> Unit {
        val session = generation
        requestedOffset = null
        spokenRange = segment.startOffset until segment.endOffset
        return { if (active && session == generation) requestedOffset = segment.endOffset }
    }
}
