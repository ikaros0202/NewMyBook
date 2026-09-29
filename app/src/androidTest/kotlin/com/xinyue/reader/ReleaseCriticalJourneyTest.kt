package com.xinyue.reader

import android.content.Context
import android.widget.EditText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.xinyue.reader.core.data.BackupCatalogSnapshot
import com.xinyue.reader.core.data.BackupEquivalenceVerifier
import com.xinyue.reader.core.data.RestoreDatabaseGateway
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseCriticalJourneyTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    private val context: Context get() = instrumentation.targetContext.applicationContext
    private val targetPackage: String get() = instrumentation.targetContext.packageName
    private val gateway: RestoreDatabaseGateway
        get() = EntryPointAccessors.fromApplication(context, BackupDebugEntryPoint::class.java)
            .restoreDatabaseGateway()

    @Test
    fun publicFixtureCompletesReleaseCriticalJourneyAndBackupRoundTrip() {
        compose.onNodeWithTag("nav_library", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("library_root").assertIsDisplayed()
        importPublicFixtureThroughDocumentsUi()
        compose.onNodeWithText(FIXTURE_TITLE).assertIsDisplayed()
        compose.onNodeWithContentDescription("《$FIXTURE_TITLE》的默认封面").assertIsDisplayed()

        createGroupAndExerciseFinishedStatistics()
        openFixtureFromCurrentLibraryView()
        exerciseReaderNavigationAndAppearance()
        addHighlightAndNoteThroughPlatformSelection()
        returnToLibrary()
        exportClearAndRestoreEquivalentLibrary()
    }

    private fun importPublicFixtureThroughDocumentsUi() {
        if (compose.onAllNodesWithText(FIXTURE_TITLE).fetchSemanticsNodes().isNotEmpty()) return
        val importTag = if (compose.onAllNodesWithTag("library_empty_import").fetchSemanticsNodes().isNotEmpty()) {
            "library_empty_import"
        } else {
            "library_import"
        }
        compose.onNodeWithTag(importTag).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("选择 TXT 文件").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("选择 TXT 文件").performClick()
        selectFromDocumentsUi(FIXTURE_FILE_NAME)
        awaitApp()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("导入成功").fetchSemanticsNodes().isNotEmpty()
        }
        device.pressBack()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(FIXTURE_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun createGroupAndExerciseFinishedStatistics() {
        compose.onNodeWithText("新建分组").performScrollTo().performClick()
        compose.onNodeWithText("分组名称").performTextInput(GROUP_NAME)
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(GROUP_NAME).fetchSemanticsNodes().isNotEmpty()
        }

        enterBookSelection()
        compose.onNodeWithText("移动到").performClick()
        clickPopupText(GROUP_NAME)
        awaitSelectionActionFinished()

        enterBookSelection()
        compose.onNodeWithText("标记已读完").performScrollTo().performClick()
        awaitSelectionActionFinished()
        compose.onNodeWithTag("library-status-finished").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(FIXTURE_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(FIXTURE_TITLE).assertIsDisplayed()

        compose.onNodeWithContentDescription("《$FIXTURE_TITLE》更多操作").performClick()
        clickPopupText("统计")
        compose.onNodeWithTag("statistics_root").assertIsDisplayed()
        compose.onNodeWithText("日").assertIsDisplayed()
        compose.onNodeWithText("周").assertIsDisplayed()
        compose.onNodeWithText("月").assertIsDisplayed()
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithTag("library_root").assertIsDisplayed()
    }

    private fun enterBookSelection() {
        compose.onNodeWithText(FIXTURE_TITLE).performTouchInput { longClick() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("library-selection-bar").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("library-selection-bar").assertIsDisplayed()
    }

    private fun awaitSelectionActionFinished() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("library-selection-bar").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun openFixtureFromCurrentLibraryView() {
        compose.onNodeWithText(FIXTURE_TITLE).performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("reader_root").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("reader_root").assertIsDisplayed()
        dismissImmersiveConfirmationIfPresent()
    }

    private fun exerciseReaderNavigationAndAppearance() {
        openReaderMenu()
        MENU_TAGS.forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
        }

        compose.onNodeWithTag("reader-menu-navigation").performClick()
        compose.onNodeWithTag("reader-navigation-drawer", useUnmergedTree = true).assertExists()
        val drawerChapterNodes = compose.onAllNodes(
            hasText("第一章 雨夜来信") and hasAnyAncestor(hasTestTag("reader-navigation-drawer")),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        check(drawerChapterNodes.isNotEmpty()) { "first chapter is missing from the navigation drawer" }
        device.pressBack()

        openReaderMenu()
        openProgressPanel()
        awaitTag("reader-progress-preview-snippet", 10_000, "initial progress preview")
        compose.onNodeWithTag("reader-progress-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        awaitText("返回原位置", 10_000, "middle temporary progress jump")
        compose.onNodeWithText("从这里继续").performClick()

        openReaderMenu()
        openProgressPanel()
        awaitTag("reader-progress-preview-snippet", 10_000, "middle progress preview")
        compose.onNodeWithText("取消").performClick()

        openReaderMenu()
        openProgressPanel()
        awaitTag("reader-progress-preview-snippet", 10_000, "progress preview before near-end jump")
        compose.onNodeWithTag("reader-progress-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.9f) }
        awaitText("返回原位置", 10_000, "near-end temporary progress jump")
        compose.onNodeWithText("返回原位置").performClick()

        openReaderMenu()
        compose.onNodeWithTag("reader-menu-search").performClick()
        compose.onNodeWithText("关键词").performTextInput("星河")
        compose.waitForIdle()
        compose.onNodeWithTag("reader-search-submit").performClick()
        awaitTag("reader-search-result", 60_000, "indexed search result")
        val results = compose.onAllNodesWithTag("reader-search-result")
        results[results.fetchSemanticsNodes().lastIndex].performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("返回原位置").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("返回原位置").performClick()

        openReaderMenu()
        compose.onNodeWithTag("reader-menu-bookmark").performClick()
        compose.onNodeWithTag("reader-menu-navigation").performClick()
        compose.onNodeWithText("书签").performClick()
        compose.onNodeWithText("当前书籍还没有书签").let { node ->
            check(runCatching { node.assertIsDisplayed() }.isFailure) { "bookmark was not persisted" }
        }
        device.pressBack()

        openReaderMenu()
        compose.onNodeWithTag("reader-menu-appearance").performClick()
        compose.onNodeWithTag("reader-quick-settings-font-size").assertIsDisplayed()
        compose.onNodeWithTag("reader-quick-settings-more").performScrollTo().performClick()
        awaitTag("reader-settings-full", 5_000, "full appearance settings")
        compose.onNodeWithTag("reader-settings-common").performScrollTo().assertIsDisplayed()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("仅本书", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("仅本书", substring = true).performScrollTo().performClick()
        awaitText("本书：$FIXTURE_TITLE", 5_000, "per-book appearance status")
        compose.onNodeWithContentDescription("字号", substring = true)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(22f) }
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("阅读外观").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun addHighlightAndNoteThroughPlatformSelection() {
        hideReaderMenu()
        longPressCurrentPage()
        awaitSelectionActions()
        clickSelectionAction("黄色高亮")
        device.wait(Until.gone(By.text("黄色高亮")), 3_000)
        compose.waitForIdle()

        longPressCurrentPage()
        awaitSelectionActions()
        clickSelectionAction("批注")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("添加批注").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("批注内容").performTextInput(NOTE_TEXT)
        compose.onNodeWithText("保存").performClick()

        openReaderMenu()
        compose.onNodeWithTag("reader-menu-notes").performClick()
        compose.onNodeWithTag("reader_annotations").assertIsDisplayed()
        compose.onNodeWithText("批注 · 位置", substring = true).assertIsDisplayed()
        compose.onNodeWithText(NOTE_TEXT).assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()

        val bounds = currentPageBounds()
        check(device.swipe(bounds.center.x.toInt(), bounds.center.y.toInt(), bounds.left.toInt(), bounds.center.y.toInt(), 18)) {
            "page swipe failed after selection dismissal"
        }
        compose.onNodeWithTag("reader_root").assertIsDisplayed()
    }

    private fun returnToLibrary() {
        openReaderMenu()
        compose.onNodeWithTag("reader-menu-back").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("library_root").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun exportClearAndRestoreEquivalentLibrary() {
        val expected = runBlocking { BackupEquivalenceVerifier(context.filesDir).capture(gateway.snapshot()) }
        compose.onNodeWithTag("nav_settings", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_backup"))
        compose.onNodeWithTag("settings_backup").performClick()
        compose.onNodeWithText("开始导出").performClick()
        compose.onNodeWithText("选择保存位置").performClick()
        saveBackupFromDocumentsUi()
        awaitApp()
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText("操作完成").fetchSemanticsNodes().isNotEmpty()
        }

        runBlocking { gateway.replaceAll(BackupCatalogSnapshot()) }
        File(context.filesDir, "books").deleteRecursively()
        File(context.filesDir, "fonts").deleteRecursively()
        File(context.filesDir, "covers").deleteRecursively()

        compose.onNodeWithText("返回备份与恢复").performClick()
        compose.onNodeWithText("选择备份文件").performClick()
        selectBackupFromDocumentsUi()
        awaitApp()
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText("恢复预览").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("覆盖整个书架").performClick()
        compose.onNodeWithTag("restore_confirm").performScrollTo().performClick()
        compose.onNodeWithText("创建快照并覆盖").performClick()
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText("操作完成").fetchSemanticsNodes().isNotEmpty()
        }

        val result = runBlocking { BackupEquivalenceVerifier(context.filesDir).compare(expected, gateway.snapshot()) }
        assertTrue(result.mismatches.joinToString(), result.equivalent)
        assertTrue(result.mismatches.isEmpty())
        device.executeShellCommand("rm -f /sdcard/Download/$BACKUP_FILE_NAME /sdcard/Download/$BACKUP_FILE_NAME.zip")
    }

    private fun openReaderMenu() {
        repeat(2) {
            if (compose.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isNotEmpty()) return
            val bounds = currentPageBounds()
            check(device.click(bounds.center.x.toInt(), bounds.center.y.toInt())) { "reader menu tap failed" }
            runCatching {
                compose.waitUntil(3_000) {
                    compose.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
        compose.onNodeWithTag("reader-menu-navigation").assertIsDisplayed()
    }

    private fun hideReaderMenu() {
        if (compose.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isEmpty()) return
        val bounds = currentPageBounds()
        check(device.click(bounds.center.x.toInt(), bounds.center.y.toInt())) { "reader menu dismiss tap failed" }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun openProgressPanel() {
        compose.onNodeWithTag("reader-menu-more").performClick()
        compose.onNodeWithText("定位进度").performClick()
    }

    private fun longPressCurrentPage() {
        val bounds = currentPageBounds()
        device.executeShellCommand(
            "input touchscreen swipe ${bounds.center.x.toInt()} ${bounds.center.y.toInt()} " +
                "${bounds.center.x.toInt()} ${bounds.center.y.toInt()} 1200",
        )
    }

    private fun currentPageBounds() = compose.onAllNodesWithTag("reader_page_surface")
        .fetchSemanticsNodes()
        .map { it.boundsInRoot }
        .firstOrNull { bounds ->
            bounds.width > 0f && bounds.height > 0f &&
                bounds.left >= 0f && bounds.top >= 0f &&
                bounds.right <= device.displayWidth.toFloat() &&
                bounds.bottom <= device.displayHeight.toFloat()
        }
        ?: error("no reader page is visible inside the current display")

    private fun awaitTag(tag: String, timeoutMillis: Long, stage: String) {
        try {
            compose.waitUntil(timeoutMillis) {
                compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            error("$stage did not expose test tag $tag within ${timeoutMillis}ms")
        }
    }

    private fun awaitText(text: String, timeoutMillis: Long, stage: String) {
        try {
            compose.waitUntil(timeoutMillis) {
                compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            error("$stage did not expose text $text within ${timeoutMillis}ms")
        }
    }

    private fun awaitSelectionActions() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("reader_selection_actions").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("reader_selection_actions").assertIsDisplayed()
    }

    private fun clickSelectionAction(label: String) {
        val composeActions = compose.onAllNodesWithText(label)
        if (composeActions.fetchSemanticsNodes().isNotEmpty()) {
            composeActions[0].performClick()
            return
        }
        val action = device.wait(Until.findObject(By.text(label)), 2_000)
            ?: device.wait(Until.findObject(By.desc(label)), 2_000)
            ?: run {
                val overflow = device.findObject(By.descContains("More"))
                    ?: device.findObject(By.descContains("更多"))
                    ?: error("selection overflow missing for $label")
                overflow.click()
                device.wait(Until.findObject(By.text(label)), 3_000)
                    ?: error("selection action missing: $label")
            }
        action.click()
    }

    private fun clickPopupText(text: String) {
        compose.onNode(hasText(text) and hasAnyAncestor(isPopup())).performClick()
    }

    private fun selectFromDocumentsUi(fileName: String) {
        check(device.wait(Until.hasObject(DOCUMENTS_PACKAGE), 10_000)) { "DocumentsUI did not open" }
        openDownloads()
        val selector = By.res("android:id/title").text(fileName)
        var file = device.wait(Until.findObject(selector), 3_000)
        if (file == null) {
            val searchOpened = clickFresh(By.desc("Search"), 2_000) || clickFresh(By.desc("搜索"), 2_000)
            if (searchOpened) {
                val input = device.wait(
                    Until.findObject(By.res("com.google.android.documentsui:id/search_src_text")),
                    3_000,
                )
                input?.text = fileName
                device.executeShellCommand("input keyevent 66")
                file = device.wait(Until.findObject(selector), 15_000)
            }
        }
        check(file != null && clickFresh(selector, 5_000)) { "public fixture missing from DocumentsUI" }
    }

    private fun saveBackupFromDocumentsUi() {
        check(device.wait(Until.hasObject(DOCUMENTS_PACKAGE), 10_000)) { "DocumentsUI did not open for export" }
        openDownloads()
        val field = device.wait(Until.findObject(By.clazz(EditText::class.java)), 5_000)
            ?: error("DocumentsUI filename field missing")
        field.text = BACKUP_FILE_NAME
        check(clickFresh(By.text(Pattern.compile("保存|Save", Pattern.CASE_INSENSITIVE)), 5_000)) {
            "DocumentsUI save action missing"
        }
        device.wait(
            Until.findObject(By.text(Pattern.compile("替换|Replace", Pattern.CASE_INSENSITIVE))),
            1_500,
        )?.click()
    }

    private fun selectBackupFromDocumentsUi() {
        check(device.wait(Until.hasObject(DOCUMENTS_PACKAGE), 10_000)) { "DocumentsUI did not open for restore" }
        openDownloads()
        val name = Pattern.compile("^${Pattern.quote(BACKUP_FILE_NAME)}(?:\\.zip)?$")
        val selector = By.res("android:id/title").text(name)
        val file = device.wait(Until.findObject(selector), 5_000)
        check(file != null && clickFresh(selector, 5_000)) { "exported backup missing from DocumentsUI" }
    }

    private fun openDownloads() {
        if (!hasRootsDrawer()) {
            val rootsOpened = clickFresh(By.desc("Show roots"), 1_500) || clickFresh(By.desc("显示根目录"), 1_500)
            if (!rootsOpened) return
            val deadline = System.currentTimeMillis() + 3_000
            while (!hasRootsDrawer() && System.currentTimeMillis() < deadline) Thread.sleep(100)
            check(hasRootsDrawer()) { "DocumentsUI roots drawer did not open" }
        }
        val downloads = By.res("android:id/title").text(Pattern.compile("Downloads|下载"))
        check(clickFresh(downloads, 3_000)) { "DocumentsUI Downloads root missing" }
        check(waitForRootsDrawerToClose()) { "DocumentsUI roots drawer did not close" }
    }

    private fun hasRootsDrawer(): Boolean = device.hasObject(ROOTS_DRAWER) ||
        device.hasObject(GOOGLE_ROOTS_DRAWER)

    private fun waitForRootsDrawerToClose(): Boolean {
        val deadline = System.currentTimeMillis() + 3_000
        do {
            if (!hasRootsDrawer()) return true
            Thread.sleep(100)
        } while (System.currentTimeMillis() < deadline)
        return false
    }

    private fun awaitApp() {
        check(device.wait(Until.hasObject(By.pkg(targetPackage)), 15_000)) { "app did not resume" }
        compose.waitForIdle()
    }

    private fun clickFresh(selector: BySelector, timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        do {
            val candidate: UiObject2? = device.findObject(selector)
            if (candidate != null && runCatching {
                    val bounds = candidate.visibleBounds
                    device.click(bounds.centerX(), bounds.centerY())
                }.getOrDefault(false)
            ) return true
            Thread.sleep(100)
        } while (System.currentTimeMillis() < deadline)
        return false
    }

    private fun dismissImmersiveConfirmationIfPresent() {
        val confirmation = device.wait(Until.findObject(By.text("Viewing full screen")), 1_500) ?: return
        val acknowledged = clickFresh(By.text("GOT IT"), 2_000) ||
            clickFresh(By.text("知道了"), 500) ||
            clickFresh(By.text("我知道了"), 500)
        if (!acknowledged) {
            device.swipe(
                device.displayWidth / 2,
                confirmation.visibleBounds.top,
                device.displayWidth / 2,
                device.displayHeight / 3,
                30,
            )
        }
        check(device.wait(Until.gone(By.text("Viewing full screen")), 3_000)) {
            "platform immersive confirmation remained above the reader"
        }
    }

    private companion object {
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
        const val FIXTURE_FILE_NAME = "sample-novel.txt"
        const val FIXTURE_TITLE = "sample-novel"
        const val GROUP_NAME = "E2公开分组"
        const val NOTE_TEXT = "E2公开批注"
        const val BACKUP_FILE_NAME = "xinyue-e2-release.xinyuebackup"
        val DOCUMENTS_PACKAGE: BySelector = By.pkg(Pattern.compile("com\\.(google\\.android|android)\\.documentsui"))
        val ROOTS_DRAWER: BySelector = By.res("com.android.documentsui:id/roots_list")
        val GOOGLE_ROOTS_DRAWER: BySelector = By.res("com.google.android.documentsui:id/roots_list")
    }
}
