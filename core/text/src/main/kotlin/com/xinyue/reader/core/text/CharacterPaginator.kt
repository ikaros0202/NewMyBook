package com.xinyue.reader.core.text

data class CharacterPage(
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
)

object CharacterPaginator {
    fun pageAt(
        text: String,
        anchorOffset: Int,
        maxUtf16Units: Int,
    ): CharacterPage {
        require(maxUtf16Units > 0) { "maxUtf16Units must be positive" }

        var start = anchorOffset.coerceIn(0, text.length)
        if (
            start > 0 && start < text.length &&
            text[start].isLowSurrogate() && text[start - 1].isHighSurrogate()
        ) {
            start -= 1
        }
        var end = (start + maxUtf16Units).coerceAtMost(text.length)
        if (
            end > start &&
            end < text.length &&
            text[end - 1].isHighSurrogate() &&
            text[end].isLowSurrogate()
        ) {
            end -= 1
        }
        if (end <= start && start < text.length) {
            end = (start + Character.charCount(text.codePointAt(start)))
                .coerceAtMost(text.length)
        }

        return CharacterPage(
            startOffset = start,
            endOffset = end,
            text = text.substring(start, end),
        )
    }
}
