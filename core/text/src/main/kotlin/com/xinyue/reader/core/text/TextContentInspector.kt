package com.xinyue.reader.core.text

/** Collects lightweight readability signals while normalized text is streamed. */
class TextContentInspector {
    private var totalUnits = 0L
    private var meaningfulUnits = 0L
    private var suspiciousControlUnits = 0L
    private var replacementUnits = 0L
    private var nulUnits = 0L

    fun accept(character: Char) {
        totalUnits += 1
        if (!character.isWhitespace()) meaningfulUnits += 1
        if (character == '\u0000') nulUnits += 1
        if (character == '\uFFFD') replacementUnits += 1
        if (character.code < 0x20 && character != '\n' && character != '\t') {
            suspiciousControlUnits += 1
        }
    }

    fun accept(characters: CharArray, offset: Int, count: Int) {
        require(offset >= 0 && count >= 0 && offset + count <= characters.size)
        for (index in offset until offset + count) accept(characters[index])
    }

    fun requireReadableText() {
        require(meaningfulUnits > 0) { "TXT 文件没有有效正文" }
        val suspiciousLimit = maxOf(3L, totalUnits / 100L)
        val replacementLimit = maxOf(3L, totalUnits / 100L)
        require(
            nulUnits == 0L &&
                suspiciousControlUnits <= suspiciousLimit &&
                replacementUnits <= replacementLimit,
        ) { "文件内容不像有效 TXT，请确认没有把二进制文件改名为 .txt" }
    }
}
