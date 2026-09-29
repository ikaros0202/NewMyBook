package com.xinyue.reader.feature.reader

import com.xinyue.reader.core.text.DetectedChapter

/** Absolute, end-exclusive UTF-16 range occupied by one chapter title line. */
data class ReaderChapterTitleRange(
    val startOffset: Int,
    val endOffset: Int,
) {
    init {
        require(startOffset >= 0) { "标题起点不能为负数" }
        require(endOffset > startOffset) { "标题范围必须包含至少一个 UTF-16 单元" }
    }
}

/**
 * Derives title ranges from the raw bounded window without changing its coordinate system.
 * A title starts at the accepted chapter offset and ends immediately before the next LF.
 */
internal fun readerChapterTitleRanges(
    rawText: String,
    windowStartOffset: Int,
    chapters: Collection<DetectedChapter>,
    chaptersManuallyEdited: Boolean,
): List<ReaderChapterTitleRange> {
    require(windowStartOffset >= 0) { "窗口起点不能为负数" }
    if (rawText.isEmpty()) return emptyList()

    val windowEndOffset = windowStartOffset.toLong() + rawText.length
    require(windowEndOffset <= Int.MAX_VALUE) { "窗口偏移超出当前 UTF-16 偏移范围" }
    val automaticSyntheticBody = !chaptersManuallyEdited && chapters.size == 1 &&
        chapters.single().title == "正文" && chapters.single().startOffset == 0

    return chapters.asSequence()
        .filterNot { automaticSyntheticBody && it.title == "正文" && it.startOffset == 0 }
        .mapNotNull { chapter ->
            val absoluteStart = chapter.startOffset
            val localStart = absoluteStart.toLong() - windowStartOffset
            if (localStart !in 0 until rawText.length.toLong()) return@mapNotNull null
            val lineEnd = rawText.indexOf('\n', localStart.toInt()).let { newline ->
                if (newline >= 0) newline else rawText.length
            }
            if (lineEnd <= localStart) return@mapNotNull null
            ReaderChapterTitleRange(absoluteStart, windowStartOffset + lineEnd)
        }
        .distinctBy(ReaderChapterTitleRange::startOffset)
        .sortedBy(ReaderChapterTitleRange::startOffset)
        .toList()
}

internal fun readerShouldShowRunningChapterTitle(
    showChapterTitle: Boolean,
    pageStartOffset: Int,
    titleRanges: Collection<ReaderChapterTitleRange>,
    pageText: String = "",
): Boolean {
    if (!showChapterTitle) return false
    val firstVisibleIndex = pageText.indexOfFirst { it != '\u2060' }
    val effectivePageStart = if (firstVisibleIndex >= 0) {
        pageStartOffset + firstVisibleIndex
    } else {
        pageStartOffset
    }
    return titleRanges.none { it.startOffset == effectivePageStart }
}

/** Returns the sorted title ranges intersecting one local/absolute text interval. */
internal fun List<ReaderChapterTitleRange>.intersectingTitleRanges(
    textStartOffset: Long,
    textEndOffset: Long,
): List<ReaderChapterTitleRange> {
    if (isEmpty() || textStartOffset >= textEndOffset) return emptyList()

    var first = 0
    var high = size
    while (first < high) {
        val middle = (first + high) ushr 1
        if (this[middle].endOffset.toLong() <= textStartOffset) {
            first = middle + 1
        } else {
            high = middle
        }
    }

    var last = first
    high = size
    while (last < high) {
        val middle = (last + high) ushr 1
        if (this[middle].startOffset.toLong() < textEndOffset) {
            last = middle + 1
        } else {
            high = middle
        }
    }
    return if (first < last) subList(first, last) else emptyList()
}
