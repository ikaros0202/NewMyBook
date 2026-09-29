package com.xinyue.reader.benchmark

import android.graphics.Rect
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
private const val PUBLIC_FIXTURE_DEVICE_PATH = "/sdcard/Download/sample-novel.txt"

class ReleaseSelectionJourney {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun resetAndLaunch() {
        val fixtureCheck = device.executeShellCommand("ls $PUBLIC_FIXTURE_DEVICE_PATH").trim()
        require(fixtureCheck == PUBLIC_FIXTURE_DEVICE_PATH) {
            "Push qa/sample-novel.txt to Downloads before running ReleaseSelectionTest"
        }
        device.executeShellCommand("pm clear $PACKAGE_NAME")
        device.executeShellCommand("monkey -p $PACKAGE_NAME 1")
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
        clickText("导入")
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
        readerBody()
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
        val labels = listOf("黄色高亮", "绿色高亮", "蓝色高亮", "粉色高亮")
        labels.forEach { label ->
            longPressReaderBody()
            findSelectionAction(label).click()
            require(device.wait(Until.gone(By.text(label)), 5_000)) {
                "Selection action did not finish: $label"
            }
        }

        openAnnotationsPanel()
        val highlights = device.wait(
            Until.findObjects(By.textStartsWith("高亮 · 位置")),
            10_000,
        ).orEmpty()
        require(highlights.size >= labels.size) {
            "Expected four saved highlights, found ${highlights.size}"
        }
        clickText("关闭")
    }

    fun createEditAndJumpToNote() {
        val notePage = readerBody().text.orEmpty()
        longPressReaderBody()
        findSelectionAction("批注").click()
        require(device.wait(Until.hasObject(By.text("添加批注")), 5_000)) {
            "Add-note dialog did not open"
        }
        editField().text = "release-like note"
        clickText("保存")

        openAnnotationsPanel()
        require(device.wait(Until.hasObject(By.text("release-like note")), 10_000)) {
            "Saved note is missing"
        }
        clickText("编辑")
        require(device.wait(Until.hasObject(By.text("编辑批注")), 5_000)) {
            "Edit-note dialog did not open"
        }
        editField().text = "release-like note edited"
        clickText("保存")
        require(device.wait(Until.hasObject(By.text("release-like note edited")), 10_000)) {
            "Edited note is missing"
        }
        clickText("关闭")

        swipeBackward()
        val awayPage = waitForPageChange(notePage)
        openAnnotationsPanel()
        val noteLocation = device.wait(
            Until.findObject(By.textStartsWith("批注 · 位置")),
            10_000,
        ) ?: error("Note location action is missing")
        clickableAncestor(noteLocation).click()
        require(device.wait(Until.gone(By.text("release-like note edited")), 5_000)) {
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
        val annotations = device.wait(Until.findObject(By.text("标注")), 5_000)
            ?: error("Reader annotations control is missing")
        annotations.click()
        require(device.wait(Until.hasObject(By.text("全部")), 5_000) || device.hasObject(By.text("• 全部"))) {
            "Reader annotations panel did not open"
        }
    }

    private fun editField(): UiObject2 = device.wait(
        Until.findObject(By.clazz("android.widget.EditText")),
        5_000,
    ) ?: error("Note edit field is missing")

    private fun readerBody(): UiObject2 {
        val deadline = SystemClock.uptimeMillis() + RELEASE_SELECTION_TIMEOUT
        while (SystemClock.uptimeMillis() < deadline) {
            val candidate = device.findObjects(By.clazz("android.widget.TextView"))
                .mapNotNull { node ->
                    runCatching {
                        node.takeIf {
                            it.visibleBounds.width() > device.displayWidth / 2 &&
                                it.text.orEmpty().length >= 40
                        }
                    }.getOrNull()
                }
                .maxByOrNull { node -> runCatching { node.text.orEmpty().length }.getOrDefault(0) }
            if (candidate != null) return candidate
            SystemClock.sleep(200)
        }
        error("Reader body TextView did not become visible")
    }

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
