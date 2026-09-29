package com.xinyue.reader

import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderV14MenuInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val targetPackage: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    @Test
    fun publicFixtureUsesApprovedReaderMenuDrawerAppearanceAndNotes() {
        ensurePublicFixtureImported()
        compose.onNodeWithText(FIXTURE_TITLE).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("reader_root").fetchSemanticsNodes().isNotEmpty()
        }
        capturePublicScreenshot("reader-page")

        openReaderMenu()
        MENU_TAGS.forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        compose.onAllNodesWithTag("reader-progress-slider").assertCountEquals(0)
        capturePublicScreenshot("reader-menu")

        compose.onNodeWithTag("reader-menu-navigation").performClick()
        compose.onNodeWithTag("reader-navigation-drawer", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("• 目录").assertIsDisplayed()
        capturePublicScreenshot("navigation-drawer")
        val chapterNodes = compose.onAllNodesWithTag(
            "reader-directory-chapter",
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        check(chapterNodes.isNotEmpty()) { "directory did not expose chapter rows" }
        chapterNodes.forEach { node ->
            val text = if (node.config.contains(SemanticsProperties.Text)) {
                node.config[SemanticsProperties.Text].joinToString("") { it.text }
            } else {
                ""
            }
            check('%' !in text) { "chapter row contains a percentage: $text" }
        }
        compose.onNodeWithText("书签").performClick()
        compose.onNodeWithText("• 书签").assertIsDisplayed()
        device.pressBack()

        openReaderMenu()
        compose.onNodeWithTag("reader-menu-appearance").performClick()
        compose.onNodeWithTag("reader-quick-settings").assertIsDisplayed()
        compose.onNodeWithTag("reader-quick-settings-line-height").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reader-quick-settings-paragraph-spacing").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reader-quick-settings-page-method").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reader-quick-settings-theme-brightness").performScrollTo().assertIsDisplayed()
        capturePublicScreenshot("appearance")
        compose.onNodeWithText("取消").performClick()

        openReaderMenu()
        compose.onNodeWithTag("reader-menu-notes").performClick()
        compose.onNodeWithText("笔记").assertIsDisplayed()
        compose.onNodeWithText("当前书籍还没有笔记", substring = true).assertIsDisplayed()
        capturePublicScreenshot("notes")
        compose.onNodeWithText("关闭").performClick()

        openReaderMenu()
        compose.onNodeWithTag("reader-menu-more").performClick()
        compose.onNodeWithText("目录管理").assertIsDisplayed()
        compose.onNodeWithText("定位进度").assertIsDisplayed()
        compose.onNodeWithText("触控锁").assertIsDisplayed()
    }

    private fun ensurePublicFixtureImported() {
        compose.onNodeWithTag("nav_library", useUnmergedTree = true).performClick()
        if (compose.onAllNodesWithText(FIXTURE_TITLE).fetchSemanticsNodes().isNotEmpty()) return
        val importTag = if (compose.onAllNodesWithTag("library_empty_import").fetchSemanticsNodes().isNotEmpty()) {
            "library_empty_import"
        } else {
            "library_import"
        }
        compose.onNodeWithTag(importTag).performClick()
        compose.onNodeWithText("选择 TXT 文件").performClick()
        selectFromDocumentsUi(FIXTURE_FILE_NAME)
        check(device.wait(Until.hasObject(By.pkg(targetPackage)), 10_000)) {
            "app did not resume after TXT selection"
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("导入成功").fetchSemanticsNodes().isNotEmpty()
        }
        device.pressBack()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(FIXTURE_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun selectFromDocumentsUi(fileName: String) {
        check(device.wait(Until.hasObject(DOCUMENTS_PACKAGE), 10_000)) {
            "DocumentsUI did not open"
        }
        val result = By.res("android:id/title").text(fileName)
        var file = device.wait(Until.findObject(result), 2_000)
        if (file == null) {
            openDownloads()
            file = device.wait(Until.findObject(result), 5_000)
        }
        if (file == null) {
            val existingQuery =
                device.findObject(By.res("com.google.android.documentsui:id/search_src_text"))
                    ?: device.findObject(By.res("com.android.documentsui:id/search_src_text"))
            val searchOpened = existingQuery != null ||
                clickFresh(By.desc("Search"), 2_000) ||
                clickFresh(By.desc("搜索"), 2_000)
            val query = existingQuery ?: if (searchOpened) {
                device.wait(
                    Until.findObject(By.res("com.google.android.documentsui:id/search_src_text")),
                    3_000,
                ) ?: device.wait(
                    Until.findObject(By.res("com.android.documentsui:id/search_src_text")),
                    1_000,
                )
            } else {
                null
            }
            if (query != null) {
                query.text = fileName
                device.executeShellCommand("input keyevent 66")
                file = device.wait(Until.findObject(result), 15_000)
            }
        }
        if (file == null) file = device.wait(Until.findObject(By.text(fileName)), 3_000)
        check(file != null) { "public fixture missing from DocumentsUI: $fileName" }
        check(clickFresh(result, 2_000) || clickFresh(By.text(fileName), 5_000)) {
            "public fixture could not be selected"
        }
    }

    private fun clickFresh(selector: BySelector, timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        do {
            val candidate = device.findObject(selector)
            if (candidate != null && runCatching {
                    val bounds = candidate.visibleBounds
                    device.click(bounds.centerX(), bounds.centerY())
                }.getOrDefault(false)
            ) return true
            Thread.sleep(100)
        } while (System.currentTimeMillis() < deadline)
        return false
    }

    private fun openDownloads() {
        clickFresh(By.desc("Show roots"), 1_500) ||
            clickFresh(By.desc("显示根目录"), 1_500)
        val downloads = By.res("android:id/title").text(Pattern.compile("Downloads|下载"))
        clickFresh(downloads, 3_000) ||
            clickFresh(By.text(Pattern.compile("Downloads|下载")), 3_000)
    }

    private fun openReaderMenu() {
        if (compose.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isNotEmpty()) return
        val bounds = compose.onNodeWithTag("reader_root").fetchSemanticsNode().boundsInRoot
        check(device.click(bounds.center.x.toInt(), bounds.center.y.toInt())) {
            "failed to tap reader menu zone"
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun capturePublicScreenshot(name: String) {
        val path = "/sdcard/Download/xinyue-v14-$name-api${Build.VERSION.SDK_INT}.png"
        device.executeShellCommand("screencap -p $path")
    }

    private companion object {
        const val FIXTURE_FILE_NAME = "sample-novel.txt"
        const val FIXTURE_TITLE = "sample-novel"
        val MENU_TAGS = listOf(
            "reader-menu-back",
            "reader-menu-bookmark",
            "reader-menu-more",
            "reader-menu-navigation",
            "reader-menu-search",
            "reader-menu-speech",
            "reader-menu-appearance",
            "reader-menu-notes",
        )
        val DOCUMENTS_PACKAGE = By.pkg(Pattern.compile("com\\.(google\\.android|android)\\.documentsui"))
    }
}
