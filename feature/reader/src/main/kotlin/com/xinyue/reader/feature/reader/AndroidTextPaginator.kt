package com.xinyue.reader.feature.reader

import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderTextAlignment

data class ReaderLayoutSpec(
    val widthPx: Int,
    val heightPx: Int,
    val fontSizePx: Float,
    val lineHeightPx: Float,
    val font: ReaderFontRef = ReaderFontRef.System,
    val fontWeight: Int = 400,
    val letterSpacingEm: Float = 0f,
    val paragraphSpacingPx: Float = 0f,
    val firstLineIndentPx: Float = 0f,
    val alignment: ReaderTextAlignment = ReaderTextAlignment.START,
) {
    init {
        require(widthPx > 0) { "正文宽度必须大于零" }
        require(heightPx > 0) { "正文高度必须大于零" }
        require(fontSizePx > 0f) { "字体大小必须大于零" }
        require(lineHeightPx.isFinite() && lineHeightPx > 0f) { "行高必须是大于零的有限值" }
        require(fontWeight in 100..900) { "字重必须在 100 到 900 之间" }
        require(letterSpacingEm.isFinite()) { "字距必须是有限值" }
        require(paragraphSpacingPx.isFinite() && paragraphSpacingPx >= 0f) { "段距不能为负数" }
        require(firstLineIndentPx.isFinite() && firstLineIndentPx >= 0f) { "首行缩进不能为负数" }
    }
}

fun interface BookPaginator {
    suspend fun paginate(text: String, spec: ReaderLayoutSpec): List<ReaderPage>

    suspend fun paginateWindow(
        text: String,
        spec: ReaderLayoutSpec,
        startOffset: Int,
        endOffset: Int,
    ): List<ReaderPage> {
        val safeStart = startOffset.coerceIn(0, text.length)
        val safeEnd = endOffset.coerceIn(safeStart, text.length)
        return paginate(text.substring(safeStart, safeEnd), spec).map { page ->
            ReaderPage(page.startOffset + safeStart, page.endOffset + safeStart)
        }
    }
}

/**
 * Internal structural adapter. Legacy/fake [BookPaginator] implementations keep the public
 * four-argument contract; only the real Android paginator receives title ranges for measurement.
 */
internal suspend fun BookPaginator.paginateWindowWithTitleRanges(
    text: String,
    spec: ReaderLayoutSpec,
    startOffset: Int,
    endOffset: Int,
    titleRanges: List<ReaderChapterTitleRange>,
): List<ReaderPage> = if (this is AndroidTextPaginator) {
    paginateWindowWithTitleRanges(text, spec, startOffset, endOffset, titleRanges)
} else {
    paginateWindow(text, spec, startOffset, endOffset)
}

