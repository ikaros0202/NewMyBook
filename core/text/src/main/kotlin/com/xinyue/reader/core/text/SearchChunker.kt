package com.xinyue.reader.core.text

data class SearchChunk(
    /** Absolute UTF-16 offset in the normalized book text. */
    val startOffset: Long,
    val content: String,
)

object SearchChunker {
    const val TARGET_UTF16_UNITS = 16 * 1024
    const val OVERLAP_CODE_POINTS = 2

    fun fromWindow(windowStart: Long, text: String): Sequence<SearchChunk> = sequence {
        require(windowStart >= 0) { "Window start cannot be negative" }
        var start = 0
        while (start < text.length) {
            var end = (start + TARGET_UTF16_UNITS).coerceAtMost(text.length)
            if (
                end < text.length &&
                text[end - 1].isHighSurrogate() &&
                text[end].isLowSurrogate()
            ) {
                end--
            }
            yield(SearchChunk(windowStart + start, text.substring(start, end)))
            if (end == text.length) break
            val overlap = OVERLAP_CODE_POINTS.coerceAtMost(text.codePointCount(0, end))
            val nextStart = text.offsetByCodePoints(end, -overlap)
            check(nextStart > start) { "Search chunker did not advance" }
            start = nextStart
        }
    }
}
