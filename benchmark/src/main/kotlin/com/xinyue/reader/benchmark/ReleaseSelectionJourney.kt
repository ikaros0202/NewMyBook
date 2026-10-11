package com.xinyue.reader.benchmark

import android.graphics.Rect
import android.graphics.Color
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

private const val RELEASE_SELECTION_TIMEOUT = 30_000L
private const val PUBLIC_FIXTURE_FILE = "sample-novel.txt"
private const val PUBLIC_FIXTURE_TITLE = "sample-novel"

class ReleaseSelectionJourney {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val noteText = "release-like note ${SystemClock.uptimeMillis()}"

    fun launchPublicSession() {
        val user = device.executeShellCommand("am get-current-user").trim().toInt()
        val users = device.executeShellCommand("pm list users")
        require(user != 0 && "UserInfo{$user:NewMyBook_Public_QA:" in users) {
            "Run selection verification in the existing NewMyBook_Public_QA user; existing libraries must not be cleared"
        }
        device.executeShellCommand("am force-stop --user $user $PACKAGE_NAME")
        device.executeShellCommand("am start --user $user -W -n $PACKAGE_NAME/com.xinyue.reader.MainActivity")
        require(device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), RELEASE_SELECTION_TIMEOUT)) {
            "Release-like XinYue did not reach the foreground"
        }
    }

    fun assertNonDebuggableTarget() {
        val packageDump = device.executeShellCommand("dumpsys package $PACKAGE_NAME")
        require("pkgFlags=[" in packageDump) { "Target package dump is missing pkgFlags" }
        require("DEBUGGABLE" !in packageDump) { "Release-like target is debuggable" }
    }

    fun importAndOpenPublicFixture() {
        if (device.wait(Until.hasObject(By.text(PUBLIC_FIXTURE_TITLE)), 2_000)) {
            clickText(PUBLIC_FIXTURE_TITLE)
            openFirstChapter()
            return
        }
        clickLibraryImport(device)
        clickText("选择 TXT 文件")
        selectPublicFixtureFromDocumentsUi()
        require(device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), RELEASE_SELECTION_TIMEOUT)) {
            "XinYue did not resume after DocumentsUI"
        }
        require(device.wait(Until.hasObject(By.text("导入成功")), RELEASE_SELECTION_TIMEOUT)) {
            "Public fixture import did not finish"
        }
        device.pressBack()
        clickText(PUBLIC_FIXTURE_TITLE)
        openFirstChapter()
    }

    fun assertSelectionSuppressesAndRestoresPaging() {
        val initialPage = readerBody().text.orEmpty()
        longPressReaderBody()
        assertPlatformCopyExists()

        device.pressKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN)
        device.waitForIdle()
        require(readerBody().text.orEmpty() == initialPage) {
            "Volume key changed the page while selection was active"
        }
        assertPlatformCopyExists()

        swipeForward()
        require(readerBody().text.orEmpty() == initialPage) {
            "Swipe changed the page while selection was active"
        }

        if (hasPlatformCopyAction()) device.pressBack()
        swipeForward()
        val afterSwipe = waitForPageChange(initialPage)
        device.pressKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN)
        waitForPageChange(afterSwipe)
    }

    fun createAllHighlightColors() {
        val colors = mapOf("黄色高亮" to 0xFFD54F, "绿色高亮" to 0x66BB6A, "蓝色高亮" to 0x64B5F6, "粉色高亮" to 0xF48FB1)
        colors.forEach { (label, color) ->
            longPressReaderBody()
            findSelectionAction(label).click()
            require(device.wait(Until.gone(By.text(label)), 5_000)) {
                "Selection action did not finish: $label"
            }
            assertHighlightVisible(color)
            // Plain highlights are rendered on the page, not listed as notes.
            val highlightedPage = readerBody().text.orEmpty()
            swipeForward()
            val nextPage = waitForPageChange(highlightedPage)
            swipeBackward()
            require(waitForPageChange(nextPage) == highlightedPage) { "Did not return to the highlighted page" }
            assertHighlightVisible(color)
        }
    }

    fun createEditAndJumpToNote() {
        val notePage = readerBody().text.orEmpty()
        longPressReaderBody()
        findSelectionAction("批注").click()
        require(device.wait(Until.hasObject(By.text("添加批注")), 5_000)) {
            "Add-note dialog did not open"
        }
        editField().text = noteText
        clickText("保存")

        openAnnotationsPanel()
        val savedNote = device.wait(Until.findObject(By.textContains(noteText)), 10_000)
            ?: error("Saved note is missing")
        requireNotNull(clickableAncestor(savedNote).findObject(By.text("编辑"))).click()
        require(device.wait(Until.hasObject(By.text("编辑笔记")), 5_000)) {
            "Edit-note dialog did not open"
        }
        editField().text = "$noteText edited"
        clickText("保存")
        require(device.wait(Until.hasObject(By.textContains("$noteText edited")), 10_000)) {
            "Edited note is missing"
        }
        clickText("关闭")

        swipeBackward()
        val awayPage = waitForPageChange(notePage)
        openAnnotationsPanel()
        val noteLocation = device.wait(
            Until.findObject(By.textContains("$noteText edited")),
            10_000,
        ) ?: error("Note location action is missing")
        clickableAncestor(noteLocation).click()
        require(device.wait(Until.gone(By.textContains("$noteText edited")), 5_000)) {
            "Annotation panel did not close after the jump action"
        }
        require(waitForAnnotationJump(awayPage, notePage)) {
            "Annotation jump did not return to text containing the note anchor"
        }
    }

    private fun selectPublicFixtureFromDocumentsUi() {
        val fixtureSelector = By.text(PUBLIC_FIXTURE_FILE).clazz("android.widget.TextView")
        var fixture = device.wait(Until.findObject(fixtureSelector), 3_000)
        if (fixture == null) {
            val search = device.wait(Until.findObject(By.desc("Search")), 5_000)
                ?: device.wait(Until.findObject(By.descContains("搜索")), 3_000)
                ?: error("DocumentsUI search action is missing")
            search.click()
            val query = device.wait(
                Until.findObject(By.res("com.google.android.documentsui:id/search_src_text")),
                5_000,
            ) ?: error("DocumentsUI search field is missing")
            query.text = PUBLIC_FIXTURE_FILE
            fixture = device.wait(Until.findObject(fixtureSelector), 10_000)
        }
        (fixture ?: error("Public fixture is missing from DocumentsUI")).click()
    }

    private fun longPressReaderBody() {
        val bounds: Rect = readerBody().visibleBounds
        val x = bounds.centerX()
        val y = (bounds.top + minOf(220, bounds.height() / 3)).coerceAtMost(bounds.bottom - 1)
        require(device.swipe(x, y, x, y, 150)) { "Could not long-press reader text" }
        require(device.wait(Until.hasObject(By.text("复制")), 3_000) || device.hasObject(By.text("Copy"))) {
            "System selection ActionMode did not open"
        }
    }

    private fun assertPlatformCopyExists() {
        require(hasPlatformCopyAction()) {
            "System copy action is missing"
        }
    }

    private fun hasPlatformCopyAction(): Boolean =
        device.hasObject(By.text("复制")) || device.hasObject(By.text("Copy"))

    private fun findSelectionAction(label: String): UiObject2 {
        device.findObject(By.text(label))?.let { return it }
        val overflow = device.findObject(By.descContains("More"))
            ?: device.findObject(By.descContains("更多"))
            ?: error("Selection ActionMode overflow is missing for $label")
        overflow.click()
        return device.wait(Until.findObject(By.text(label)), 5_000)
            ?: error("Selection action is missing: $label")
    }

    private fun openAnnotationsPanel() {
        val bounds = readerBody().visibleBounds
        device.click(bounds.centerX(), bounds.centerY())
        val annotations = device.wait(Until.findObject(By.text("笔记")), 5_000)
            ?: error("Reader annotations control is missing")
        annotations.click()
        require(device.wait(Until.hasObject(By.text("导出批注")), 5_000)) {
            "Reader annotations panel did not open"
        }
    }

    private fun openFirstChapter() {
        val bounds = readerBody().visibleBounds
        device.click(bounds.centerX(), bounds.centerY())
        clickText("目录")
        clickText("第一章 雨夜来信")
        readerBody()
    }

    private fun assertHighlightVisible(rgb: Int) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            val bounds = readerBody().visibleBounds
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?: error("Could not capture the highlighted page")
            val matched = try {
                val width = bounds.width()
                val pixels = IntArray(width * bounds.height())
                bitmap.getPixels(pixels, 0, width, bounds.left, bounds.top, width, bounds.height())
                val background = pixels.asSequence().filterIndexed { index, _ -> index % 16 == 0 }
                    .groupingBy { it }.eachCount().maxBy { it.value }.key
                fun channel(foreground: Int, behind: Int) = (foreground * 128 + behind * 127 + 127) / 255
                val red = channel(Color.red(rgb), Color.red(background))
                val green = channel(Color.green(rgb), Color.green(background))
                val blue = channel(Color.blue(rgb), Color.blue(background))
                pixels.count {
                    kotlin.math.abs(Color.red(it) - red) <= 2 &&
                        kotlin.math.abs(Color.green(it) - green) <= 2 &&
                        kotlin.math.abs(Color.blue(it) - blue) <= 2
                } >= 10
            } finally { bitmap.recycle() }
            if (matched) return
            SystemClock.sleep(200)
        }
        error("Expected highlight color did not render on the page: ${rgb.toString(16)}")
    }

    private fun editField(): UiObject2 = device.wait(
        Until.findObject(By.clazz("android.widget.EditText")),
        5_000,
    ) ?: error("Note edit field is missing")

    private fun readerBody(): UiObject2 = waitForReaderBody(device, RELEASE_SELECTION_TIMEOUT)

    private fun swipeForward() {
        val bounds = readerBody().visibleBounds
        device.swipe(
            bounds.right - bounds.width() / 5,
            bounds.centerY(),
            bounds.left + bounds.width() / 5,
            bounds.centerY(),
            12,
        )
        device.waitForIdle()
    }

    private fun swipeBackward() {
        val bounds = readerBody().visibleBounds
        device.swipe(
            bounds.left + bounds.width() / 5,
            bounds.centerY(),
            bounds.right - bounds.width() / 5,
            bounds.centerY(),
            12,
        )
        device.waitForIdle()
    }

    private fun waitForPageChange(previous: String): String {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val current = readerBody().text.orEmpty()
            if (current != previous) return current
            SystemClock.sleep(200)
        }
        error("Reader page did not change after paging input")
    }

    private fun clickableAncestor(node: UiObject2): UiObject2 {
        var current: UiObject2? = node
        repeat(4) {
            val candidate = current ?: return node
            if (runCatching { candidate.isClickable }.getOrDefault(false)) return candidate
            current = runCatching { candidate.parent }.getOrNull()
        }
        return node
    }

    private fun waitForAnnotationJump(awayPage: String, notePage: String): Boolean {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val current = readerBody().text.orEmpty()
            if (current != awayPage && sharesStableFragment(current, notePage)) return true
            SystemClock.sleep(200)
        }
        return false
    }

    private fun sharesStableFragment(actual: String, expected: String): Boolean =
        expected.windowed(size = 16, step = 8, partialWindows = false)
            .filter { fragment -> fragment.isNotBlank() }
            .any(actual::contains)

    private fun clickText(text: String) {
        val target = device.wait(Until.findObject(By.text(text)), RELEASE_SELECTION_TIMEOUT)
            ?: error("UI action is missing: $text")
        target.click()
        device.waitForIdle()
    }
}
