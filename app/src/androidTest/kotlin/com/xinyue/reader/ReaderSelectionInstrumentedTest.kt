package com.xinyue.reader

import android.content.ClipboardManager
import android.text.Selection
import android.text.Spannable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSelectionInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val targetPackage get() = instrumentation.targetContext.packageName

    @Test
    fun importedFixtureSupportsExactCopyAndHighlight() {
        ensurePublicFixtureImported()
        openFixtureReader()
        enableFocusBand()
        dismissImmersiveConfirmationIfPresent()
        longPressCurrentPage()
        selectExactPublicText(EXPECTED_COPIED_TEXT)
        copySelectionAndAssert(EXPECTED_COPIED_TEXT)
        device.pressBack()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("reader_selection_actions").fetchSemanticsNodes().isEmpty()
        }

        longPressCurrentPage()
        awaitApplicationSelectionActions()
        clickCustomAction("黄色高亮")
        device.wait(Until.gone(By.text("黄色高亮")), 3_000)
        compose.waitForIdle()

        openAnnotationsPanel()
        compose.onNodeWithTag("reader_annotations").assertIsDisplayed()
        val highlights = compose.onAllNodesWithText("高亮 · 位置", substring = true)
        assertTrue("highlight was not persisted", highlights.fetchSemanticsNodes().isNotEmpty())
        highlights[0].assertIsDisplayed()
    }

    @Test
    fun importedFixtureSupportsNote() {
        ensurePublicFixtureImported()
        openFixtureReader()
        dismissImmersiveConfirmationIfPresent()
        longPressCurrentPage()
        awaitApplicationSelectionActions()
        clickCustomAction("批注")
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("reader_selection_actions").fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("添加批注").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("添加批注").assertIsDisplayed()
        compose.onNodeWithText("批注内容").performTextInput("设备批注")
        compose.onNodeWithText("保存").performClick()

        openAnnotationsPanel()
        compose.onNodeWithTag("reader_annotations").assertIsDisplayed()
        val notes = compose.onAllNodesWithText("设备批注")
        assertTrue("note was not persisted", notes.fetchSemanticsNodes().isNotEmpty())
        notes[0].assertIsDisplayed()
    }

    private fun openFixtureReader() {
        compose.onNodeWithText("sample-novel").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                compose.onNodeWithTag("reader_root").assertIsDisplayed()
            }.isSuccess
        }
        compose.onNodeWithTag("reader_root").assertIsDisplayed()
    }

    private fun ensurePublicFixtureImported() {
        if (compose.onAllNodesWithTag("reader_root").fetchSemanticsNodes().isNotEmpty()) {
            device.pressBack()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText("sample-novel").fetchSemanticsNodes().isNotEmpty()
            }
        }
        compose.onNodeWithTag("nav_library", useUnmergedTree = true).performClick()
        if (compose.onAllNodesWithText("sample-novel").fetchSemanticsNodes().isNotEmpty()) return
        val importTag = if (compose.onAllNodesWithTag("library_empty_import").fetchSemanticsNodes().isNotEmpty()) {
            "library_empty_import"
        } else {
            "library_import"
        }
        compose.onNodeWithTag(importTag).performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("选择 TXT 文件").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("选择 TXT 文件").performClick()
        selectPublicFixtureFromDocumentsUi()
        check(
            device.wait(
                Until.hasObject(By.pkg(targetPackage)),
                10_000,
            ),
        ) { "app did not resume after DocumentsUI selection" }
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("导入成功").fetchSemanticsNodes().isNotEmpty()
        }
        device.pressBack()
    }

    private fun selectPublicFixtureFromDocumentsUi() {
        val fixtureResult = By.res("android:id/title").text("sample-novel.txt")
        var file = device.wait(Until.findObject(fixtureResult), 2_000)
        if (file == null) {
            val rootsOpened = clickFresh(By.desc("Show roots"), 2_000) ||
                clickFresh(By.desc("显示根目录"), 2_000)
            if (rootsOpened) {
                file = enterDownloadsAndWaitFor(fixtureResult)
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
                clickFresh(By.res("com.google.android.documentsui:id/search_src_text"), 1_000)
                device.executeShellCommand("input text sample-novel.txt")
                check(
                    device.wait(
                        Until.hasObject(
                            By.res("com.google.android.documentsui:id/search_src_text")
                                .text("sample-novel.txt"),
                        ),
                        2_000,
                    ),
                ) { "DocumentsUI search text was not committed" }
                device.executeShellCommand("input keyevent 66")
            }
            file = if (query != null) {
                device.wait(Until.findObject(fixtureResult), 15_000)
            } else null
        }
        check(file != null && clickFresh(fixtureResult, 5_000)) {
            "public fixture missing from DocumentsUI"
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

    private fun enterDownloadsAndWaitFor(result: BySelector): UiObject2? {
        val deadline = System.currentTimeMillis() + 5_000
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
        } while (System.currentTimeMillis() < deadline)
        return null
    }

    private fun selectExactPublicText(expected: String) {
        var crossedVisualLine = false
        var actualSelection = ""
        instrumentation.runOnMainSync {
            val selectable = compose.activity.window.decorView.findSelectedTextView(expected)
                ?: error("active selectable reader text was not found")
            val start = selectable.text.toString().indexOf(expected)
            check(start >= 0) { "public copy marker is not visible on the current page" }
            val end = start + expected.length
            val layout = selectable.layout ?: error("reader text layout is unavailable")
            crossedVisualLine = layout.getLineForOffset(start) != layout.getLineForOffset(end - 1)
            Selection.setSelection(selectable.text as Spannable, start, end)
            actualSelection = selectable.text.substring(selectable.selectionStart, selectable.selectionEnd)
        }
        assertTrue("exact public selection must cross a visual line", crossedVisualLine)
        assertEquals("active TextView selection must match the public fixture", expected, actualSelection)
    }

    private fun copySelectionAndAssert(expected: String) {
        val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
        var copied = false
        instrumentation.runOnMainSync {
            val selectable = compose.activity.window.decorView.findSelectedTextView(expected)
                ?: error("active selectable reader text was not found for copy")
            copied = selectable.onTextContextMenuItem(android.R.id.copy)
        }
        assertTrue("Android TextView system copy action must succeed", copied)
        val deadline = System.currentTimeMillis() + 3_000
        var actual = ""
        do {
            actual = clipboard.primaryClip?.getItemAt(0)?.coerceToText(compose.activity)?.toString().orEmpty()
            if (actual == expected) break
            Thread.sleep(100)
        } while (System.currentTimeMillis() < deadline)
        assertEquals("system copy must preserve the exact public selection", expected, actual)
    }

    private fun View.findSelectedTextView(expected: String): TextView? {
        if (
            this is TextView &&
            isShown &&
            selectionStart >= 0 &&
            selectionEnd >= 0 &&
            selectionStart != selectionEnd &&
            text.toString().contains(expected)
        ) return this
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) {
            getChildAt(index).findSelectedTextView(expected)?.let { return it }
        }
        return null
    }

    private fun View.findReaderTextView(expected: String): TextView? {
        if (this is TextView && isShown && text.toString().contains(expected)) return this
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) {
            getChildAt(index).findReaderTextView(expected)?.let { return it }
        }
        return null
    }

    private fun longPressCurrentPage() {
        var x = 0
        var y = 0
        instrumentation.runOnMainSync {
            val selectable = compose.activity.window.decorView.findReaderTextView(EXPECTED_COPIED_TEXT)
                ?: error("visible reader text was not found for long press")
            val offset = selectable.text.toString().indexOf(EXPECTED_COPIED_TEXT) + 2
            val layout = selectable.layout ?: error("reader text layout is unavailable for long press")
            val line = layout.getLineForOffset(offset)
            val location = IntArray(2)
            selectable.getLocationOnScreen(location)
            x = location[0] + selectable.totalPaddingLeft + layout.getPrimaryHorizontal(offset).toInt()
            y = location[1] + selectable.totalPaddingTop +
                (layout.getLineTop(line) + layout.getLineBottom(line)) / 2 - selectable.scrollY
        }
        device.executeShellCommand("input touchscreen swipe $x $y $x $y 1200")
    }

    private fun findCustomAction(label: String): UiObject2 {
        device.wait(Until.findObject(By.text(label)), 1_500)?.let { return it }
        device.wait(Until.findObject(By.desc(label)), 1_500)?.let { return it }
        val overflow = device.findObject(By.descContains("More"))
            ?: device.findObject(By.descContains("更多"))
            ?: error("selection ActionMode overflow missing for $label")
        overflow.click()
        return device.wait(Until.findObject(By.text(label)), 3_000)
            ?: device.wait(Until.findObject(By.desc(label)), 3_000)
            ?: error("selection action missing: $label")
    }

    private fun clickCustomAction(label: String) {
        val composeActions = compose.onAllNodesWithText(label)
        if (composeActions.fetchSemanticsNodes().isNotEmpty()) {
            composeActions[0].performClick()
            return
        }
        findCustomAction(label).click()
    }

    private fun awaitApplicationSelectionActions() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("reader_selection_actions").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("reader_selection_actions").assertIsDisplayed()
    }

    private fun dismissImmersiveConfirmationIfPresent() {
        val confirmation = device.wait(Until.findObject(By.text("Viewing full screen")), 1_500)
            ?: return
        val gotIt = device.wait(Until.findObject(By.text("GOT IT")), 2_000)
            ?: device.wait(Until.findObject(By.text("知道了")), 500)
            ?: device.wait(Until.findObject(By.text("我知道了")), 500)
        val dismissed = if (gotIt != null) {
            val bounds = gotIt.visibleBounds
            device.click(bounds.centerX(), bounds.centerY())
        } else {
            device.swipe(
                device.displayWidth / 2,
                confirmation.visibleBounds.top,
                device.displayWidth / 2,
                device.displayHeight / 3,
                30,
            )
        }
        check(dismissed) { "failed to interact with the platform immersive confirmation" }
        check(device.wait(Until.gone(By.text("Viewing full screen")), 3_000)) {
            "platform immersive confirmation remained above the reader"
        }
    }

    private fun openAnnotationsPanel() {
        repeat(2) {
            if (compose.onAllNodesWithTag("reader-menu-notes").fetchSemanticsNodes().isNotEmpty()) return@repeat
            val bounds = compose.onAllNodesWithTag("reader_page_surface")
                .fetchSemanticsNodes()[0]
                .boundsInRoot
            check(device.click(bounds.center.x.toInt(), bounds.center.y.toInt())) {
                "failed to tap reader menu zone"
            }
            runCatching {
                compose.waitUntil(timeoutMillis = 3_000) {
                    compose.onAllNodesWithTag("reader-menu-notes").fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
        compose.onNodeWithTag("reader-menu-notes").assertIsDisplayed()
        compose.onNodeWithTag("reader-menu-notes").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithTag("reader_annotations").assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun enableFocusBand() {
        val bounds = compose.onNodeWithTag("reader_root").fetchSemanticsNode().boundsInRoot
        check(device.click(bounds.center.x.toInt(), bounds.center.y.toInt())) {
            "failed to open reader controls"
        }
        compose.onNodeWithTag("reader-menu-appearance").performClick()
        compose.onNodeWithTag("reader-quick-settings-more").performClick()
        compose.onNodeWithTag("reader-settings-advanced").performScrollTo().performClick()
        val enabledControls = compose.onAllNodesWithText("专注带行数", substring = true)
        if (enabledControls.fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("reader-focus-band-toggle").performScrollTo().performClick()
            compose.onNodeWithText("保存").performClick()
        } else {
            compose.onNodeWithText("取消").performClick()
        }
        compose.waitForIdle()
        check(device.click(bounds.center.x.toInt(), bounds.center.y.toInt())) {
            "failed to dismiss reader controls after focus-band setup"
        }
        compose.waitForIdle()
    }

    private companion object {
        const val EXPECTED_COPIED_TEXT = "夜色沿着旧城的屋檐缓慢落下，雨水敲在窗上"
    }
}
