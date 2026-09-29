package com.xinyue.reader.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TxtMetadataExtractorTest {
    @Test
    fun `extracts explicit Chinese title and author fields`() {
        val metadata = TxtMetadataExtractor.extract(
            text = """
                书名：《长夜列车》
                作者：林川

                第一章 启程
                正文
            """.trimIndent(),
            fallbackTitle = "下载文件名",
        )

        assertThat(metadata.title).isEqualTo("长夜列车")
        assertThat(metadata.author).isEqualTo("林川")
    }

    @Test
    fun `uses a short non chapter heading as title and parses inline author`() {
        val metadata = TxtMetadataExtractor.extract(
            text = """
                雾港来信
                文 / 苏遥
                第一章 海风
            """.trimIndent(),
            fallbackTitle = "novel-2026",
        )

        assertThat(metadata.title).isEqualTo("雾港来信")
        assertThat(metadata.author).isEqualTo("苏遥")
    }

    @Test
    fun `does not mistake a chapter heading for the book title`() {
        val metadata = TxtMetadataExtractor.extract(
            text = "第一章 初见\n故事从这里开始。",
            fallbackTitle = "星河",
        )

        assertThat(metadata.title).isEqualTo("星河")
        assertThat(metadata.author).isNull()
    }

    @Test
    fun `extracts explicit series and integer volume fields`() {
        val metadata = TxtMetadataExtractor.extract(
            text = """
                书名：远航
                作者：林川
                系列：星海纪事
                卷序：12

                第一章 启程
            """.trimIndent(),
            fallbackTitle = "远航",
        )

        assertThat(metadata.seriesName).isEqualTo("星海纪事")
        assertThat(metadata.seriesOrder).isEqualTo(12)
    }

    @Test
    fun `ignores guessed or invalid series ordering`() {
        val metadata = TxtMetadataExtractor.extract(
            text = """
                星海纪事 03 归途
                Volume: three
                第一章 回家
            """.trimIndent(),
            fallbackTitle = "星海纪事-03-归途",
        )

        assertThat(metadata.seriesName).isNull()
        assertThat(metadata.seriesOrder).isNull()
    }
}
