package com.xinyue.reader.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderLayoutWhitespaceNormalizerTest {
    @Test
    fun `collapses excessive whitespace between paragraphs to one logical newline`() {
        val text = "上一章正文\n\n\n\n\n第二章\n新章正文"

        val normalized = ReaderLayoutWhitespaceNormalizer.normalize(text)

        assertThat(normalized).isEqualTo("上一章正文\u2060\u2060\u2060\u2060\n第二章\n新章正文")
        assertPreservesLengthAndNonWhitespaceOffsets(text, normalized)
    }

    @Test
    fun `collapses every ordinary paragraph spacer to one logical newline`() {
        val text = "正文一\n\n正文二\n\n\n场景转换\n正文三"

        val normalized = ReaderLayoutWhitespaceNormalizer.normalize(text)

        assertThat(normalized).isEqualTo("正文一\u2060\n正文二\u2060\u2060\n场景转换\n正文三")
        assertPreservesLengthAndNonWhitespaceOffsets(text, normalized)
    }

    @Test
    fun `leading and trailing layout whitespace consumes no layout lines`() {
        val text = "\n\n　正文\n\n\n"

        val normalized = ReaderLayoutWhitespaceNormalizer.normalize(text)

        assertThat(normalized).isEqualTo("\u2060\u2060\u2060正文\u2060\u2060\u2060")
        assertPreservesLengthAndNonWhitespaceOffsets(text, normalized)
    }

    @Test
    fun `suppresses spaces full width spaces and tabs only at physical line starts`() {
        val text = "\t　  第一行\n  第二\t行\n第三  行\n"

        val normalized = ReaderLayoutWhitespaceNormalizer.normalize(text)

        assertThat(normalized).isEqualTo("\u2060\u2060\u2060\u2060第一行\n\u2060\u2060第二\t行\n第三  行\u2060")
        assertPreservesLengthAndNonWhitespaceOffsets(text, normalized)
    }

    @Test
    fun `preserves one CRLF logical newline and supplementary characters`() {
        val text = "甲😀\r\n\r\n\t乙"

        val normalized = ReaderLayoutWhitespaceNormalizer.normalize(text)

        assertThat(normalized).isEqualTo("甲😀\u2060\u2060\r\n\u2060乙")
        assertPreservesLengthAndNonWhitespaceOffsets(text, normalized)
        assertThat(normalized.indexOf("😀")).isEqualTo(text.indexOf("😀"))
    }

    @Test
    fun `suppresses the entire whitespace run immediately before a hard chapter boundary`() {
        val text = "上一章正文\n\n \t　第二章\n正文"
        val chapterStart = text.indexOf("第二章")

        val normalized = ReaderLayoutWhitespaceNormalizer.normalize(
            text = text,
            hardBoundaries = listOf(chapterStart),
        )

        assertThat(normalized).isEqualTo("上一章正文\u2060\u2060\u2060\u2060\u2060第二章\n正文")
        assertPreservesLengthAndNonWhitespaceOffsets(text, normalized)
    }

    @Test
    fun `ignores duplicate zero end and out of range hard boundaries`() {
        val text = "甲\n\n第二章\n乙"
        val chapterStart = text.indexOf("第二章")

        val normalized = ReaderLayoutWhitespaceNormalizer.normalize(
            text = text,
            hardBoundaries = listOf(-1, 0, chapterStart, chapterStart, text.length, text.length + 1),
        )

        assertThat(normalized).isEqualTo("甲\u2060\u2060第二章\n乙")
        assertPreservesLengthAndNonWhitespaceOffsets(text, normalized)
    }

    @Test
    fun `normalizes a 50 MiB all newline input`() {
        val newlineCount = 50 * 1024 * 1024
        val text = "\n".repeat(newlineCount)

        val normalized = ReaderLayoutWhitespaceNormalizer.normalize(text)

        assertThat(normalized.length).isEqualTo(newlineCount)
        assertThat(normalized.all { it == '\u2060' }).isTrue()
    }

    private fun assertPreservesLengthAndNonWhitespaceOffsets(text: String, normalized: String) {
        assertThat(normalized.length).isEqualTo(text.length)
        text.indices
            .filterNot { text[it].isWhitespace() }
            .forEach { index ->
                assertThat(normalized[index]).isEqualTo(text[index])
            }
    }
}
