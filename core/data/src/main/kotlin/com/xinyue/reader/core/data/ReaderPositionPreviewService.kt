package com.xinyue.reader.core.data

import com.xinyue.reader.core.text.DetectedChapter
import javax.inject.Inject
import kotlin.math.roundToLong

data class ReaderPositionPreview(
    val targetOffset: Int,
    val chapterTitle: String,
    val percent: Int,
    val snippet: String,
)

class ReaderPositionPreviewService @Inject constructor(
    private val textSource: TextSource,
) {
    suspend fun preview(
        normalizedPath: String,
        totalUtf16Length: Long,
        chapters: List<DetectedChapter>,
        fraction: Float,
    ): ReaderPositionPreview {
        val safeLength = totalUtf16Length.coerceIn(0, Int.MAX_VALUE.toLong())
        val safeFraction = fraction.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        val target = (safeLength * safeFraction.toDouble()).roundToLong()
            .coerceIn(0, safeLength)
            .toInt()
        val window = textSource.readWindow(
            normalizedPath = normalizedPath,
            anchorOffset = target.toLong(),
            beforeUtf16Units = PREVIEW_BEFORE_UTF16_UNITS,
            afterUtf16Units = PREVIEW_AFTER_UTF16_UNITS,
        )
        val chapter = chapters.asSequence()
            .filter { it.startOffset <= target }
            .maxByOrNull(DetectedChapter::startOffset)
        val percent = if (safeLength == 0L) 0 else {
            (target.toDouble() / safeLength * 100).toInt().coerceIn(0, 100)
        }
        return ReaderPositionPreview(
            targetOffset = target,
            chapterTitle = chapter?.title ?: "正文",
            percent = percent,
            snippet = window.text
                .repairSurrogateEdges()
                .replace(WHITESPACE, " ")
                .trim(),
        )
    }

    private fun String.repairSurrogateEdges(): String {
        if (isEmpty()) return this
        val start = if (first().isLowSurrogate()) 1 else 0
        val end = if (last().isHighSurrogate()) lastIndex else length
        return if (start >= end) "" else substring(start, end)
    }

    private companion object {
        const val PREVIEW_BEFORE_UTF16_UNITS = 80
        const val PREVIEW_AFTER_UTF16_UNITS = 160
        val WHITESPACE = Regex("\\s+")
    }
}
