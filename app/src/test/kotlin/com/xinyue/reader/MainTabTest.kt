package com.xinyue.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MainTabTest {
    @Test
    fun `unknown saved tab falls back to the library root`() {
        assertThat(mainTabForState("HOME")).isEqualTo(MainTab.LIBRARY)
        assertThat(mainTabForState("not-a-tab")).isEqualTo(MainTab.LIBRARY)
    }

    @Test
    fun `root navigation has one stable icon definition for each approved tab`() {
        assertThat(MainTab.entries.map(MainTab::contentDescription)).containsExactly(
            "书架",
            "统计",
            "设置",
        ).inOrder()

        MainTab.entries.forEach { tab ->
            assertThat(tab.outlinedIconRes).isNotEqualTo(0)
            assertThat(tab.selectedIconRes).isNotEqualTo(0)
            if (tab != MainTab.STATISTICS) {
                assertThat(tab.selectedIconRes).isNotEqualTo(tab.outlinedIconRes)
            }
        }
    }
}
