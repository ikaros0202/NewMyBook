package com.xinyue.reader.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchChunkerTest {
    @Test
    fun `chunks stay bounded and overlap by two complete code points`() {
        val prefix = "甲".repeat(SearchChunker.TARGET_UTF16_UNITS - 1)
        val text = prefix + "😀乙丙丁"

        val chunks = SearchChunker.fromWindow(100, text).toList()

        assertThat(chunks).hasSize(2)
        assertThat(chunks.first().content.length).isAtMost(SearchChunker.TARGET_UTF16_UNITS)
        assertThat(chunks.first().content.last().isHighSurrogate()).isFalse()
        assertThat(chunks.last().content.first().isLowSurrogate()).isFalse()
        val firstEnd = chunks.first().startOffset + chunks.first().content.length
        assertThat(chunks.last().startOffset).isLessThan(firstEnd)
        assertThat(chunks.last().startOffset).isEqualTo(
            100L + text.offsetByCodePoints(chunks.first().content.length, -2),
        )
    }

    @Test
    fun `a trigram crossing a target boundary exists in a complete chunk`() {
        val text = "甲".repeat(SearchChunker.TARGET_UTF16_UNITS - 2) + "天地人" + "乙".repeat(10)

        val chunks = SearchChunker.fromWindow(0, text).toList()

        assertThat(chunks.any { "天地人" in it.content }).isTrue()
    }

    @Test
    fun `absolute offsets are stable and empty input yields no chunks`() {
        val text = "甲".repeat(SearchChunker.TARGET_UTF16_UNITS + 10)
        val chunks = SearchChunker.fromWindow(8_000, text).toList()

        chunks.forEach { chunk ->
            val localStart = (chunk.startOffset - 8_000).toInt()
            assertThat(chunk.content).isEqualTo(text.substring(localStart, localStart + chunk.content.length))
        }
        assertThat(SearchChunker.fromWindow(0, "").toList()).isEmpty()
    }
}
