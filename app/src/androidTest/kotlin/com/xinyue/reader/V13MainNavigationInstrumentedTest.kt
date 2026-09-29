package com.xinyue.reader

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.espresso.Espresso.pressBack
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class V13MainNavigationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun libraryStatisticsAndSettingsRootTabsUseVisibleLabelsAndLibraryIsDefault() {
        compose.onNodeWithTag("library_root").assertIsDisplayed()
        compose.onNodeWithContentDescription("书架").assertIsSelected()
        compose.onNodeWithContentDescription("统计").assertIsNotSelected()
        compose.onNodeWithContentDescription("设置").assertIsNotSelected()
        compose.onNodeWithText("⌂").assertDoesNotExist()
        compose.onNodeWithText("▣").assertDoesNotExist()
        compose.onNodeWithText("⚙").assertDoesNotExist()
        compose.onNodeWithTag("nav_library", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("nav_statistics", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("statistics_root").assertIsDisplayed()
        compose.onNodeWithText("阅读统计").assertIsDisplayed()
        compose.onNodeWithText("返回").assertDoesNotExist()
        compose.onNodeWithContentDescription("统计").assertIsSelected()

        compose.onNodeWithTag("nav_library", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("书架").assertIsSelected()
        compose.onNodeWithTag("library_root").assertIsDisplayed()
        compose.onNodeWithTag("library_search_trigger").assertIsDisplayed().performClick()
        compose.onNodeWithTag("library_search_root").assertIsDisplayed()
        compose.onAllNodesWithTag("nav_library", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("library_search_field").performTextInput("山")
        compose.onNodeWithContentDescription("返回书架").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithTag("library_root").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        compose.onNodeWithTag("library_root").assertIsDisplayed()
        compose.onNodeWithTag("nav_library", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("自动视图").assertDoesNotExist()
        compose.onNodeWithContentDescription("书架").assertIsSelected()

        compose.onNodeWithTag("nav_settings", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("settings_root").assertIsDisplayed()
        val entries = listOf(
            "全局阅读外观",
            "主题与自动切换",
            "导入字体",
            "精细排版与专注带",
            "阅读行为",
            "阅读信息",
            "备份与恢复",
        )
        entries.dropLast(1).forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_backup"))
        compose.onNodeWithTag("settings_backup").assertIsDisplayed()

        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_behavior"))
        compose.onNodeWithTag("settings_behavior").performClick()
        compose.onNodeWithTag("global_settings_behavior").assertIsDisplayed()
        compose.onAllNodesWithTag("nav_settings", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("global_keep_screen").assertIsOn().performClick()
        pressBack()
        compose.onNodeWithText("放弃未保存的更改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("global_keep_screen").assertIsOff()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("settings_root").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("settings_root").assertIsDisplayed()
        compose.onNodeWithTag("nav_settings", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("settings_behavior").performClick()
        compose.onNodeWithTag("global_keep_screen").assertIsOff()
    }
}
