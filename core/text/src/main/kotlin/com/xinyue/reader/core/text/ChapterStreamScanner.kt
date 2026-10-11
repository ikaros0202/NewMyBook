package com.xinyue.reader.core.text

/** Detects only complete physical lines, retaining at most one possible heading between reads. */
class ChapterStreamScanner(private val ruleSet: ChapterRuleSet) {
    private val line = StringBuilder()
    private var leadingOffset = 0L
    private var pendingSpaces = 0
    private val whitespace = StringBuilder()
    private var tooLong = false
    private var nextOffset = 0L

    fun accept(startOffset: Long, text: String, endOfBook: Boolean): List<DetectedChapter> = buildList {
        fun finishLine() {
            if (!tooLong && line.isNotEmpty()) {
                ChapterDetector.detect(line.toString(), ruleSet)
                    .filterNot { it.title == "正文" && it.startOffset == 0 }
                    .forEach { add(it.copy(startOffset = (leadingOffset + it.startOffset).toInt())) }
            }
            line.clear()
            pendingSpaces = 0
            whitespace.clear()
            tooLong = false
        }
        text.forEachIndexed { index, character ->
            val offset = startOffset + index
            if (offset < nextOffset) return@forEachIndexed // Surrogate-safe sources can repeat a boundary unit.
            nextOffset = offset + 1
            when {
                character == '\n' || character == '\r' -> finishLine()
                tooLong -> Unit
                character == ' ' || character == '\t' || character == '\u3000' -> {
                    if (line.isNotEmpty()) {
                        pendingSpaces++
                        if (whitespace.length < ChapterDetector.MAX_TITLE_CHARACTERS) whitespace.append(character)
                    }
                }
                else -> {
                    if (line.isEmpty()) leadingOffset = offset
                    if (line.length + pendingSpaces + 1 > ChapterDetector.MAX_TITLE_CHARACTERS) {
                        tooLong = true
                        line.clear()
                    } else {
                        line.append(whitespace)
                        whitespace.clear()
                        pendingSpaces = 0
                        line.append(character)
                    }
                }
            }
        }
        if (endOfBook) finishLine()
    }
}