class AndroidTextPaginator @Inject constructor(
    private val styledTextFactory: ReaderStyledTextFactory,
) : BookPaginator {
    internal constructor() : this(ReaderStyledTextFactory(ReaderTypefaceResolver.forTests()))

    override suspend fun paginate(text: String, spec: ReaderLayoutSpec): List<ReaderPage> =
        paginateRange(text, spec, 0, text.length)

    override suspend fun paginateWindow(
        text: String,
        spec: ReaderLayoutSpec,
        startOffset: Int,
        endOffset: Int,
    ): List<ReaderPage> = paginateRange(text, spec, startOffset, endOffset, emptyList())

    internal suspend fun paginateWindowWithTitleRanges(
        text: String,
        spec: ReaderLayoutSpec,
        startOffset: Int,
        endOffset: Int,
        titleRanges: List<ReaderChapterTitleRange>,
    ): List<ReaderPage> = paginateRange(text, spec, startOffset, endOffset, titleRanges)

    private suspend fun paginateRange(
        text: String,
        spec: ReaderLayoutSpec,
        requestedStart: Int,
        requestedEnd: Int,
        titleRanges: List<ReaderChapterTitleRange> = emptyList(),
    ): List<ReaderPage> =
        withContext(Dispatchers.Default) {
            var rangeStart = requestedStart.coerceIn(0, text.length)
            if (
                rangeStart > 0 && rangeStart < text.length &&
                text[rangeStart].isLowSurrogate() && text[rangeStart - 1].isHighSurrogate()
            ) {
                rangeStart -= 1
            }
            var rangeEnd = requestedEnd.coerceIn(rangeStart, text.length)
            rangeEnd = avoidSplittingSurrogate(text, rangeStart, rangeEnd)
            if (rangeStart == rangeEnd) return@withContext listOf(ReaderPage(rangeStart, rangeEnd))

            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                styledTextFactory.configurePaint(this, spec)
            }
            val lineSpacingAdd = styledTextFactory.lineSpacingAdd(paint, spec)
            val pages = ArrayList<ReaderPage>(((rangeEnd - rangeStart) / 600).coerceAtLeast(1))
            val estimatedCharactersPerPage = (
                (spec.widthPx / spec.fontSizePx) * (spec.heightPx / spec.lineHeightPx)
            ).toInt().coerceAtLeast(64)
            val measureWindow = (estimatedCharactersPerPage * WINDOW_MULTIPLIER)
                .coerceIn(MIN_MEASURE_WINDOW, MAX_MEASURE_WINDOW)
            var start = rangeStart
            while (start < rangeEnd) {
                currentCoroutineContext().ensureActive()
                var chunkEnd = (start + measureWindow).coerceAtMost(rangeEnd)
                chunkEnd = avoidSplittingSurrogate(text, start, chunkEnd)
                val layout = buildLayout(
                    text = text,
                    start = start,
                    end = chunkEnd,
                    spec = spec,
                    paint = paint,
                    lineSpacingAdd = lineSpacingAdd,
                    titleRanges = titleRanges,
                )

                var lineIndex = layout.getLineForVertical((spec.heightPx - 1).coerceAtLeast(0))
                    .coerceIn(0, layout.lineCount - 1)
                while (lineIndex > 0 && layout.getLineBottom(lineIndex) > spec.heightPx) {
                    lineIndex -= 1
                }
                var end = (start + layout.getLineEnd(lineIndex)).coerceIn(start, chunkEnd)
                end = avoidSplittingSurrogate(text, start, end)
                if (end <= start) {
                    end = start + Character.charCount(text.codePointAt(start))
                }
                end = fitRenderedPageEnd(
                    text = text,
                    spec = spec,
                    paint = paint,
                    lineSpacingAdd = lineSpacingAdd,
                    start = start,
                    candidateEnd = end,
                    titleRanges = titleRanges,
                )

                pages += ReaderPage(
                    startOffset = start,
                    endOffset = end,
                )
                start = end
            }
            pages
        }

    private fun buildLayout(
        text: String,
        start: Int,
        end: Int,
        spec: ReaderLayoutSpec,
        paint: TextPaint,
        lineSpacingAdd: Float,
        titleRanges: List<ReaderChapterTitleRange>,
    ): StaticLayout {
        val styledText = styledTextFactory.create(
            text = text.substring(start, end),
            spec = spec,
            textStartOffset = start.toLong(),
            titleRanges = titleRanges.intersectingTitleRanges(
                textStartOffset = start.toLong(),
                textEndOffset = end.toLong(),
            ),
            firstParagraphIsContinuation = start > 0 && text[start - 1] != '\n',
            lastParagraphIsContinuation = end < text.length && text[end - 1] != '\n',
        )
        return ReaderTextLayoutPolicy.configure(
            StaticLayout.Builder.obtain(styledText, 0, styledText.length, paint, spec.widthPx),
        )
            .setAlignment(spec.alignment.toLayoutAlignment())
            .setIncludePad(false)
            .setLineSpacing(lineSpacingAdd, 1f)
            .setJustificationMode(
                if (spec.alignment == ReaderTextAlignment.JUSTIFY) {
                    Layout.JUSTIFICATION_MODE_INTER_WORD
                } else {
                    Layout.JUSTIFICATION_MODE_NONE
                },
            )
            .build()
    }

    /**
     * The chunk layout also contains following text, while the page is rendered as an
     * independent TextView fragment. Re-measure that exact fragment and remove only its
     * overflowing final line until it fits the same pixel viewport.
     */
    private fun fitRenderedPageEnd(
        text: String,
        spec: ReaderLayoutSpec,
        paint: TextPaint,
        lineSpacingAdd: Float,
        start: Int,
        candidateEnd: Int,
        titleRanges: List<ReaderChapterTitleRange>,
    ): Int {
        var end = candidateEnd.coerceIn(start + 1, text.length)
        while (true) {
            val pageLayout = buildLayout(
                text = text,
                start = start,
                end = end,
                spec = spec,
                paint = paint,
                lineSpacingAdd = lineSpacingAdd,
                titleRanges = titleRanges,
            )
            val lastLine = pageLayout.lineCount - 1
            if (lastLine < 0 || pageLayout.getLineBottom(lastLine) <= spec.heightPx) {
                return end
            }

            val lineStartOffset = start + pageLayout.getLineStart(lastLine)
            val previousCodePoint = previousCodePointStart(text, end)
            val nextEnd = if (lineStartOffset in (start + 1) until end) {
                lineStartOffset
            } else {
                previousCodePoint
            }
            if (nextEnd <= start) {
                return start + Character.charCount(text.codePointAt(start))
            }
            end = avoidSplittingSurrogate(text, start, nextEnd)
        }
    }

    private fun previousCodePointStart(text: String, endExclusive: Int): Int {
        val safeEnd = endExclusive.coerceIn(1, text.length)
        return if (
            safeEnd >= 2 &&
            text[safeEnd - 2].isHighSurrogate() &&
            text[safeEnd - 1].isLowSurrogate()
        ) {
            safeEnd - 2
        } else {
            safeEnd - 1
        }
    }

    private fun avoidSplittingSurrogate(text: String, start: Int, proposedEnd: Int): Int {
        var end = proposedEnd
        if (
            end > start &&
            end < text.length &&
            text[end - 1].isHighSurrogate() &&
            text[end].isLowSurrogate()
        ) {
            end -= 1
        }
        return end
    }

    private companion object {
        const val WINDOW_MULTIPLIER = 3
        const val MIN_MEASURE_WINDOW = 2_048
        const val MAX_MEASURE_WINDOW = 32_768
    }
}
