package com.xinyue.reader.benchmark

import android.graphics.Rect
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
            device.pressBack()
            device.waitForIdle()
            backPresses += 1
        }
        require(device.wait(Until.hasObject(By.text("导入")), 5_000) || isLibraryVisible()) {
            "XinYue did not return to the library"
        }
    }

    fun ensurePublicFixtureImported() {
        if (device.hasObject(By.text(FIXTURE_NAME))) return
        clickText("导入")
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
        require(device.wait(Until.hasObject(By.textContains("全书")), UI_TIMEOUT_MILLIS)) {
            "Public fixture did not open in the reader"
        }
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
        val searchButton = device.findObjects(By.text("搜索")).firstOrNull(UiObject2::isClickable)
            ?: error("Reader search submit action is missing")
        searchButton.click()
        require(device.wait(Until.hasObject(By.textContains(query)), UI_TIMEOUT_MILLIS)) {
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
        clickText("设置")
        val fontLabel = device.wait(Until.findObject(By.textStartsWith("字号 ")), 5_000)
            ?: error("Reader font-size setting is missing")
        val labelBounds: Rect = fontLabel.visibleBounds
        val sliderY = labelBounds.bottom + 56
        device.swipe(
            device.displayWidth * 2 / 5,
            sliderY,
            device.displayWidth * 3 / 5,
            sliderY,
            12,
        )
        clickText("应用")
        device.waitForIdle()
    }

    private fun openReaderControls() {
        val menuTarget = device.wait(Until.findObject(By.descContains("菜单")), 5_000)
        if (menuTarget != null) {
            menuTarget.click()
        } else {
            device.click(device.displayWidth / 2, device.displayHeight / 2)
        }
        require(device.wait(Until.hasObject(By.text("设置")), 5_000)) {
            "Reader controls did not open"
        }
    }

    private fun isLibraryVisible(): Boolean =
        device.hasObject(By.text("导入")) || device.hasObject(By.text(FIXTURE_NAME))

    private fun clickText(text: String) {
        val target = device.wait(Until.findObject(By.text(text)), UI_TIMEOUT_MILLIS)
            ?: error("UI action is missing: $text")
        target.click()
        device.waitForIdle()
    }
}
