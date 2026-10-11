package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderSpeechPositionTest {
    @Test
    fun `manual navigation invalidates pending continuation and callbacks from older sessions`() {
        val session = ReaderSpeechSession()
        session.start(100)
        val oldCompletion = session.beginSegment(ReaderSpeechSegment(100, 200, "public text"))
        oldCompletion()
        assertThat(session.requestedOffset).isEqualTo(200)

        session.stop() // Every manual navigation stops before requesting its own window.
        oldCompletion()
        assertThat(session.active).isFalse()
        assertThat(session.requestedOffset).isNull()

        session.start(8_000)
        oldCompletion()
        assertThat(session.requestedOffset).isEqualTo(8_000)
        val newCompletion = session.beginSegment(ReaderSpeechSegment(8_000, 8_100, "new public text"))
        newCompletion()
        assertThat(session.requestedOffset).isEqualTo(8_100)
    }

    @Test
    fun `speech continues at the same absolute offset after page indexes are rebased`() {
        val text = (0 until 200).joinToString("") { "%04d".format(it) }
        fun window(start: Int, end: Int, pageSize: Int) = ReaderUiState(
            content = text.substring(start, end), contentStartOffset = start, contentLength = text.length.toLong(),
            pages = (start until end step pageSize).map { ReaderPage(it, minOf(it + pageSize, end)) },
        )
        val spoken = window(0, 600, 10).speechSegmentAt(500)!!
        val continuation = window(400, 800, 13).speechSegmentAt(spoken.endOffset)!!

        assertThat(continuation.startOffset).isEqualTo(510)
        assertThat(continuation.text).isEqualTo(text.substring(510, continuation.endOffset))
        assertThat(continuation.endOffset).isEqualTo(517)
        assertThat(window(0, 500, 10).speechSegmentAt(510)).isNull()
        assertThat(window(400, 800, 13).speechSegmentAt(800)).isNull()
    }
}
