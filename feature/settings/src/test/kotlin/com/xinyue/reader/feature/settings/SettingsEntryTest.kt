package com.xinyue.reader.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsEntryTest {
    @Test
    fun `settings overview keeps exactly the seven approved entries`() {
        assertThat(SettingsEntry.entries.map(SettingsEntry::title)).containsExactly(
            "全局阅读外观",
            "主题与自动切换",
            "导入字体",
            "精细排版与专注带",
            "阅读行为",
            "阅读信息",
            "备份与恢复",
        ).inOrder()
    }

    @Test
    fun `settings entries form three readable groups without losing an entry`() {
        assertThat(SettingsEntry.entries.groupBy(SettingsEntry::section).mapValues { it.value.map(SettingsEntry::title) })
            .containsExactly(
                SettingsSection.COMMON_READING, listOf(
                    "全局阅读外观",
                    "阅读行为",
                ),
                SettingsSection.PERSONALIZATION, listOf(
                    "主题与自动切换",
                    "导入字体",
                    "精细排版与专注带",
                ),
                SettingsSection.INFORMATION_LOCAL, listOf(
                    "阅读信息",
                    "备份与恢复",
                ),
            )
    }

    @Test
    fun `settings overview uses reader first group titles`() {
        assertThat(SettingsSection.entries.map(SettingsSection::title)).containsExactly(
            "常用阅读",
            "个性化",
            "信息与本地数据",
        ).inOrder()
    }
}
