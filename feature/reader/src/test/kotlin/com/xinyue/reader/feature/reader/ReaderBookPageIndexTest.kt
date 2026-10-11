package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.data.TextSource
import com.xinyue.reader.core.data.TextWindow
import com.xinyue.reader.core.text.DetectedChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReaderBookPageIndexTest {
    @Test
    fun `holds a partial page at each read boundary and returns contiguous absolute pages`() = runTest {
        val content = "0123456789abcdefghijklmnopqrstuvwxyz"
        val pages = buildReaderBookPageIndex(
            textSource = WindowTextSource(content),
            paginator = FixedCharacterPaginator(pageSize = 4),
            normalizedPath = "fixture.txt",
            totalUtf16Length = content.length.toLong(),
            spec = testSpec(),
            chapters = listOf(DetectedChapter("正文", 0)),
            chaptersManuallyEdited = false,
            computationDispatcher = Dispatchers.Unconfined,
            chunkUtf16Units = 10,
            contextUtf16Units = 3,
        )

        assertThat(pages).containsExactlyElementsIn(
            content.chunked(4).mapIndexed { index, chunk ->
                ReaderPage(index * 4, index * 4 + chunk.length)
            },
        ).inOrder()
    }

    @Test
    fun `chapter starts remain hard page boundaries when a chapter crosses a read boundary`() = runTest {
        val content = "abcdefghijklmno123456789"
        val pages = buildReaderBookPageIndex(
            textSource = WindowTextSource(content),
            paginator = FixedCharacterPaginator(pageSize = 64),
            normalizedPath = "fixture.txt",
            totalUtf16Length = content.length.toLong(),
            spec = testSpec(),
            chapters = listOf(
                DetectedChapter("第一章", 0),
                DetectedChapter("第二章", 13),
            ),
            chaptersManuallyEdited = true,
            computationDispatcher = Dispatchers.Unconfined,
            chunkUtf16Units = 10,
            contextUtf16Units = 2,
        )

        assertThat(pages).containsExactly(
            ReaderPage(0, 13),
            ReaderPage(13, content.length),
        ).inOrder()
    }

    @Test
    fun `chapter separator whitespace uses the same boundaries as visible pagination`() = runTest {
        val content = "abcdef\u2060\u2060\u2060第二章\n后续正文后续正文"
        val paginator = FixedCharacterPaginator(pageSize = 64)
        val chapters = listOf(DetectedChapter("第一章", 0), DetectedChapter("第二章", 9))
        val pages = buildReaderBookPageIndex(
            textSource = WindowTextSource(content),
            paginator = paginator,
            normalizedPath = "fixture.txt",
            totalUtf16Length = content.length.toLong(),
            spec = testSpec(),
            chapters = chapters,
            chaptersManuallyEdited = true,
            computationDispatcher = Dispatchers.Unconfined,
            chunkUtf16Units = 9,
            contextUtf16Units = 4,
        )
        assertThat(pages).containsExactlyElementsIn(
            paginator.paginateAtChapterStarts(content, testSpec(), listOf(0, 9)),
        ).inOrder()
    }

    private fun testSpec() = ReaderLayoutSpec(
        widthPx = 320,
        heightPx = 480,
        fontSizePx = 18f,
        lineHeightPx = 26f,
    )

    private class WindowTextSource(private val content: String) : TextSource {
        override suspend fun readWindow(
            normalizedPath: String,
            anchorOffset: Long,
            beforeUtf16Units: Int,
            afterUtf16Units: Int,
        ): TextWindow {
            val anchor = anchorOffset.coerceIn(0, content.length.toLong()).toInt()
            val start = (anchor - beforeUtf16Units).coerceAtLeast(0)
            val end = (anchor + afterUtf16Units).coerceAtMost(content.length)
            return TextWindow(
                startOffset = start.toLong(),
                text = content.substring(start, end),
                totalUtf16Length = content.length.toLong(),
            )
        }
    }

    private class FixedCharacterPaginator(private val pageSize: Int) : BookPaginator {
        override suspend fun paginate(text: String, spec: ReaderLayoutSpec): List<ReaderPage> =
            text.chunked(pageSize).mapIndexed { index, page ->
                ReaderPage(index * pageSize, index * pageSize + page.length)
            }
    }
}
