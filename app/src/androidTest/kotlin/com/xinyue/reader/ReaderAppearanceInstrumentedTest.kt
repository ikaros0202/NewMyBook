package com.xinyue.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderAppearanceInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val targetPackage: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun focusBandAndBoundedProgressPreviewWorkInTheRealReader() {
        ensurePublicFixtureImported()
        openFixture()

        openReaderMenu()
        compose.onNodeWithTag("reader-menu-more").performClick()
        compose.onNodeWithText("定位进度").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("样例正文", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                runCatching { compose.onNodeWithTag("reader-progress-preview-snippet").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("reader-progress-preview-snippet").assertIsDisplayed()
        compose.onNodeWithTag("reader-progress-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(0.8f) }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("返回原位置").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("返回原位置").assertIsDisplayed()
        compose.onNodeWithText("返回原位置").performClick()

        openReaderMenu()
        compose.onNodeWithTag("reader-menu-appearance").performClick()
        compose.onNodeWithTag("reader-quick-settings-more").performClick()
        compose.onNodeWithTag("reader-settings-advanced").performScrollTo().performClick()
        val focusBandLines = compose.onAllNodesWithText("专注带行数", substring = true)
        if (focusBandLines.fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("reader-focus-band-toggle").performScrollTo().performClick()
            compose.onNodeWithText("专注带行数", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("保存").performClick()
        } else {
            focusBandLines[0].performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("取消").performClick()
        }

        openReaderMenu()
        compose.onNodeWithTag("reader-menu-appearance").performClick()
        compose.onNodeWithTag("reader-quick-settings-more").performClick()
        compose.onNodeWithTag("reader-settings-advanced").performScrollTo().performClick()
        compose.onNodeWithText("专注带行数", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
    }

    @Test
    fun importExternalFontAndSelectIt() {
        ensurePublicFixtureImported()
        device.executeShellCommand(
            "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE " +
                "-d file:///sdcard/Download/$FONT_FILE_NAME",
        )
        openFixture()
        openReaderMenu()
        compose.onNodeWithTag("reader-menu-appearance").performClick()
        compose.onNodeWithTag("reader-quick-settings-more").performClick()
        compose.onNodeWithText("导入字体").performScrollTo().performClick()

        selectFromDocumentsUi(FONT_FILE_NAME)
        check(device.wait(Until.hasObject(By.pkg(targetPackage)), 10_000)) {
            "app did not resume after font selection"
        }
        try {
            compose.waitUntil(timeoutMillis = 30_000) {
                compose.onAllNodesWithText(FONT_FILE_NAME).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            error("font import did not publish into settings:\n${compose.onRoot().printToString()}")
        }
        compose.onAllNodesWithText(FONT_FILE_NAME)[0].performScrollTo().performClick()
        compose.onNodeWithText("保存").performClick()
    }

    @Test
    fun importedFontRemainsAvailableAfterExternalCopyWasRemoved() {
        assumeTrue(
            "仅在保留上一轮应用数据的显式设备恢复验证中运行",
            InstrumentationRegistry.getArguments().getString("fontPersistence") == "true",
        )
        openFixture()
        openReaderMenu()
        compose.onNodeWithTag("reader-menu-appearance").performClick()
        compose.onNodeWithTag("reader-quick-settings-more").performClick()
        compose.onNodeWithText("✓ $FONT_FILE_NAME")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
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
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("导入成功").fetchSemanticsNodes().isNotEmpty()
        }
        device.pressBack()
    }

    private fun selectFromDocumentsUi(fileName: String) {
        val result = By.res("android:id/title").text(fileName)
        var file = device.wait(Until.findObject(result), 2_000)
        if (file == null) {
            val rootsOpened = clickFresh(By.desc("Show roots"), 2_000) ||
                clickFresh(By.desc("显示根目录"), 2_000)
            if (rootsOpened) {
                file = enterDownloadsAndWaitFor(result)
            }
        }
        if (file == null) {
            val searchOpened = clickFresh(By.desc("Search"), 2_000) ||
                clickFresh(By.desc("搜索"), 2_000)
            val query = if (searchOpened) {
                device.wait(
                    Until.findObject(By.res("com.google.android.documentsui:id/search_src_text")),
                    3_000,
                )
            } else null
            if (query != null) {
                check(fileName.matches(Regex("[A-Za-z0-9._-]+")))
                query.text = fileName
                check(
                    device.wait(
                        Until.hasObject(
                            By.res("com.google.android.documentsui:id/search_src_text").text(fileName),
                        ),
                        2_000,
                    ),
                ) { "DocumentsUI search text was not committed" }
                device.executeShellCommand("input keyevent 66")
            }
            file = if (query != null) device.wait(Until.findObject(result), 15_000) else null
        }
        if (file == null) {
            file = device.wait(Until.findObject(By.text(fileName)), 3_000)
        }
        check(file != null) {
            val header = device.findObject(
                By.res("com.google.android.documentsui:id/header_title"),
            )?.text.orEmpty()
            "public test file missing from DocumentsUI: package=${device.currentPackageName}, " +
                "header=$header, searchButton=${device.hasObject(By.desc("Search"))}, " +
                "searchInput=${device.hasObject(By.res("com.google.android.documentsui:id/search_src_text"))}, " +
                "titleText=${device.hasObject(By.text(fileName))}, " +
                "preview=${device.hasObject(By.desc("Preview the file $fileName"))}, " +
                "noItems=${device.hasObject(By.text("No items"))}"
        }
        check(clickFresh(result, 2_000) || clickFresh(By.text(fileName), 5_000)) {
            "public test file could not be selected"
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

    private fun enterDownloadsAndWaitFor(result: BySelector): androidx.test.uiautomator.UiObject2? {
        val deadline = System.currentTimeMillis() + 15_000
        do {
            device.findObject(result)?.let { return it }
            val downloads = device.findObject(By.text("Downloads"))
                ?: device.findObject(By.text("下载"))
            if (downloads != null) {
                runCatching {
                    val bounds = downloads.visibleBounds
                    device.click(bounds.centerX(), bounds.centerY())
                }
            }
            device.wait(Until.findObject(result), 500)?.let { return it }
            val list = device.findObject(By.res("com.google.android.documentsui:id/dir_list"))
            if (list?.isScrollable == true) runCatching { list.scroll(Direction.DOWN, 0.8f) }
        } while (System.currentTimeMillis() < deadline)
        return null
    }

    private fun openFixture() {
        compose.onNodeWithText(FIXTURE_TITLE).performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            runCatching { compose.onNodeWithTag("reader_root").assertIsDisplayed() }.isSuccess
        }
    }

    private fun openReaderMenu() {
        if (compose.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isNotEmpty()) return
        val bounds = compose.onNodeWithTag("reader_root").fetchSemanticsNode().boundsInRoot
        check(device.click(bounds.center.x.toInt(), bounds.center.y.toInt())) {
            "failed to tap reader menu zone"
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val FIXTURE_FILE_NAME = "sample-novel.txt"
        const val FIXTURE_TITLE = "sample-novel"
        const val FONT_FILE_NAME = "xinyue-test-font.ttc"
    }
}
