package com.xinyue.reader.core.data

data class TextWindow(
    val startOffset: Long,
    val text: String,
    val totalUtf16Length: Long,
) {
    val endOffset: Long get() = startOffset + text.length
}

interface TextSource {
    suspend fun readWindow(
        normalizedPath: String,
        anchorOffset: Long,
        beforeUtf16Units: Int,
        afterUtf16Units: Int,
    ): TextWindow
}
