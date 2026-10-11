package com.xinyue.reader.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChapterStreamScannerTest {
    @Test
    fun `every split preserves the complete heading and absolute offset`() {
        val text = "公开正文\n  第一章\t春夏秋冬\n正文\n第二章　月色"
        val expected = ChapterDetector.detect(text)
        for (split in 1 until text.length) {
            val scanner = ChapterStreamScanner(ChapterRuleSet.STANDARD)
            val chapters = scanner.accept(0, text.take(split), false) +
                scanner.accept(split.toLong(), text.drop(split), true)
            assertThat(chapters).containsExactlyElementsIn(expected).inOrder()
        }
    }

    @Test
    fun `long physical lines never create headings from a continuation`() {
        val text = "甲".repeat(262_140) + "第一章 假标题\n" + " ".repeat(800) + "第二章 真标题\n"
        val scanner = ChapterStreamScanner(ChapterRuleSet.STANDARD)
        val chapters = text.chunked(262_144).flatMapIndexed { index, part ->
            scanner.accept(index * 262_144L, part, (index + 1L) * 262_144 >= text.length)
        }
        assertThat(chapters).containsExactly(DetectedChapter("第二章 真标题", text.indexOf("第二章")))
    }
}
