package com.xinyue.reader.feature.reader

import com.xinyue.reader.core.data.TextSource
import com.xinyue.reader.core.data.TextWindow
import com.xinyue.reader.core.text.DetectedChapter
import com.xinyue.reader.core.text.ReaderLayoutWhitespaceNormalizer
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Builds a complete, absolute page table without loading the book into memory at once.
 *
 * A window is allowed to end in the middle of a page. That last candidate is held until the
 * next window contains enough text to measure the page completely. The prefix is deliberately
 * retained when reading the next window because paragraph continuation and chapter-boundary
 * whitespace are part of the same pagination contract used by the visible reader.
 */
internal suspend fun buildReaderBookPageIndex(
    textSource: TextSource,
    paginator: BookPaginator,
    normalizedPath: String,
    totalUtf16Length: Long,
    spec: ReaderLayoutSpec,
    chapters: List<DetectedChapter>,
    chaptersManuallyEdited: Boolean,
    computationDispatcher: CoroutineDispatcher,
    chunkUtf16Units: Int = READER_BOOK_PAGE_INDEX_CHUNK_UTF16,
    contextUtf16Units: Int = READER_BOOK_PAGE_INDEX_CONTEXT_UTF16,
    onProgress: (Float) -> Unit = {},
): List<ReaderPage> {
    require(totalUtf16Length in 0..Int.MAX_VALUE.toLong()) {
        "正文长度超过当前全书页码索引支持范围"
    }
    require(chunkUtf16Units > 0) { "全书页码索引分块大小必须大于零" }
    require(chunkUtf16Units <= MAX_READER_BOOK_PAGE_INDEX_READ_UTF16) {
        "全书页码索引分块超过单次读取上限"
    }
    require(contextUtf16Units >= 0) { "全书页码索引上下文不能为负数" }

    val totalLength = totalUtf16Length.toInt()
    if (totalLength == 0) {
        onProgress(1f)
        return listOf(ReaderPage(0, 0))
    }

    val absoluteChapterStarts = chapters.map { it.startOffset }
        .filter { it in 0..totalLength }
        .distinct()
        .sorted()
    val pages = ArrayList<ReaderPage>()
    var cursor = 0
    var requestedAfter = chunkUtf16Units

    while (cursor < totalLength) {
        currentCoroutineContext().ensureActive()
        val previousCursor = cursor
        val before = min(contextUtf16Units, cursor)
        val window = textSource.readWindow(
            normalizedPath = normalizedPath,
            anchorOffset = cursor.toLong(),
            beforeUtf16Units = before,
            afterUtf16Units = requestedAfter,
        )
        validateWindow(window, totalUtf16Length, cursor)

        val prepared = withContext(computationDispatcher) {
            val localChapterStarts = absoluteChapterStarts.mapNotNull { absolute ->
                val local = absolute.toLong() - window.startOffset
                local.takeIf { it in 0..window.text.length.toLong() }?.toInt()
            }
            PreparedBookPageIndexWindow(
                text = ReaderLayoutWhitespaceNormalizer.normalize(
                    window.text,
                    hardBoundaries = localChapterStarts,
                ),
                chapterStarts = localChapterStarts,
                titleRanges = readerChapterTitleRanges(
                    rawText = window.text,
                    windowStartOffset = window.startOffset.toInt(),
                    chapters = chapters,
                    chaptersManuallyEdited = chaptersManuallyEdited,
                ),
            )
        }

        val candidates = withContext(computationDispatcher) {
            paginateReaderBookPageChunk(
                paginator = paginator,
                text = prepared.text,
                windowStartOffset = window.startOffset.toInt(),
                cursorOffset = cursor,
                spec = spec,
                chapterStarts = prepared.chapterStarts,
                titleRanges = prepared.titleRanges,
            )
        }
        require(candidates.isNotEmpty()) { "全书页码索引分页器没有返回页面" }

        val windowEnd = window.endOffset.toInt().coerceAtMost(totalLength)
        val last = candidates.last()
        val lastIsIncomplete = windowEnd < totalLength &&
            last.endOffset >= windowEnd
        val completeCandidates = if (lastIsIncomplete) candidates.dropLast(1) else candidates

        if (completeCandidates.isEmpty()) {
            if (windowEnd >= totalLength || requestedAfter >= MAX_READER_BOOK_PAGE_INDEX_READ_UTF16) {
                error("全书页码索引无法在单次读取上限内完成页面")
            }
            requestedAfter = (requestedAfter.toLong() * 2L)
                .coerceAtMost(MAX_READER_BOOK_PAGE_INDEX_READ_UTF16.toLong())
                .toInt()
            continue
        }

        val first = completeCandidates.first()
        require(first.startOffset == cursor) {
            "全书页码索引在 ${cursor} 处出现不连续页面"
        }
        completeCandidates.zipWithNext().forEach { (current, next) ->
            require(current.endOffset == next.startOffset) {
                "全书页码索引包含重叠或间隙"
            }
        }
        if (pages.isNotEmpty()) {
            require(pages.last().endOffset == first.startOffset) {
                "全书页码索引跨分块时出现重叠或间隙"
            }
        }
        require(pages.size <= MAX_READER_BOOK_PAGE_INDEX_PAGES - completeCandidates.size) {
            "全书页码数量超过当前版本支持上限"
        }
        pages += completeCandidates
        cursor = completeCandidates.last().endOffset
        require(cursor > previousCursor && cursor <= totalLength) { "全书页码索引没有向前推进" }
        requestedAfter = chunkUtf16Units
        onProgress((cursor.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f))
    }

    require(pages.isNotEmpty() && pages.last().endOffset == totalLength) {
        "全书页码索引没有覆盖正文末尾"
    }
    onProgress(1f)
    return pages
}

