package com.xinyue.reader.feature.reader

import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.HighlightColor
import com.xinyue.reader.core.domain.model.ReaderAnnotation
import com.xinyue.reader.core.domain.model.ReaderTextAlignment
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class ReaderStyledTextFactory @Inject constructor(
    private val typefaceResolver: ReaderTypefaceResolver,
) {
    internal fun create(
        text: String,
        spec: ReaderLayoutSpec,
        annotations: List<ReaderAnnotation> = emptyList(),
        textStartOffset: Long = 0L,
        firstParagraphIsContinuation: Boolean = false,
        lastParagraphIsContinuation: Boolean = false,
        titleRanges: List<ReaderChapterTitleRange> = emptyList(),
    ): SpannableString {
        val styled = SpannableString(text)
        if (text.isNotEmpty()) {
            val visibleTitleRanges = titleRanges.intersectingTitleRanges(
                textStartOffset = textStartOffset,
                textEndOffset = textStartOffset + text.length,
            )
            applyParagraphStyles(
                styled = styled,
                spec = spec,
                textStartOffset = textStartOffset,
                titleRanges = visibleTitleRanges,
                firstParagraphIsContinuation = firstParagraphIsContinuation,
                lastParagraphIsContinuation = lastParagraphIsContinuation,
            )
            applyTitleCharacterStyles(styled, textStartOffset, visibleTitleRanges)
            applyAnnotations(styled, annotations, textStartOffset)
        }
        return styled
    }

    fun configurePaint(paint: TextPaint, spec: ReaderLayoutSpec) {
        paint.textSize = spec.fontSizePx
        paint.letterSpacing = spec.letterSpacingEm
        paint.typeface = resolveTypeface(spec)
    }

    fun resolveTypeface(spec: ReaderLayoutSpec): Typeface =
        typefaceResolver.resolve(spec.font, spec.fontWeight)

    private fun applyParagraphStyles(
        styled: SpannableString,
        spec: ReaderLayoutSpec,
        textStartOffset: Long,
        titleRanges: List<ReaderChapterTitleRange>,
        firstParagraphIsContinuation: Boolean,
        lastParagraphIsContinuation: Boolean,
    ) {
        var paragraphStart = 0
        var paragraphIndex = 0
        var titleCursor = 0
        while (paragraphStart < styled.length) {
            val newline = styled.indexOf('\n', paragraphStart)
            val paragraphEnd = if (newline >= 0) newline + 1 else styled.length
            val isFirstContinuation = paragraphIndex == 0 && firstParagraphIsContinuation
            val paragraphAbsoluteStart = textStartOffset + paragraphStart
            val paragraphAbsoluteEnd = textStartOffset + paragraphEnd
            while (titleCursor < titleRanges.size &&
                titleRanges[titleCursor].endOffset.toLong() <= paragraphAbsoluteStart
            ) {
                titleCursor += 1
            }
            val isTitleParagraph = titleCursor < titleRanges.size &&
                titleRanges[titleCursor].startOffset.toLong() < paragraphAbsoluteEnd
            styled.setSpan(
                LeadingMarginSpan.Standard(
                    if (isTitleParagraph || isFirstContinuation) {
                        0
                    } else {
                        spec.firstLineIndentPx.roundToInt()
                    },
                    0,
                ),
                paragraphStart,
                paragraphEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            styled.setSpan(
                AlignmentSpan.Standard(spec.alignment.toLayoutAlignment()),
                paragraphStart,
                paragraphEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            val isLastParagraph = paragraphEnd == styled.length
            val paragraphIsComplete = newline >= 0 || !isLastParagraph || !lastParagraphIsContinuation
            val paragraphSpacingPx = if (isTitleParagraph) {
                spec.fontSizePx * 0.75f
            } else {
                spec.paragraphSpacingPx
            }
            if (paragraphSpacingPx > 0f && paragraphIsComplete) {
                styled.setSpan(
                    ParagraphSpacingSpan(paragraphEnd, paragraphSpacingPx),
                    paragraphStart,
                    paragraphEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            paragraphStart = paragraphEnd
            paragraphIndex += 1
        }
    }

    private fun applyTitleCharacterStyles(
        styled: SpannableString,
        textStartOffset: Long,
        titleRanges: List<ReaderChapterTitleRange>,
    ) {
        val textEndOffset = textStartOffset + styled.length
        titleRanges.forEach { range ->
            val absoluteStart = range.startOffset.toLong().coerceAtLeast(textStartOffset)
            val absoluteEnd = range.endOffset.toLong().coerceAtMost(textEndOffset)
            if (absoluteStart >= absoluteEnd) return@forEach
            val relativeStart = (absoluteStart - textStartOffset).toInt()
            val relativeEnd = (absoluteEnd - textStartOffset).toInt()
            styled.setSpan(
                RelativeSizeSpan(1.45f),
                relativeStart,
                relativeEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            styled.setSpan(
                StyleSpan(Typeface.BOLD),
                relativeStart,
                relativeEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun applyAnnotations(
        styled: SpannableString,
        annotations: List<ReaderAnnotation>,
        textStartOffset: Long,
    ) {
        val textEndOffset = textStartOffset + styled.length
        annotations.forEach { annotation ->
            if (annotation.kind != AnnotationKind.HIGHLIGHT && annotation.kind != AnnotationKind.NOTE) {
                return@forEach
            }
            val absoluteStart = annotation.range.startOffset.coerceAtLeast(textStartOffset)
            val absoluteEnd = annotation.range.endOffset.coerceAtMost(textEndOffset)
            if (absoluteStart >= absoluteEnd) return@forEach
            styled.setSpan(
                BackgroundColorSpan(annotation.color.toHighlightArgb()),
                (absoluteStart - textStartOffset).toInt(),
                (absoluteEnd - textStartOffset).toInt(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
}

internal fun ReaderTextAlignment.toLayoutAlignment(): Layout.Alignment = when (this) {
    ReaderTextAlignment.START,
    ReaderTextAlignment.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
    ReaderTextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
}

internal fun HighlightColor?.toHighlightArgb(): Int = when (this) {
    HighlightColor.GREEN -> 0x8066BB6A.toInt()
    HighlightColor.BLUE -> 0x8064B5F6.toInt()
    HighlightColor.PINK -> 0x80F48FB1.toInt()
    HighlightColor.YELLOW, null -> 0x80FFD54F.toInt()
}
