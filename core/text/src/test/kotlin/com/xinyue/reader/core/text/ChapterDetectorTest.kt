package com.xinyue.reader.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChapterDetectorTest {
    @Test
    fun `detects common Chinese chapter headings with stable UTF-16 offsets`() {
        val text = "序言内容\n第一章 初见\n正文😀\n第十二章 重逢\n尾声"

        val chapters = ChapterDetector.detect(text)

        assertThat(chapters.map { it.title }).containsExactly("第一章 初见", "第十二章 重逢").inOrder()
        assertThat(chapters[0].startOffset).isEqualTo(text.indexOf("第一章"))
        assertThat(chapters[1].startOffset).isEqualTo(text.indexOf("第十二章"))
    }

    @Test
    fun `returns a synthetic beginning chapter when no heading exists`() {
        val chapters = ChapterDetector.detect("只有正文，没有章节标题。")

        assertThat(chapters).hasSize(1)
        assertThat(chapters.single().title).isEqualTo("正文")
        assertThat(chapters.single().startOffset).isEqualTo(0)
    }

    @Test
    fun `broad rules include common preface and ending headings`() {
        val text = "序章\n正文\n第一章 开始\n内容\n尾声\n结束"

        val chapters = ChapterDetector.detect(text, ChapterRuleSet.BROAD)

        assertThat(chapters.map { it.title })
            .containsExactly("序章", "第一章 开始", "尾声")
            .inOrder()
    }

    @Test
    fun `numbered rules include Chinese list and English chapter headings`() {
        val text = "1、开端\n正文\nChapter 2 Return\n正文\n第三章 收束\n正文"

        val chapters = ChapterDetector.detect(text, ChapterRuleSet.NUMBERED)

        assertThat(chapters.map { it.title })
            .containsExactly("1、开端", "Chapter 2 Return", "第三章 收束")
            .inOrder()
    }

    @Test
    fun `standard rules do not treat broad or numbered headings as chapters`() {
        val text = "序章\n正文\n1、开端\n正文\nChapter 2 Return\n正文"

        val chapters = ChapterDetector.detect(text, ChapterRuleSet.STANDARD)

        assertThat(chapters).containsExactly(DetectedChapter("正文", 0))
    }

    @Test
    fun `duplicate titles remain separate when offsets differ`() {
        val text = "第一章 重逢\nA\n第一章 重逢\nB"

        val chapters = ChapterDetector.detect(text)

        assertThat(chapters.map { it.startOffset })
            .containsExactly(text.indexOf("第一章"), text.lastIndexOf("第一章"))
            .inOrder()
    }

    @Test
    fun `heading candidates must occupy a complete reasonably sized line`() {
        val tooLong = "第一章 " + "很长".repeat(80)
        val text = "这里提到第一章 但不是标题\n3.14\n$tooLong\n正文"

        val chapters = ChapterDetector.detect(text, ChapterRuleSet.BROAD)

        assertThat(chapters).containsExactly(DetectedChapter("正文", 0))
    }

    @Test
    fun `detects headings across CRLF without shifting UTF-16 offsets`() {
        val text = "前言内容\r\n第一章 开始\r\n正文\r\n尾声\r\n结束"

        val chapters = ChapterDetector.detect(text, ChapterRuleSet.BROAD)

        assertThat(chapters.map { it.title }).containsExactly("第一章 开始", "尾声").inOrder()
        assertThat(chapters.map { it.startOffset })
            .containsExactly(text.indexOf("第一章"), text.indexOf("尾声"))
            .inOrder()
    }

    @Test
    fun `accepts full width indentation around an otherwise strict heading`() {
        val text = "第一章\n正文\n\u3000\u3000第二章\u3000\n正文\n第三章"

        val chapters = ChapterDetector.detect(text)

        assertThat(chapters.map { it.title })
            .containsExactly("第一章", "第二章", "第三章")
            .inOrder()
        assertThat(chapters[1].startOffset).isEqualTo(text.indexOf("第二章"))
    }
}
