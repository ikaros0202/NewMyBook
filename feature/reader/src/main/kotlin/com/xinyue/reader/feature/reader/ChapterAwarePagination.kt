package com.xinyue.reader.feature.reader

internal fun chapterPaginationBoundaries(
    text: String,
    chapterStarts: List<Int>,
): List<Int> {
    val textLength = text.length
    return buildList {
        add(0)
        chapterStarts.asSequence()
            .filter { it in 1 until textLength }
            .map { start ->
                var effectiveStart = start
                while (effectiveStart > 0 && text[effectiveStart - 1] == '\u2060') {
                    effectiveStart -= 1
                }
                effectiveStart
            }
            .distinct()
            .sorted()
            .forEach(::add)
        if (textLength > 0) add(textLength)
    }.distinct().sorted()
}

internal suspend fun BookPaginator.paginateAtChapterStarts(
    text: String,
    spec: ReaderLayoutSpec,
    chapterStarts: List<Int>,
    titleRanges: List<ReaderChapterTitleRange> = emptyList(),
): List<ReaderPage> {
    if (text.isEmpty()) return listOf(ReaderPage(0, 0))
    val orderedTitleRanges = titleRanges.sortedBy(ReaderChapterTitleRange::startOffset)
    return chapterPaginationBoundaries(text, chapterStarts)
        .zipWithNext()
        .flatMap { (start, end) ->
            paginateWindowWithTitleRanges(
                text,
                spec,
                startOffset = start,
                endOffset = end,
                titleRanges = orderedTitleRanges.intersectingTitleRanges(
                    textStartOffset = start.toLong(),
                    textEndOffset = end.toLong(),
                ),
            )
        }
}