/**
 * Paginates the suffix of a bounded window while preserving chapter hard boundaries. The
 * returned offsets are absolute, even though the paginator receives window-local offsets.
 */
internal suspend fun paginateReaderBookPageChunk(
    paginator: BookPaginator,
    text: String,
    windowStartOffset: Int,
    cursorOffset: Int,
    spec: ReaderLayoutSpec,
    chapterStarts: List<Int>,
    titleRanges: List<ReaderChapterTitleRange>,
): List<ReaderPage> {
    if (text.isEmpty()) return emptyList()
    val localCursor = (cursorOffset - windowStartOffset).coerceIn(0, text.length)
    if (localCursor == text.length) return emptyList()
    val boundaries = buildList {
        add(localCursor)
        chapterPaginationBoundaries(text, chapterStarts).asSequence()
            .filter { it > localCursor && it < text.length }
            .sorted()
            .forEach(::add)
        add(text.length)
    }.distinct().sorted()
    val orderedTitles = titleRanges.mapNotNull { range ->
        val localStart = range.startOffset - windowStartOffset
        val localEnd = range.endOffset - windowStartOffset
        if (localStart < 0 || localEnd > text.length || localEnd <= localStart) {
            null
        } else {
            ReaderChapterTitleRange(localStart, localEnd)
        }
    }.sortedBy(ReaderChapterTitleRange::startOffset)
    return boundaries.zipWithNext().flatMap { (start, end) ->
        paginator.paginateWindowWithTitleRanges(
            text = text,
            spec = spec,
            startOffset = start,
            endOffset = end,
            titleRanges = orderedTitles.intersectingTitleRanges(
                textStartOffset = start.toLong(),
                textEndOffset = end.toLong(),
            ),
        ).map { page ->
            ReaderPage(
                startOffset = windowStartOffset + page.startOffset,
                endOffset = windowStartOffset + page.endOffset,
            )
        }
    }
}

private fun validateWindow(window: TextWindow, expectedTotal: Long, cursor: Int) {
    require(window.startOffset in 0..cursor.toLong()) { "全书页码索引读取窗口起点无效" }
    require(window.endOffset >= cursor.toLong()) { "全书页码索引读取窗口没有覆盖当前游标" }
    require(window.endOffset <= expectedTotal) { "全书页码索引读取窗口超出正文长度" }
    require(window.totalUtf16Length == expectedTotal) { "正文在生成全书页码时发生变化" }
    require(window.text.length.toLong() == window.endOffset - window.startOffset) {
        "全书页码索引读取窗口长度无效"
    }
}

private data class PreparedBookPageIndexWindow(
    val text: String,
    val chapterStarts: List<Int>,
    val titleRanges: List<ReaderChapterTitleRange>,
)

internal const val READER_BOOK_PAGE_INDEX_CHUNK_UTF16 = 100_000
internal const val READER_BOOK_PAGE_INDEX_CONTEXT_UTF16 = 50_000
internal const val MAX_READER_BOOK_PAGE_INDEX_READ_UTF16 = 1_000_000
internal const val MAX_READER_BOOK_PAGE_INDEX_PAGES = 1_000_000
