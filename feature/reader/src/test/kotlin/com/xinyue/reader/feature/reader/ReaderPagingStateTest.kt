package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.ReaderPageAnimation
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.text.DetectedChapter
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReaderPagingStateTest {
    @Test
    fun `system bar icon contrast follows final composited background luminance`() {
        assertThat(readerUsesDarkSystemBarIcons(ReaderSettings(backgroundArgb = 0xFFFFFFFF))).isTrue()
        assertThat(readerUsesDarkSystemBarIcons(ReaderSettings(backgroundArgb = 0xFF000000))).isFalse()
        assertThat(
            readerUsesDarkSystemBarIcons(
                ReaderSettings(
                    backgroundArgb = 0xFFFFFFFF,
                    warmOverlayArgb = 0xFF000000,
                    warmOverlayOpacity = 1f,
                ),
            ),
        ).isFalse()
        assertThat(
            readerEffectiveBackgroundArgb(
                ReaderSettings(
                    backgroundArgb = 0xFF000000,
                    warmOverlayArgb = 0xFFFFFFFF,
                    warmOverlayOpacity = 1f,
                ),
            ),
        ).isEqualTo(0xFFFFFFFF)
    }

    @Test
    fun `restored page is used as the pager initial page`() {
        assertThat(readerInitialPage(restoredPageIndex = 1, pageCount = 3)).isEqualTo(1)
    }

    @Test
    fun `restored page is clamped when pagination changed`() {
        assertThat(readerInitialPage(restoredPageIndex = 9, pageCount = 3)).isEqualTo(2)
        assertThat(readerInitialPage(restoredPageIndex = -1, pageCount = 3)).isEqualTo(0)
        assertThat(readerInitialPage(restoredPageIndex = 2, pageCount = 0)).isEqualTo(0)
    }

    @Test
    fun `position info reports independent whole book and current chapter progress`() {
        val info = readerPositionInfo(
            anchorOffset = 450,
            contentLength = 1_000,
            chapters = listOf(
                DetectedChapter("第一章", 100),
                DetectedChapter("第二章", 400),
                DetectedChapter("第三章", 800),
            ),
        )

        assertThat(info.chapterTitle).isEqualTo("第二章")
        assertThat(info.bookProgressPercent).isEqualTo(45)
        assertThat(info.chapterProgressPercent).isEqualTo(12)
    }

    @Test
    fun `position info safely handles empty books and anchors before the first heading`() {
        val empty = readerPositionInfo(20, 0, emptyList())
        val beforeFirst = readerPositionInfo(
            anchorOffset = 50,
            contentLength = 1_000,
            chapters = listOf(DetectedChapter("第一章", 100)),
        )

        assertThat(empty.bookProgressPercent).isEqualTo(0)
        assertThat(empty.chapterProgressPercent).isEqualTo(0)
        assertThat(beforeFirst.chapterTitle).isEqualTo("正文")
        assertThat(beforeFirst.chapterProgressPercent).isEqualTo(50)
    }

    @Test
    fun `progress slider maps its full range to safe absolute anchors`() {
        assertThat(readerProgressOffset(0f, contentLength = 1_000)).isEqualTo(0)
        assertThat(readerProgressOffset(0.45f, contentLength = 1_000)).isEqualTo(450)
        assertThat(readerProgressOffset(1f, contentLength = 1_000)).isEqualTo(1_000)
        assertThat(readerProgressOffset(-2f, contentLength = 1_000)).isEqualTo(0)
        assertThat(readerProgressOffset(2f, contentLength = 1_000)).isEqualTo(1_000)
        assertThat(readerProgressOffset(0.5f, contentLength = 0)).isEqualTo(0)
    }

    @Test
    fun `tap coordinates resolve to all nine reading zones`() {
        assertThat(readerTapZone(x = 0f, y = 0f, width = 300f, height = 600f)).isEqualTo(0)
        assertThat(readerTapZone(x = 150f, y = 300f, width = 300f, height = 600f)).isEqualTo(4)
        assertThat(readerTapZone(x = 299f, y = 599f, width = 300f, height = 600f)).isEqualTo(8)
    }

    @Test
    fun `vertical edge drag adjusts and clamps reader brightness`() {
        assertThat(adjustReaderBrightness(start = 0.5f, dragDeltaY = -100f, height = 400f)).isEqualTo(0.75f)
        assertThat(adjustReaderBrightness(start = 0.5f, dragDeltaY = 100f, height = 400f)).isEqualTo(0.25f)
        assertThat(adjustReaderBrightness(start = 0.5f, dragDeltaY = -1_000f, height = 400f)).isEqualTo(1f)
        assertThat(adjustReaderBrightness(start = 0.5f, dragDeltaY = 1_000f, height = 400f)).isEqualTo(0.02f)
    }

    @Test
    fun `reading surface input is disabled while controls or touch lock are active`() {
        assertThat(readerContentInputEnabled(touchLocked = false, controlsVisible = false)).isTrue()
        assertThat(readerContentInputEnabled(touchLocked = false, controlsVisible = true)).isFalse()
        assertThat(readerContentInputEnabled(touchLocked = true, controlsVisible = false)).isFalse()
        assertThat(readerContentInputEnabled(touchLocked = true, controlsVisible = true)).isFalse()
        assertThat(
            readerContentInputEnabled(
                touchLocked = false,
                controlsVisible = false,
                selectionActive = true,
            ),
        ).isFalse()
        assertThat(
            readerContentInputEnabled(
                touchLocked = false,
                controlsVisible = false,
                isPaginating = true,
            ),
        ).isFalse()
    }

    @Test
    fun `restore marker blocks coarse or active pagination but not settled measured pages`() {
        assertThat(
            readerPageMoveBlockedByRestore(
                pendingRestoreOffset = 17,
                isPaginating = false,
                hasMeasuredLayout = false,
            ),
        ).isTrue()
        assertThat(
            readerPageMoveBlockedByRestore(
                pendingRestoreOffset = 17,
                isPaginating = true,
                hasMeasuredLayout = true,
            ),
        ).isTrue()
        assertThat(
            readerPageMoveBlockedByRestore(
                pendingRestoreOffset = 17,
                isPaginating = false,
                hasMeasuredLayout = true,
            ),
        ).isFalse()
        assertThat(
            readerPageMoveBlockedByRestore(
                pendingRestoreOffset = null,
                isPaginating = true,
                hasMeasuredLayout = false,
            ),
        ).isFalse()
    }

    @Test
    fun `page number is exact within the current chapter`() {
        val info = readerPageNumberInfo(
            anchorOffset = 200,
            contentLength = 400,
            pages = listOf(
                ReaderPage(0, 100),
                ReaderPage(100, 200),
                ReaderPage(200, 300),
                ReaderPage(300, 400),
            ),
            currentPageIndex = 2,
            chapters = listOf(
                DetectedChapter("第一章", 0),
                DetectedChapter("第二章", 100),
                DetectedChapter("第三章", 300),
            ),
        )

        assertThat(info).isEqualTo(ReaderPageNumberInfo(current = 2, total = 2, isEstimated = false))
        assertThat(info?.displayText).isEqualTo("第2/2页")
    }

    @Test
    fun `page number is explicitly estimated within a bounded current chapter window`() {
        val info = readerPageNumberInfo(
            anchorOffset = 450,
            contentLength = 1_000,
            pages = listOf(
                ReaderPage(300, 400),
                ReaderPage(400, 500),
                ReaderPage(500, 600),
            ),
            currentPageIndex = 1,
            chapters = listOf(
                DetectedChapter("第一章", 0),
                DetectedChapter("第二章", 300),
                DetectedChapter("第三章", 700),
            ),
        )

        assertThat(info).isEqualTo(ReaderPageNumberInfo(current = 2, total = 4, isEstimated = true))
        assertThat(info?.displayText).isEqualTo("第2/4页")
        assertThat(readerPageNumberInfo(0, 0, emptyList(), 0, emptyList())).isNull()
    }

    @Test
    fun `page animation only degrades after sustained or severe slow frames`() {
        assertThat(shouldDisablePageAnimation(List(12) { 16.7f })).isFalse()
        assertThat(shouldDisablePageAnimation(listOf(16f, 17f, 18f, 40f, 16f))).isFalse()
        assertThat(
            shouldDisablePageAnimation(
                listOf(16f, 17f, 41f, 16f, 39f, 17f, 42f, 16f, 17f, 16f),
            ),
        ).isTrue()
        assertThat(
            shouldDisablePageAnimation(listOf(16f, 17f, 16f, 105f, 17f, 16f, 17f, 16f)),
        ).isTrue()
    }

    @Test
    fun `cover animation holds the revealed page while the foreground page moves`() {
        assertThat(readerCoverTranslation(pageOffset = 0.4f, pageWidth = 1_000f)).isEqualTo(400f)
        assertThat(readerCoverTranslation(pageOffset = -0.6f, pageWidth = 1_000f)).isEqualTo(0f)
    }

    @Test
    fun `programmatic page turns publish state only after the selected pager movement`() = runTest {
        ReaderPageAnimation.entries.forEach { animation ->
            val events = mutableListOf<String>()

            turnReaderPagerToPage(
                targetPage = 2,
                pageAnimation = animation,
                scrollToPage = { animated ->
                    events += "scroll:$animated"
                },
                onPageChanged = { page ->
                    events += "state:$page"
                },
            )

            assertThat(events).containsExactly(
                "scroll:${animation != ReaderPageAnimation.NONE}",
                "state:2",
            ).inOrder()
        }
    }

    @Test
    fun `chapter title extraction uses absolute line ranges and distinguishes synthetic正文`() {
        val rawWindow = "intro\nChapter One\nbody"
        val windowStart = 100
        val titleLocalStart = rawWindow.indexOf("Chapter One")
        val titleStart = windowStart + titleLocalStart
        val titleEnd = windowStart + rawWindow.indexOf('\n', titleLocalStart)

        assertThat(
            readerChapterTitleRanges(
                rawText = rawWindow,
                windowStartOffset = windowStart,
                chapters = listOf(
                    DetectedChapter("invalid", windowStart - 1),
                    DetectedChapter("Chapter One", titleStart),
                    DetectedChapter("duplicate", titleStart),
                    DetectedChapter("outside", windowStart + rawWindow.length),
                ),
                chaptersManuallyEdited = false,
            ),
        ).containsExactly(ReaderChapterTitleRange(titleStart, titleEnd))

        val synthetic = "\u6b63\u6587"
        assertThat(
            readerChapterTitleRanges(
                rawText = synthetic,
                windowStartOffset = 0,
                chapters = listOf(DetectedChapter("\u6b63\u6587", 0)),
                chaptersManuallyEdited = false,
            ),
        ).isEmpty()
        assertThat(
            readerChapterTitleRanges(
                rawText = synthetic,
                windowStartOffset = 0,
                chapters = listOf(DetectedChapter("\u6b63\u6587", 0)),
                chaptersManuallyEdited = true,
            ),
        ).containsExactly(ReaderChapterTitleRange(0, synthetic.length))
    }

    @Test
    fun `chapter title extraction preserves crlf and eof title ends in a nonzero window`() {
        val rawWindow = "prefix\r\nCRLF title\r\nEOF title"
        val windowStart = 40
        val crlfStart = windowStart + rawWindow.indexOf("CRLF title")
        val eofStart = windowStart + rawWindow.indexOf("EOF title")

        assertThat(
            readerChapterTitleRanges(
                rawText = rawWindow,
                windowStartOffset = windowStart,
                chapters = listOf(
                    DetectedChapter("CRLF title", crlfStart),
                    DetectedChapter("EOF title", eofStart),
                ),
                chaptersManuallyEdited = true,
            ),
        ).containsExactly(
            ReaderChapterTitleRange(crlfStart, windowStart + rawWindow.indexOf('\n', crlfStart - windowStart)),
            ReaderChapterTitleRange(eofStart, windowStart + rawWindow.length),
        ).inOrder()
    }

    @Test
    fun `running chapter title is hidden only on real title start pages`() {
        val title = ReaderChapterTitleRange(100, 110)

        assertThat(readerShouldShowRunningChapterTitle(true, 100, listOf(title))).isFalse()
        assertThat(readerShouldShowRunningChapterTitle(true, 101, listOf(title))).isTrue()
        assertThat(readerShouldShowRunningChapterTitle(false, 100, listOf(title))).isFalse()
        assertThat(readerShouldShowRunningChapterTitle(true, 100, emptyList())).isTrue()
    }

    @Test
    fun `running header reserves stable space even when hidden on a chapter title page`() {
        assertThat(
            readerShouldReserveRunningHeaderSpace(
                showBookTitle = false,
                bookTitleAvailable = true,
                showChapterTitle = true,
                chapterTitleAvailable = true,
            ),
        ).isTrue()
        assertThat(
            readerShouldReserveRunningHeaderSpace(
                showBookTitle = false,
                bookTitleAvailable = true,
                showChapterTitle = false,
                chapterTitleAvailable = true,
            ),
        ).isFalse()
    }

    @Test
    fun `running chapter title skips leading layout placeholders on an effective chapter page`() {
        val title = ReaderChapterTitleRange(2, 5)

        assertThat(
            readerShouldShowRunningChapterTitle(
                showChapterTitle = true,
                pageStartOffset = 0,
                pageText = "\u2060\u2060第一章",
                titleRanges = listOf(title),
            ),
        ).isFalse()
    }

    @Test
    fun `dense title range lookup only reads binary search probes and the hit slice`() {
        val ranges = CountingTitleRanges(10_000)

        val hits = ranges.intersectingTitleRanges(
            textStartOffset = 20_000,
            textEndOffset = 20_010,
        ).toList()

        assertThat(hits.map(ReaderChapterTitleRange::startOffset))
            .containsExactly(20_000, 20_004, 20_008)
            .inOrder()
        assertThat(ranges.getCount).isLessThan(100)
        assertThat(ranges.getCount).isLessThan(10_000)
    }

    private class CountingTitleRanges(
        override val size: Int,
    ) : AbstractList<ReaderChapterTitleRange>() {
        var getCount: Int = 0

        override fun get(index: Int): ReaderChapterTitleRange {
            getCount += 1
            return ReaderChapterTitleRange(index * 4, index * 4 + 2)
        }
    }

}
