package com.xinyue.reader.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val PACKAGE_NAME = "com.xinyue.reader"
private const val FIXTURE_NAME = "sample-novel"
private const val FIXTURE_FILE_NAME = "sample-novel.txt"
private const val UI_TIMEOUT_MILLIS = 15_000L

class XinYueJourneys(private val scope: MacrobenchmarkScope) {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun coldStartToLibrary() {
        scope.pressHome()
        scope.startActivityAndWait()
        require(device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), UI_TIMEOUT_MILLIS)) {
            "XinYue did not reach the foreground"
        }
        var backPresses = 0
        while (!isLibraryVisible() && backPresses < 4) {
            device.findObject(By.desc("书架"))?.let { it.click(); device.waitForIdle() }
            if (isLibraryVisible()) break
            device.pressBack()
            device.waitForIdle()
            backPresses += 1
        }
        require(isLibraryVisible()) {
            "XinYue did not return to the library"
        }
    }

    fun ensurePublicFixtureImported() {
        if (device.hasObject(By.text(FIXTURE_NAME))) return
        clickLibraryImport(device)
        clickText("选择 TXT 文件")

        val fixtureResult = By.text(FIXTURE_FILE_NAME).clazz("android.widget.TextView")
        var fixture = device.wait(Until.findObject(fixtureResult), 2_000)
        if (fixture == null) {
            val search = device.wait(Until.findObject(By.desc("Search")), 3_000)
                ?: error("DocumentsUI search action is missing")
            search.click()
            val query = device.wait(
                Until.findObject(By.res("com.google.android.documentsui:id/search_src_text")),
                3_000,
            ) ?: error("DocumentsUI search field is missing")
            query.text = FIXTURE_FILE_NAME
            fixture = device.wait(Until.findObject(fixtureResult), 5_000)
        }
        (fixture ?: error("Push qa/sample-novel.txt to Downloads before running benchmarks")).click()
        require(device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), UI_TIMEOUT_MILLIS)) {
            "XinYue did not resume after DocumentsUI"
        }
        require(device.wait(Until.hasObject(By.text("导入成功")), UI_TIMEOUT_MILLIS)) {
            "Public fixture import did not finish"
        }
        device.pressBack()
        require(device.wait(Until.hasObject(By.text(FIXTURE_NAME)), UI_TIMEOUT_MILLIS)) {
            "Imported public fixture is missing from the library"
        }
    }

    fun openFixture() {
        clickText(FIXTURE_NAME)
        waitForReaderBody(device, UI_TIMEOUT_MILLIS)
    }

    fun turnPages(count: Int) {
        require(count >= 0) { "Page-turn count must be non-negative" }
        repeat(count) {
            device.swipe(
                device.displayWidth * 4 / 5,
                device.displayHeight / 2,
                device.displayWidth / 5,
                device.displayHeight / 2,
                12,
            )
            device.waitForIdle()
        }
    }

    fun openSearchAndFind(query: String) {
        openReaderControls()
        clickText("搜索")
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)
            ?: error("Reader search field is missing")
        field.text = query
        val searchButton = device.wait(Until.findObject(By.text("搜索")), 5_000)
            ?: error("Reader search submit action is missing")
        searchButton.click()
        require(device.wait(Until.hasObject(By.textContains(query).clazz("android.widget.TextView")), UI_TIMEOUT_MILLIS)) {
            "Reader search did not expose a result for the public query"
        }
    }

    fun returnToLibraryAndScroll() {
        var backPresses = 0
        while (!device.hasObject(By.text(FIXTURE_NAME)) && backPresses < 4) {
            device.pressBack()
            device.waitForIdle()
            backPresses += 1
        }
        require(device.wait(Until.hasObject(By.text(FIXTURE_NAME)), UI_TIMEOUT_MILLIS)) {
            "Could not return to the library"
        }
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 4 / 5,
            device.displayWidth / 2,
            device.displayHeight / 3,
            12,
        )
        device.waitForIdle()
    }

    fun openSettingsAndApplyReflow() {
        openReaderControls()
        clickText("外观")
        val increase = device.wait(Until.findObject(By.desc("增大字号")), 5_000)
            ?: error("Reader font-size action is missing")
        if (increase.isEnabled) increase.click() else {
            requireNotNull(device.findObject(By.desc("减小字号"))).click()
        }
        clickText("完成")
        require(device.wait(Until.gone(By.desc("增大字号")), 5_000)) { "Appearance draft was not saved" }
        device.waitForIdle()
    }

    private fun openReaderControls() {
        val menuTarget = device.wait(Until.findObject(By.descContains("菜单")), 5_000)
        if (menuTarget != null) {
            menuTarget.click()
        } else {
            device.click(device.displayWidth / 2, device.displayHeight / 2)
        }
        require(device.wait(Until.hasObject(By.text("外观")), 5_000)) {
            "Reader controls did not open"
        }
    }

    private fun isLibraryVisible(): Boolean =
        device.hasObject(By.desc("书架").selected(true)) || device.hasObject(By.desc("导入 TXT"))

    private fun clickText(text: String) {
        val target = device.wait(Until.findObject(By.text(text)), UI_TIMEOUT_MILLIS)
            ?: error("UI action is missing: $text")
        target.click()
        device.waitForIdle()
    }
}

internal fun clickLibraryImport(device: UiDevice) {
    val action = device.findObject(By.desc("导入 TXT"))
        ?: device.wait(Until.findObject(By.text("选择 TXT 文件")), UI_TIMEOUT_MILLIS)
        ?: error("Library TXT import action is missing")
    action.click()
    device.waitForIdle()
}

internal fun waitForReaderBody(device: UiDevice, timeoutMillis: Long): UiObject2 {
    val deadline = android.os.SystemClock.uptimeMillis() + timeoutMillis
    while (android.os.SystemClock.uptimeMillis() < deadline) {
        val body = device.findObjects(By.clazz("android.widget.TextView"))
            .mapNotNull { node ->
                runCatching {
                    node.takeIf { it.visibleBounds.width() > device.displayWidth / 2 && it.text.orEmpty().length >= 40 }
                        ?.let { it to it.text.orEmpty().length }
                }.getOrNull()
            }
            .maxByOrNull { it.second }?.first
        if (body != null) return body
        android.os.SystemClock.sleep(200)
    }
    error("Reader body TextView did not become visible")
}
