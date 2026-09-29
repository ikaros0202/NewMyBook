package com.xinyue.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupNavigationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsBackupEntry_opensCompleteBackupHome() {
        compose.onNodeWithTag("nav_settings", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_backup"))
        compose.onNodeWithTag("settings_backup").assertIsDisplayed().performClick()
        compose.onNodeWithTag("backup_root").assertIsDisplayed()
        compose.onNodeWithText("导出备份").assertIsDisplayed()
        compose.onNodeWithText("从备份恢复").assertIsDisplayed()
        compose.onNodeWithText("所有处理都在本机完成").assertIsDisplayed()
        compose.onNodeWithTag("backup_home_list").performScrollToIndex(4)
        compose.onNodeWithText("导出全部批注").assertIsDisplayed()
        compose.onNodeWithText("导出单书阅读接力包").assertIsDisplayed()
        compose.onNodeWithText("应用阅读接力包").assertIsDisplayed()
    }
}
