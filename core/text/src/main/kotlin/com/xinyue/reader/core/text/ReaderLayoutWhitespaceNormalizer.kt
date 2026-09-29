package com.xinyue.reader.core.text

/**
 * Produces a same-length reader layout copy with TXT separator whitespace normalized.
 *
 * Ordinary whitespace between content paragraphs retains one logical line break; line-leading,
 * file-leading/trailing, and hard-boundary whitespace is replaced with zero-width placeholders.
 * Non-whitespace source characters and all UTF-16 offsets remain unchanged.
 */
object ReaderLayoutWhitespaceNormalizer {
    fun normalize(
        text: String,
        hardBoundaries: Collection<Int> = emptyList(),
    ): String {
        if (text.isEmpty()) return text
        val validHardBoundaries = hardBoundaries.asSequence()
            .filter { it in 1 until text.length }
            .toSet()
        var normalized: CharArray? = null

        fun collapse(index: Int) {
            val output = normalized ?: text.toCharArray().also { normalized = it }
            output[index] = COLLAPSED_LAYOUT_CHARACTER
        }

        var runStart = 0
        while (runStart < text.length) {
            if (!text[runStart].isLayoutWhitespace()) {
                runStart += 1
                continue
            }
            var runEnd = runStart + 1
            while (runEnd < text.length && text[runEnd].isLayoutWhitespace()) {
                runEnd += 1
            }
            val suppressEntireRun = runStart == 0 ||
                runEnd == text.length ||
                validHardBoundaries.contains(runEnd)
            if (suppressEntireRun) {
                for (index in runStart until runEnd) collapse(index)
            } else {
                var lastBreakStart = -1
                var lastBreakEndExclusive = -1
                var index = runStart
                while (index < runEnd) {
                    when (text[index]) {
                        '\r' -> {
                            lastBreakStart = index
                            lastBreakEndExclusive = if (index + 1 < runEnd && text[index + 1] == '\n') {
                                index + 2
                            } else {
                                index + 1
                            }
                            index = lastBreakEndExclusive
                        }
                        '\n' -> {
                            lastBreakStart = index
                            lastBreakEndExclusive = index + 1
                            index += 1
                        }
                        else -> index += 1
                    }
                }
                if (lastBreakStart >= 0) {
                    for (collapseIndex in runStart until lastBreakStart) collapse(collapseIndex)
                    for (collapseIndex in lastBreakEndExclusive until runEnd) collapse(collapseIndex)
                }
            }
            runStart = runEnd
        }

        return normalized?.concatToString() ?: text
    }

    private fun Char.isLayoutWhitespace(): Boolean =
        this == '\r' || this == '\n' || this == '\t' || this == ' ' || this == '\u3000'

    private const val COLLAPSED_LAYOUT_CHARACTER = '\u2060'
}
