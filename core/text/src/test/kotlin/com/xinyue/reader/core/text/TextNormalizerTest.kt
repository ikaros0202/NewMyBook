package com.xinyue.reader.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun `normalizes bom and mixed line endings without changing content offsets unnecessarily`() {
        val result = TextNormalizer.normalize("\uFEFF第一行\r\n第二行\r第三行")

        assertThat(result).isEqualTo("第一行\n第二行\n第三行")
    }

    @Test
    fun `keeps supplementary unicode characters intact`() {
        val result = TextNormalizer.normalize("开场😀\r\n结束")

        assertThat(result).isEqualTo("开场😀\n结束")
    }
}
