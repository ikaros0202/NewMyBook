package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderTextAlignment
import com.xinyue.reader.core.text.ReaderLayoutWhitespaceNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidTextPaginatorTest {
    @Test
    fun `complete typography changes pagination without breaking utf16 continuity`() = runTest {
        val text = buildString {
            repeat(80) { index -> append("第${index}段，星河😀缓缓流过窗前，风吹过旧城。\n") }
        }
        val paginator = AndroidTextPaginator()
        val baseline = paginator.paginate(
            text,
            ReaderLayoutSpec(320, 260, 30f, 44f),
        )
        val personalized = paginator.paginate(
            text,
            ReaderLayoutSpec(
                widthPx = 320,
                heightPx = 260,
                fontSizePx = 30f,
                lineHeightPx = 44f,
                font = ReaderFontRef.Serif,
                fontWeight = 700,
                letterSpacingEm = 0.16f,
                paragraphSpacingPx = 18f,
                firstLineIndentPx = 60f,
                alignment = ReaderTextAlignment.JUSTIFY,
            ),
        )

        assertThat(personalized).isNotEqualTo(baseline)
        assertContinuousUtf16Coverage(text, personalized)
    }

    @Test
    fun `uses pixel constraints without losing or duplicating text`() = runTest {
        val text = buildString {
            repeat(40) { index -> append("第${index}行，星河缓缓流过窗前。\n") }
        }
        val paginator = AndroidTextPaginator()

        val pages = paginator.paginate(
            text = text,
            spec = ReaderLayoutSpec(
                widthPx = 320,
                heightPx = 260,
                fontSizePx = 32f,
                lineHeightPx = 52f,
            ),
        )

        assertThat(pages.size).isGreaterThan(1)
        assertThat(pages.joinToString(separator = "") { text.substring(it.startOffset, it.endOffset) })
            .isEqualTo(text)
        assertThat(pages.zipWithNext().all { (first, second) -> first.endOffset == second.startOffset })
            .isTrue()
    }

    @Test
    fun `never splits a unicode surrogate pair at a page boundary`() = runTest {
        val text = "甲乙丙丁😀戊己庚辛".repeat(20)
        val pages = AndroidTextPaginator().paginate(
            text,
            ReaderLayoutSpec(widthPx = 100, heightPx = 80, fontSizePx = 30f, lineHeightPx = 40f),
        )

        assertThat(pages.joinToString(separator = "") { text.substring(it.startOffset, it.endOffset) })
            .isEqualTo(text)
        assertThat(pages.dropLast(1).none { text[it.endOffset - 1].isHighSurrogate() }).isTrue()
    }

    @Test
    fun `chapter boundary blank runs do not create blank only pages`() = runTest {
        val text = buildString {
            append("第一章\n")
            repeat(20) { append("上一章正文内容。\n") }
            repeat(120) { append('\n') }
            append("第二章\n")
            repeat(20) { append("下一章正文内容。\n") }
        }

        val layoutText = ReaderLayoutWhitespaceNormalizer.normalize(text)
        val pages = AndroidTextPaginator().paginate(
            layoutText,
            ReaderLayoutSpec(widthPx = 240, heightPx = 180, fontSizePx = 28f, lineHeightPx = 42f),
        )

        assertContinuousUtf16Coverage(text, pages)
        assertThat(
            pages.none { page ->
                text.substring(page.startOffset, page.endOffset).isBlank()
            },
        ).isTrue()
    }

    @Test
    fun `chapter starts begin new pages without losing utf16 coverage`() = runTest {
        val text = buildString {
            append("第一章\n")
            repeat(7) { append("上一章正文内容仍在继续。\n") }
            append("\n\n第二章\n")
            repeat(7) { append("下一章正文内容从标题后开始。\n") }
        }
        val secondChapterStart = text.indexOf("第二章")

        val pages = AndroidTextPaginator().paginateAtChapterStarts(
            text = text,
            spec = ReaderLayoutSpec(widthPx = 240, heightPx = 180, fontSizePx = 28f, lineHeightPx = 42f),
            chapterStarts = listOf(0, secondChapterStart),
        )

        assertContinuousUtf16Coverage(text, pages)
        assertThat(pages.any { it.startOffset == secondChapterStart }).isTrue()
        assertThat(pages.none { it.startOffset < secondChapterStart && it.endOffset > secondChapterStart })
            .isTrue()
    }

    @Test
    fun `invisible boundary segments join the first visible chapter page`() = runTest {
        val raw = "\n\n第一章\n正文\n正文\n\n第二章\n后续"
        val firstChapterStart = raw.indexOf("第一章")
        val secondChapterStart = raw.indexOf("第二章")
        val layout = ReaderLayoutWhitespaceNormalizer.normalize(
            raw,
            hardBoundaries = listOf(firstChapterStart, secondChapterStart),
        )
        val titleRanges = readerChapterTitleRanges(
            rawText = raw,
            windowStartOffset = 0,
            chapters = listOf(
                com.xinyue.reader.core.text.DetectedChapter("第一章", firstChapterStart),
                com.xinyue.reader.core.text.DetectedChapter("第二章", secondChapterStart),
            ),
            chaptersManuallyEdited = true,
        )

        val pages = AndroidTextPaginator().paginateAtChapterStarts(
            text = layout,
            spec = ReaderLayoutSpec(240, 180, 28f, 42f),
            chapterStarts = listOf(firstChapterStart, secondChapterStart),
            titleRanges = titleRanges,
        )

        assertContinuousUtf16Coverage(raw, pages)
        assertThat(pages.all { it.endOffset > it.startOffset }).isTrue()
        assertThat(pages.none { page ->
            layout.substring(page.startOffset, page.endOffset).isNotEmpty() &&
                layout.substring(page.startOffset, page.endOffset).all { it == '\u2060' }
        }).isTrue()
        assertThat(pages.any { page ->
            page.startOffset <= firstChapterStart && page.endOffset > firstChapterStart
        }).isTrue()
        assertThat(pages.any { page ->
            page.startOffset <= secondChapterStart && page.endOffset > secondChapterStart
        }).isTrue()
        assertThat(pages.none { page ->
            page.startOffset < secondChapterStart &&
                page.endOffset > secondChapterStart &&
                layout.substring(page.startOffset, secondChapterStart)
                    .any { it != '\u2060' }
        }).isTrue()
    }

    @Test
    fun `title metrics can change measured cuts while preserving exact continuity`() = runTest {
        val title = "\u7ae0\u8282\u6807\u9898\uff0c\u661f\u6cb3\uff0c\u98ce\u4ece\u65e7\u57ce\u5439\u6765\u3002".repeat(20)
        val text = title + "\n" + "body line\n".repeat(80)
        val spec = ReaderLayoutSpec(
            widthPx = 180,
            heightPx = 140,
            fontSizePx = 24f,
            lineHeightPx = 30f,
            paragraphSpacingPx = 2f,
        )
        val paginator = AndroidTextPaginator()
        val bodyOnly = paginator.paginateAtChapterStarts(
            text = text,
            spec = spec,
            chapterStarts = listOf(0),
        )
        val styled = paginator.paginateAtChapterStarts(
            text = text,
            spec = spec,
            chapterStarts = listOf(0),
            titleRanges = listOf(ReaderChapterTitleRange(0, title.length)),
        )

        assertThat(styled).isNotEqualTo(bodyOnly)
        assertContinuousUtf16Coverage(text, styled)
    }

    @Test
    fun `title ranged pagination traverses multiple measurement chunks with exact continuity`() = runTest {
        val builder = StringBuilder()
        val titleRanges = buildList {
            repeat(400) { index ->
                val titleStart = builder.length
                builder.append("Title ").append(index).append('\n')
                add(ReaderChapterTitleRange(titleStart, builder.length - 1))
                repeat(18) {
                    builder.append("body line with stable measured width and chapter content\n")
                }
            }
        }
        val text = builder.toString()
        val pages = AndroidTextPaginator().paginateWindowWithTitleRanges(
            text = text,
            spec = ReaderLayoutSpec(320, 260, 24f, 36f),
            startOffset = 0,
            endOffset = text.length,
            titleRanges = titleRanges,
        )

        assertThat(pages).isNotEmpty()
        assertThat(pages.all { it.endOffset > it.startOffset }).isTrue()
        assertThat(pages.first().startOffset).isEqualTo(0)
        assertThat(pages.last().endOffset).isEqualTo(text.length)
        assertThat(pages.zipWithNext().all { (first, second) ->
            first.endOffset == second.startOffset
        }).isTrue()
        assertThat(pages.joinToString(separator = "") { text.substring(it.startOffset, it.endOffset) })
            .isEqualTo(text)
    }

    @Test
    fun `compact line height below font size can be paginated`() = runTest {
        val text = "第一章\n紧凑排版仍然保持连续。\n".repeat(30)

        val pages = AndroidTextPaginator().paginate(
            text,
            ReaderLayoutSpec(widthPx = 240, heightPx = 180, fontSizePx = 30f, lineHeightPx = 24f),
        )

        assertContinuousUtf16Coverage(text, pages)
    }

    @Test
    fun `large novel pagination remains linear enough for background repagination`() = runTest {
        val paragraph = "第一章 星河\n夜色落在窗前，风从旧城的长街缓缓吹来。\n"
        val text = paragraph.repeat(10_000)
        lateinit var pages: List<ReaderPage>

        val elapsedMillis = measureTimeMillis {
            pages = AndroidTextPaginator().paginate(
                text,
                ReaderLayoutSpec(900, 1_800, 42f, 66f),
            )
        }

        assertThat(pages.first().startOffset).isEqualTo(0)
        assertThat(pages.last().endOffset).isEqualTo(text.length)
        assertThat(pages.zipWithNext().all { (first, second) -> first.endOffset == second.startOffset })
            .isTrue()
        assertThat(elapsedMillis).isLessThan(15_000L)
    }

    @Test
    fun `window pagination keeps absolute offsets and unicode boundaries`() = runTest {
        val text = "开头😀中段文字".repeat(100)
        val emojiStart = text.indexOf("😀")
        val pages = AndroidTextPaginator().paginateWindow(
            text,
            ReaderLayoutSpec(180, 160, 30f, 44f),
            startOffset = emojiStart + 1,
            endOffset = text.length - 1,
        )

        assertThat(pages.first().startOffset).isEqualTo(emojiStart)
        assertThat(pages.zipWithNext().all { (first, second) -> first.endOffset == second.startOffset })
            .isTrue()
        assertThat(pages.none {
            it.endOffset < text.length && text[it.endOffset - 1].isHighSurrogate() && text[it.endOffset].isLowSurrogate()
        }).isTrue()
    }

    private fun assertContinuousUtf16Coverage(text: String, pages: List<ReaderPage>) {
        assertThat(pages.first().startOffset).isEqualTo(0)
        assertThat(pages.last().endOffset).isEqualTo(text.length)
        assertThat(pages.zipWithNext().all { (first, second) -> first.endOffset == second.startOffset })
            .isTrue()
        assertThat(pages.none { page ->
            page.endOffset in 1 until text.length &&
                text[page.endOffset - 1].isHighSurrogate() && text[page.endOffset].isLowSurrogate()
        }).isTrue()
    }
}
