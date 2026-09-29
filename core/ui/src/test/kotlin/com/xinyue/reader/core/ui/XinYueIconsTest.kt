package com.xinyue.reader.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class XinYueIconsTest {
    @Test
    fun `library interaction icons are real drawable resources`() {
        listOf(
            XinYueIcons.MoreVert,
            XinYueIcons.ArrowBack,
            XinYueIcons.Close,
            XinYueIcons.Check,
        ).forEach { assertThat(it).isNotEqualTo(0) }
    }
}
