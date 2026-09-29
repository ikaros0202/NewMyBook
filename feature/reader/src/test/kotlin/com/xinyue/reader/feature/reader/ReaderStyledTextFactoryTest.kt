package com.xinyue.reader.feature.reader

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spannable
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.HighlightColor
import com.xinyue.reader.core.domain.model.ReaderAnnotation
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderTextAlignment
import com.xinyue.reader.core.domain.model.TextRangeAnchor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderStyledTextFactoryTest {
    private val factory = ReaderStyledTextFactory(ReaderTypefaceResolver.forTests())

    @Test
    fun `paragraph styling preserves every original utf16 offset`() {
        val original = "第一段。\n第二段。"
        val styled = factory.create(
            text = original,
            spec = ReaderLayoutSpec(
                widthPx = 320,
                heightPx = 480,
                fontSizePx = 30f,
                lineHeightPx = 48f,
                paragraphSpacingPx = 12f,
                firstLineIndentPx = 60f,
                alignment = ReaderTextAlignment.CENTER,
            ),
        )

        assertThat(styled.toString()).isEqualTo(original)
        assertThat(styled.length).isEqualTo(original.length)
        val margins = styled.getSpans(0, styled.length, LeadingMarginSpan::class.java)
        val alignments = styled.getSpans(0, styled.length, AlignmentSpan::class.java)
        val spacing = styled.getSpans(0, styled.length, ParagraphSpacingSpan::class.java)
        assertThat(margins).hasLength(2)
        assertThat(alignments).hasLength(2)
        assertThat(spacing).hasLength(2)
        assertThat(margins.map { styled.getSpanStart(it) to styled.getSpanEnd(it) })
            .containsExactly(0 to 5, 5 to original.length).inOrder()
        assertThat(alignments.map { styled.getSpanStart(it) to styled.getSpanEnd(it) })
            .containsExactly(0 to 5, 5 to original.length).inOrder()
        assertThat(spacing.map { it.paragraphEnd })
            .containsExactly(5, original.length).inOrder()
    }

    @Test
    fun `absolute annotation highlight coexists with paragraph spans without changing selection offsets`() {
        val pageStart = 100L
        val original = "甲乙第一段。\n第二段。"
        val annotation = ReaderAnnotation(
            id = "highlight-1",
            bookId = "book-1",
            kind = AnnotationKind.HIGHLIGHT,
            range = TextRangeAnchor(
                startOffset = pageStart + 2,
                endOffset = pageStart + 6,
                prefix = "",
                suffix = "",
                selectedSha256 = null,
            ),
            color = HighlightColor.YELLOW,
            note = null,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )

        val styled = factory.create(
            text = original,
            spec = ReaderLayoutSpec(320, 480, 30f, 48f, firstLineIndentPx = 60f),
            annotations = listOf(annotation),
            textStartOffset = pageStart,
        )

        val highlight = styled.getSpans(0, styled.length, BackgroundColorSpan::class.java).single()
        assertThat(styled.getSpanStart(highlight)).isEqualTo(2)
        assertThat(styled.getSpanEnd(highlight)).isEqualTo(6)
        assertThat(styled.getSpanFlags(highlight)).isEqualTo(Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        assertThat(styled.substring(2, 6)).isEqualTo(original.substring(2, 6))
        assertThat(styled.length).isEqualTo(original.length)
    }

    @Test
    fun `bounded page continuations do not invent indent or trailing paragraph space`() {
        val styled = factory.create(
            text = "中段\n完整段\n尾段",
            spec = ReaderLayoutSpec(
                widthPx = 320,
                heightPx = 480,
                fontSizePx = 30f,
                lineHeightPx = 48f,
                paragraphSpacingPx = 12f,
                firstLineIndentPx = 60f,
            ),
            firstParagraphIsContinuation = true,
            lastParagraphIsContinuation = true,
        )

        val margins = styled.getSpans(0, styled.length, LeadingMarginSpan.Standard::class.java)
        val spacing = styled.getSpans(0, styled.length, ParagraphSpacingSpan::class.java)
        assertThat(margins.map { it.getLeadingMargin(true) })
            .containsExactly(0, 60, 60).inOrder()
        assertThat(spacing.map { it.paragraphEnd }).containsExactly(3, 7).inOrder()
    }

    @Test
    fun `chapter titles receive exact metric spans and replace body paragraph spacing`() {
        val original = "Title\nbody\n"
        val styled = factory.create(
            text = original,
            spec = ReaderLayoutSpec(
                widthPx = 320,
                heightPx = 480,
                fontSizePx = 30f,
                lineHeightPx = 48f,
                paragraphSpacingPx = 12f,
                firstLineIndentPx = 60f,
            ),
            textStartOffset = 100L,
            titleRanges = listOf(ReaderChapterTitleRange(100, 105)),
        )

        assertThat(styled.toString()).isEqualTo(original)
        assertThat(styled.length).isEqualTo(original.length)

        val relativeSize = styled.getSpans(0, styled.length, RelativeSizeSpan::class.java).single()
        assertThat(relativeSize.sizeChange).isEqualTo(1.45f)
        assertThat(styled.getSpanStart(relativeSize)).isEqualTo(0)
        assertThat(styled.getSpanEnd(relativeSize)).isEqualTo(5)

        val bold = styled.getSpans(0, styled.length, StyleSpan::class.java).single()
        assertThat(bold.style).isEqualTo(Typeface.BOLD)
        assertThat(styled.getSpanStart(bold)).isEqualTo(0)
        assertThat(styled.getSpanEnd(bold)).isEqualTo(5)

        val margins = styled.getSpans(0, styled.length, LeadingMarginSpan.Standard::class.java)
        assertThat(margins.map { styled.getSpanStart(it) to styled.getSpanEnd(it) })
            .containsExactly(0 to 6, 6 to original.length).inOrder()
        assertThat(margins.first().getLeadingMargin(true)).isEqualTo(0)
        assertThat(margins[1].getLeadingMargin(true)).isEqualTo(60)

        val spacing = styled.getSpans(0, styled.length, ParagraphSpacingSpan::class.java)
        assertThat(spacing).hasLength(2)
        assertThat(spacing.map { it.paragraphEnd }).containsExactly(6, original.length).inOrder()
        val titleMetrics = Paint.FontMetricsInt()
        spacing.first().chooseHeight(styled, 0, 6, 0, 0, titleMetrics)
        assertThat(titleMetrics.descent).isEqualTo(23)
        val bodyMetrics = Paint.FontMetricsInt()
        spacing[1].chooseHeight(styled, 6, original.length, 0, 0, bodyMetrics)
        assertThat(bodyMetrics.descent).isEqualTo(12)
    }

    @Test
    fun `dense one line title ranges keep styling work bounded and preserve every offset`() {
        val titleCount = 10_000
        val text = buildString(titleCount * 3) {
            repeat(titleCount) { append("章\n") }
        }
        val titleRanges = buildList(titleCount) {
            repeat(titleCount) { index ->
                add(ReaderChapterTitleRange(index * 2, index * 2 + 1))
            }
        }

        val styled = factory.create(
            text = text,
            spec = ReaderLayoutSpec(320, 480, 30f, 48f),
            titleRanges = titleRanges,
        )

        assertThat(styled.toString()).isEqualTo(text)
        assertThat(styled.length).isEqualTo(text.length)
        assertThat(styled.getSpans(0, styled.length, RelativeSizeSpan::class.java)).hasLength(titleCount)
        assertThat(styled.getSpans(0, styled.length, StyleSpan::class.java)).hasLength(titleCount)
        assertThat(styled.getSpans(0, styled.length, RelativeSizeSpan::class.java).first().let(styled::getSpanStart))
            .isEqualTo(0)
        assertThat(styled.getSpans(0, styled.length, RelativeSizeSpan::class.java).last().let(styled::getSpanEnd))
            .isEqualTo(text.length - 1)
    }

    @Test
    fun `typeface resolver caches missing imported font fallback by id and weight`() {
        var lookups = 0
        val resolver = ReaderTypefaceResolver.forTests(
            openImportedFont = {
                lookups += 1
                null
            },
        )
        val font = ReaderFontRef.Imported("missing-font")

        val first = resolver.resolve(font, 700)
        val second = resolver.resolve(font, 700)

        assertThat(second).isSameInstanceAs(first)
        assertThat(first).isSameInstanceAs(Typeface.DEFAULT)
        assertThat(lookups).isEqualTo(1)
    }
}
