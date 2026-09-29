package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.text.DetectedChapter
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReaderPositionPreviewServiceTest {
    @Test
    fun `fractions clamp and a fifty MiB book still uses a bounded window`() = runTest {
        val source = RecordingPreviewTextSource("预览正文")
        val service = ReaderPositionPreviewService(source)
        val length = 50L * 1024 * 1024

        val beforeStart = service.preview("books/book/content.txt", length, emptyList(), -2f)
        val afterEnd = service.preview("books/book/content.txt", length, emptyList(), 2f)

        assertThat(beforeStart.targetOffset).isEqualTo(0)
        assertThat(afterEnd.targetOffset).isEqualTo(length.toInt())
        assertThat(source.requests).hasSize(2)
        source.requests.forEach { request ->
            assertThat(request.beforeUtf16Units).isAtMost(80)
            assertThat(request.afterUtf16Units).isAtMost(160)
            assertThat(request.beforeUtf16Units + request.afterUtf16Units).isLessThan(length.toInt())
        }
    }

    @Test
    fun `preview chooses nearest preceding chapter and normalizes whitespace`() = runTest {
        val source = RecordingPreviewTextSource("  甲\n\t😀   乙  ")
        val service = ReaderPositionPreviewService(source)

        val preview = service.preview(
            normalizedPath = "books/book/content.txt",
            totalUtf16Length = 1_000,
            chapters = listOf(
                DetectedChapter("第一章", 0),
                DetectedChapter("第二章", 400),
                DetectedChapter("第三章", 800),
            ),
            fraction = 0.75f,
        )

        assertThat(preview.targetOffset).isEqualTo(750)
        assertThat(preview.chapterTitle).isEqualTo("第二章")
        assertThat(preview.percent).isEqualTo(75)
        assertThat(preview.snippet).isEqualTo("甲 😀 乙")
        assertThat(preview.snippet.last().isHighSurrogate()).isFalse()
    }

    @Test
    fun `empty and non finite fractions stay safe`() = runTest {
        val source = RecordingPreviewTextSource("")
        val service = ReaderPositionPreviewService(source)

        val preview = service.preview("books/book/content.txt", 0, emptyList(), Float.NaN)

        assertThat(preview).isEqualTo(
            ReaderPositionPreview(targetOffset = 0, chapterTitle = "正文", percent = 0, snippet = ""),
        )
        assertThat(source.requests.single().afterUtf16Units).isGreaterThan(0)
    }

    @Test
    fun `preview removes surrogate halves exposed at a bounded window edge`() = runTest {
        val source = RecordingPreviewTextSource("\uDE00正文\uD83D")

        val preview = ReaderPositionPreviewService(source).preview(
            normalizedPath = "books/book/content.txt",
            totalUtf16Length = 1_000,
            chapters = emptyList(),
            fraction = 0.5f,
        )

        assertThat(preview.snippet).isEqualTo("正文")
        assertThat(preview.snippet.first().isLowSurrogate()).isFalse()
        assertThat(preview.snippet.last().isHighSurrogate()).isFalse()
    }

    private class RecordingPreviewTextSource(private val text: String) : TextSource {
        val requests = mutableListOf<Request>()

        override suspend fun readWindow(
            normalizedPath: String,
            anchorOffset: Long,
            beforeUtf16Units: Int,
            afterUtf16Units: Int,
        ): TextWindow {
            requests += Request(anchorOffset, beforeUtf16Units, afterUtf16Units)
            return TextWindow(
                startOffset = (anchorOffset - beforeUtf16Units).coerceAtLeast(0),
                text = text,
                totalUtf16Length = anchorOffset.coerceAtLeast(text.length.toLong()),
            )
        }
    }

    private data class Request(
        val anchorOffset: Long,
        val beforeUtf16Units: Int,
        val afterUtf16Units: Int,
    )
}
