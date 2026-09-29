package com.xinyue.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Base64
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.xinyue.reader.core.domain.model.BUILT_IN_DARK_ID
import com.xinyue.reader.core.domain.model.BUILT_IN_PAPER_ID
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import com.xinyue.reader.core.domain.model.ReadingStatistics
import com.xinyue.reader.core.domain.model.RestoreConflict
import com.xinyue.reader.core.domain.model.RestoreConflictChoice
import com.xinyue.reader.core.domain.model.RestoreConflictKind
import com.xinyue.reader.core.domain.model.RestoreMode
import com.xinyue.reader.core.domain.model.RestorePreview
import com.xinyue.reader.core.domain.model.ThemeScheduleMode
import com.xinyue.reader.feature.backup.RestorePreviewScreen
import com.xinyue.reader.feature.backup.defaultConflictSelections
import com.xinyue.reader.feature.library.BookReadingRank
import com.xinyue.reader.feature.library.DailyReadingStat
import com.xinyue.reader.feature.library.LibraryUiState
import com.xinyue.reader.feature.library.LibraryScreen
import com.xinyue.reader.feature.library.StatisticsPeriod
import com.xinyue.reader.feature.library.StatisticsScreen
import com.xinyue.reader.feature.library.StatisticsUiState
import dagger.hilt.android.EntryPointAccessors
import java.util.regex.Pattern
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderV12AccessibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    private val targetPackage: String get() = instrumentation.targetContext.packageName

    @Test
    fun criticalControlsRemainLabeledReachableAnd48DpAtCurrentFontScale() {
        ensurePublicFixtureImported()
        compose.onNodeWithTag("library_root").assertIsDisplayed()
        assertTouchTarget(compose.onNodeWithTag("library_import").fetchSemanticsNode().boundsInRoot, "library import")
        assertTouchTarget(
            compose.onNodeWithTag("nav_settings", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot,
            "settings navigation",
        )
        compose.onNodeWithText("搜索书名或作者").assertIsDisplayed()

        compose.onNodeWithText(FIXTURE_TITLE).performClick()
        awaitTag("reader_root")
        dismissImmersiveConfirmationIfPresent()
        openReaderMenu()
        TOOLBAR_TAGS.forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTouchTarget(bounds, "reader toolbar $tag")
            assertInsideDisplay(bounds, "reader toolbar $tag")
        }

        compose.onNodeWithTag("reader-menu-more").performClick()
        compose.onNodeWithText("定位进度").performClick()
        awaitTag("reader-progress-preview-snippet")
        val slider = compose.onNodeWithTag("reader-progress-slider").fetchSemanticsNode()
        assertTrue("progress slider has no adjustable range", slider.config.contains(SemanticsProperties.ProgressBarRangeInfo))
        device.pressBack()

        openReaderMenu()
        compose.onNodeWithTag("reader-menu-appearance").performClick()
        compose.onNodeWithTag("reader-quick-settings-more").performClick()
        compose.onNodeWithTag("reader-settings-advanced").performScrollTo().performClick()
        compose.onNodeWithTag("reader-focus-band-toggle").performScrollTo().assertIsDisplayed()
        val focusToggle = compose.onNodeWithTag("reader-focus-band-toggle").fetchSemanticsNode()
        assertTrue("focus-band switch has no state", focusToggle.config.contains(SemanticsProperties.ToggleableState))
        compose.onNodeWithText("取消").performClick()

        openReaderMenu()
        compose.onNodeWithText("返回").performClick()
        awaitTag("library_root")
        compose.onNodeWithTag("nav_settings", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_backup"))
        compose.onNodeWithTag("settings_backup").performClick()
        awaitTag("backup_root")
        compose.onNodeWithText("开始导出").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("选择备份文件").performScrollTo().assertIsDisplayed()
        assertTouchTarget(compose.onNodeWithText("开始导出").fetchSemanticsNode().boundsInRoot, "backup export")
        assertTouchTarget(compose.onNodeWithText("选择备份文件").fetchSemanticsNode().boundsInRoot, "backup restore")
    }

    @Test
    fun captureRequestedReaderVisualState() {
        val requestedScenario = InstrumentationRegistry.getArguments().getString("visualScenario")
        assumeTrue("visualScenario is required for screenshot capture", !requestedScenario.isNullOrBlank())
        val scenario = requireNotNull(requestedScenario)

        when (scenario) {
            "library-covers-long-title" -> renderLibraryCoverVisual()
            "statistics" -> renderStatisticsVisual()
            "backup-conflict-preview" -> renderBackupConflictVisual()
            else -> renderReaderVisual(scenario)
        }
        compose.waitForIdle()
        device.waitForIdle()
        emitScreenshot(scenario)
    }

    private fun renderReaderVisual(scenario: String) {
        configureAutomaticTheme(scenario)
        ensurePublicFixtureImported()
        compose.onNodeWithText(FIXTURE_TITLE).performClick()
        awaitTag("reader_root")
        dismissImmersiveConfirmationIfPresent()

        when (scenario) {
            "preset-paper" -> applyBuiltInTheme("built-in-paper")
            "preset-sepia" -> applyBuiltInTheme("built-in-sepia")
            "preset-green" -> applyBuiltInTheme("built-in-green")
            "preset-dark" -> applyBuiltInTheme("built-in-dark")
            "preset-oled" -> applyBuiltInTheme("built-in-oled")
            "custom-high-contrast" -> showCustomContrast("#FF000000", "#FFFFFFFF", expectWarning = false)
            "custom-low-contrast" -> showCustomContrast("#FF777777", "#FF777777", expectWarning = true)
            "focus-band" -> enableFocusBandForScreenshot()
            "system-light", "system-dark", "scheduled-day", "scheduled-night" -> Unit
            else -> error("unsupported visualScenario: $scenario")
        }
    }

    private fun configureAutomaticTheme(scenario: String) {
        val mode = when (scenario) {
            "system-light", "system-dark" -> ThemeScheduleMode.FOLLOW_SYSTEM
            "scheduled-day", "scheduled-night" -> ThemeScheduleMode.FIXED_TIME
            else -> return
        }
        val now = LocalTime.now()
        val minute = now.hour * 60 + now.minute
        val nextMinute = (minute + 1) % (24 * 60)
        val schedule = ReaderThemeSchedule(
            mode = mode,
            lightThemeId = BUILT_IN_PAPER_ID,
            darkThemeId = BUILT_IN_DARK_ID,
            lightMinuteOfDay = if (scenario == "scheduled-night") nextMinute else minute,
            darkMinuteOfDay = if (scenario == "scheduled-night") minute else nextMinute,
            manualOverrideUntilNextSwitch = false,
        )
        val dependencies = EntryPointAccessors.fromApplication(
            instrumentation.targetContext.applicationContext,
            BackupDebugEntryPoint::class.java,
        )
        runBlocking { dependencies.readerThemeScheduleRepository().updateSchedule(schedule) }
    }

    private fun renderLibraryCoverVisual() {
        val customCover = createPublicCustomCover()
        val fallbackBook = publicBook(
            id = "fallback-book",
            title = "这是用于验证两行省略和百分之二百字体可读性的公开超长测试书名",
            fileName = "public-long-title.txt",
        )
        val customBook = publicBook(
            id = "custom-cover-book",
            title = "公开自定义封面示例",
            fileName = "public-cover.txt",
            customCoverPath = "covers/public-qa-cover.png",
            customCoverModel = customCover,
        )
        compose.activity.setContent {
            LibraryScreen(
                uiState = LibraryUiState(
                    books = listOf(fallbackBook, customBook),
                    progressFractions = mapOf(fallbackBook.id to 0.27, customBook.id to 1.0),
                    errorMessage = null,
                    query = "",
                    sort = com.xinyue.reader.feature.library.LibrarySort.RECENT,
                    groups = emptyList(),
                    filter = com.xinyue.reader.feature.library.LibraryFilter.All,
                    hasAnyBooks = true,
                    coverUpdatingBookIds = emptySet(),
                    selectedBookIds = emptySet(),
                    isSelectionMode = false,
                    selectionActionInProgress = false,
                    pendingDeletion = null,
                    readingMillisByBook = mapOf(fallbackBook.id to 125_000L, customBook.id to 300_000L),
                ),
                onImport = {}, onOpenBook = {}, onDismissError = {}, onQueryChanged = {},
                onSortChanged = {}, onFilterChanged = {}, onCreateGroup = {}, onRenameGroup = { _, _ -> },
                onDeleteGroup = {}, onEditBook = { _, _, _ -> }, onDeleteBook = {}, onPickCover = {},
                onClearCover = {}, onEnterSelection = {}, onToggleSelection = {}, onSelectAllVisible = {},
                onClearSelection = {}, onMoveSelected = {}, onMarkSelectedFinished = {}, onDeleteSelected = {},
                onUndoDelete = {}, onOpenStatistics = {}, onOpenBackup = {},
                isSearchActive = false, onSearchActiveChange = {},
            )
        }
        compose.onNodeWithTag("library_root").assertIsDisplayed()
        compose.onNodeWithText(fallbackBook.title).assertIsDisplayed()
        compose.onNodeWithContentDescription("《${customBook.title}》的自定义封面").assertIsDisplayed()
    }

    private fun renderStatisticsVisual() {
        val date = LocalDate.of(2026, 7, 16)
        val book = publicBook("statistics-book", "公开统计示例", "public-statistics.txt")
        val statistics = ReadingStatistics(book.id, 18 * 60_000L, 3, 0, 24 * 60 * 60_000L)
        compose.activity.setContent {
            StatisticsScreen(
                uiState = StatisticsUiState(
                    period = StatisticsPeriod.WEEK,
                    selectedBookId = book.id,
                    selectedBook = book,
                    total = statistics,
                    daily = listOf(
                        DailyReadingStat(date.minusDays(1), 6 * 60_000L, 1),
                        DailyReadingStat(date, 12 * 60_000L, 2),
                    ),
                    ranking = listOf(BookReadingRank(book, statistics)),
                    clearConfirmationVisible = false,
                    isClearing = false,
                    errorMessage = null,
                ),
                onBack = {}, onSelectPeriod = {}, onSelectBook = {}, onRequestClear = {},
                onConfirmClear = {}, onCancelClear = {}, onDismissError = {},
            )
        }
        compose.onNodeWithTag("statistics_root").assertIsDisplayed()
        compose.onNodeWithText("有效阅读 18 分钟").assertIsDisplayed()
    }

    private fun renderBackupConflictVisual() {
        val conflicts = listOf(
            RestoreConflict(
                id = "public-progress-conflict",
                kind = RestoreConflictKind.PROGRESS,
                label = "《公开恢复示例》阅读进度",
                allowedChoices = setOf(RestoreConflictChoice.KEEP_LOCAL, RestoreConflictChoice.USE_BACKUP),
                suggestedChoice = RestoreConflictChoice.KEEP_LOCAL,
            ),
            RestoreConflict(
                id = "public-theme-conflict",
                kind = RestoreConflictKind.THEME,
                label = "公开护眼主题",
                allowedChoices = setOf(RestoreConflictChoice.KEEP_LOCAL, RestoreConflictChoice.RENAME_BACKUP),
                suggestedChoice = RestoreConflictChoice.RENAME_BACKUP,
            ),
        )
        val selections = defaultConflictSelections(conflicts).toMutableMap().apply {
            this["public-theme-conflict"] = com.xinyue.reader.feature.backup.RestoreConflictSelection(
                choice = RestoreConflictChoice.RENAME_BACKUP,
                renamedValue = "公开护眼主题（备份）",
            )
        }
        compose.activity.setContent {
            RestorePreviewScreen(
                preview = RestorePreview(
                    stagedPlanToken = "public-visual-token",
                    formatVersion = 1,
                    createdAtEpochMillis = 1_768_435_200_000L,
                    options = BackupOptions(includeBookText = true, includeFonts = false),
                    newBookCount = 2,
                    duplicateBookCount = 1,
                    conflictCount = conflicts.size,
                    stagingBytes = 2_097_152L,
                    publishBytes = 1_048_576L,
                    snapshotBytes = 524_288L,
                    requiredFreeBytes = 3_670_016L,
                    conflicts = conflicts,
                ),
                selections = selections,
                mode = RestoreMode.MERGE,
                canRestore = true,
                onSelectionChanged = { _, _ -> }, onModeChanged = {}, onRestore = {}, onCancel = {},
            )
        }
        compose.onNodeWithTag("restore_preview").assertIsDisplayed()
        compose.onNodeWithText("逐项处理冲突").assertIsDisplayed()
    }

    private fun publicBook(
        id: String,
        title: String,
        fileName: String,
        customCoverPath: String? = null,
        customCoverModel: File? = null,
    ) = Book(
        id = id,
        title = title,
        author = "公开测试作者",
        originalFileName = fileName,
        originalPath = "books/$id/original.txt",
        normalizedPath = "books/$id/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "public-$id",
        contentLength = 12_345L,
        createdAtEpochMillis = 1_768_435_200_000L,
        lastOpenedAtEpochMillis = null,
        groupId = null,
        customCoverPath = customCoverPath,
        finished = false,
        customCoverModel = customCoverModel,
    )

    private fun createPublicCustomCover(): File {
        val file = File(instrumentation.targetContext.cacheDir, "public-qa-cover.png")
        val bitmap = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.rgb(25, 76, 102))
        paint.color = Color.rgb(241, 196, 83)
        canvas.drawRect(24f, 24f, 276f, 426f, paint)
        paint.color = Color.rgb(25, 76, 102)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 96f
        canvas.drawText("QA", 150f, 250f, paint)
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "custom cover PNG creation failed" }
        }
        bitmap.recycle()
        return file
    }

    private fun ensurePublicFixtureImported() {
        compose.onNodeWithTag("nav_library", useUnmergedTree = true).performClick()
        if (compose.onAllNodesWithText(FIXTURE_TITLE).fetchSemanticsNodes().isNotEmpty()) return
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 3 / 4,
            device.displayWidth / 2,
            device.displayHeight / 4,
            20,
        )
        compose.waitForIdle()
        if (compose.onAllNodesWithText(FIXTURE_TITLE).fetchSemanticsNodes().isNotEmpty()) return
        val importTag = if (compose.onAllNodesWithTag("library_empty_import").fetchSemanticsNodes().isNotEmpty()) {
            "library_empty_import"
        } else {
            "library_import"
        }
        compose.onNodeWithTag(importTag).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("选择 TXT 文件").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("选择 TXT 文件").performClick()
        selectFromDocumentsUi(FIXTURE_FILE_NAME)
        check(device.wait(Until.hasObject(By.pkg(targetPackage)), 10_000)) { "app did not resume after DocumentsUI" }
        compose.waitUntil(30_000) { compose.onAllNodesWithText("导入成功").fetchSemanticsNodes().isNotEmpty() }
        device.pressBack()
    }

    private fun selectFromDocumentsUi(fileName: String) {
        check(device.wait(Until.hasObject(DOCUMENTS_PACKAGE), 10_000)) { "DocumentsUI did not open" }
        openDownloads()
        val selector = By.res("android:id/title").text(fileName)
        var file = device.wait(Until.findObject(selector), 3_000)
        if (file == null) {
            val opened = clickFresh(By.desc("Search"), 2_000) || clickFresh(By.desc("搜索"), 2_000)
            if (opened) {
                val input = device.wait(Until.findObject(By.res("com.google.android.documentsui:id/search_src_text")), 3_000)
                    ?: device.wait(Until.findObject(By.res("com.android.documentsui:id/search_src_text")), 1_000)
                input?.text = fileName
                device.executeShellCommand("input keyevent 66")
                file = device.wait(Until.findObject(selector), 15_000)
            }
        }
        check(file != null && clickFresh(selector, 5_000)) { "public fixture missing from DocumentsUI" }
    }

    private fun openDownloads() {
        if (!hasRootsDrawer()) {
            val opened = clickFresh(By.desc("Show roots"), 1_500) || clickFresh(By.desc("显示根目录"), 1_500)
            if (!opened) return
            val deadline = System.currentTimeMillis() + 3_000
            while (!hasRootsDrawer() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        }
        if (hasRootsDrawer()) {
            check(clickFresh(By.res("android:id/title").text(Pattern.compile("Downloads|下载")), 3_000)) {
                "DocumentsUI Downloads root missing"
            }
            val deadline = System.currentTimeMillis() + 3_000
            while (hasRootsDrawer() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        }
    }

    private fun hasRootsDrawer(): Boolean = device.hasObject(By.res("com.google.android.documentsui:id/roots_list")) ||
        device.hasObject(By.res("com.android.documentsui:id/roots_list"))

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

    private fun openReaderMenu() {
        if (compose.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isNotEmpty()) return
        val bounds = currentPageBounds()
        check(device.click(bounds.center.x.toInt(), bounds.center.y.toInt())) { "reader menu tap failed" }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun applyBuiltInTheme(themeId: String) {
        openReaderMenu()
        compose.onNodeWithTag("reader-menu-appearance").performClick()
        compose.onNodeWithTag("reader-quick-settings-more").performClick()
        compose.onNodeWithTag("reader-theme-apply-$themeId").performScrollTo().performClick()
        val save = compose.onNodeWithText("保存")
        if (save.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) {
            compose.onNodeWithText("取消").performClick()
        } else {
            save.performClick()
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("阅读外观").fetchSemanticsNodes().isEmpty() }
        hideReaderMenu()
    }

    private fun showCustomContrast(foreground: String, background: String, expectWarning: Boolean) {
        openReaderMenu()
        compose.onNodeWithTag("reader-menu-appearance").performClick()
        compose.onNodeWithTag("reader-quick-settings-more").performClick()
        compose.onNodeWithTag("reader-settings-advanced").performScrollTo().performClick()
        compose.onNodeWithTag("reader-foreground-argb").performScrollTo().performTextReplacement(foreground)
        compose.onNodeWithTag("reader-background-argb").performScrollTo().performTextReplacement(background)
        val expectedRatio = if (expectWarning) "对比度 1.00 : 1" else "对比度 21.00 : 1"
        compose.onNodeWithText(expectedRatio).performScrollTo().assertIsDisplayed()
        val warningVisible = compose.onAllNodesWithText("对比度较低，长时间阅读可能疲劳")
            .fetchSemanticsNodes().isNotEmpty()
        check(warningVisible == expectWarning) {
            "contrast warning state mismatch: expected=$expectWarning actual=$warningVisible"
        }
    }

    private fun enableFocusBandForScreenshot() {
        openReaderMenu()
        compose.onNodeWithTag("reader-menu-appearance").performClick()
        compose.onNodeWithTag("reader-quick-settings-more").performClick()
        compose.onNodeWithTag("reader-settings-advanced").performScrollTo().performClick()
        val toggle = compose.onNodeWithTag("reader-focus-band-toggle")
        toggle.performScrollTo()
        val state = toggle.fetchSemanticsNode().config[SemanticsProperties.ToggleableState]
        if (state != androidx.compose.ui.state.ToggleableState.On) toggle.performClick()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("阅读外观").fetchSemanticsNodes().isEmpty() }
        hideReaderMenu()
    }

    private fun hideReaderMenu() {
        if (compose.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isEmpty()) return
        val bounds = currentPageBounds()
        check(device.click(bounds.center.x.toInt(), bounds.center.y.toInt())) { "reader menu dismissal tap failed" }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun emitScreenshot(name: String) {
        check(name.matches(Regex("[a-z0-9-]+"))) { "unsafe screenshot name" }
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val bytes = ByteArrayOutputStream()
        bytes.use { stream ->
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG compression failed" }
        }
        screenshot.recycle()
        val encoded = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        check(encoded.isNotEmpty()) { "screenshot encoding is empty" }
        val chunks = encoded.chunked(SCREENSHOT_CHUNK_SIZE)
        chunks.forEachIndexed { index, chunk ->
            instrumentation.sendStatus(
                2,
                Bundle().apply {
                    putString("stream", "XINYUE_SCREENSHOT_CHUNK=$name:$index:${chunks.size}:$chunk\n")
                },
            )
        }
    }

    private fun currentPageBounds() = compose.onAllNodesWithTag("reader_page_surface")
        .fetchSemanticsNodes().map { it.boundsInRoot }
        .firstOrNull { it.width > 0f && it.height > 0f } ?: error("reader page is not visible")

    private fun awaitTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun assertTouchTarget(bounds: Rect, label: String) {
        val minimum = 48f * compose.activity.resources.displayMetrics.density
        assertTrue("$label width ${bounds.width} is below 48dp", bounds.width >= minimum)
        assertTrue("$label height ${bounds.height} is below 48dp", bounds.height >= minimum)
    }

    private fun assertInsideDisplay(bounds: Rect, label: String) {
        assertTrue("$label crosses left", bounds.left >= 0f)
        assertTrue("$label crosses top", bounds.top >= 0f)
        assertTrue("$label crosses right", bounds.right <= device.displayWidth.toFloat())
        assertTrue("$label crosses bottom", bounds.bottom <= device.displayHeight.toFloat())
    }

    private fun dismissImmersiveConfirmationIfPresent() {
        val confirmation = device.wait(Until.findObject(By.text("Viewing full screen")), 1_500) ?: return
        val gotIt = device.wait(Until.findObject(By.text("GOT IT")), 2_000)
            ?: device.wait(Until.findObject(By.text("知道了")), 500)
            ?: device.wait(Until.findObject(By.text("我知道了")), 500)
        val dismissed = gotIt?.let {
            val bounds = it.visibleBounds
            device.click(bounds.centerX(), bounds.centerY())
        } ?: device.swipe(device.displayWidth / 2, confirmation.visibleBounds.top, device.displayWidth / 2, device.displayHeight / 3, 30)
        check(dismissed && device.wait(Until.gone(By.text("Viewing full screen")), 3_000)) {
            "platform immersive confirmation remained above the reader"
        }
    }

    private companion object {
        const val FIXTURE_FILE_NAME = "sample-novel.txt"
        const val FIXTURE_TITLE = "sample-novel"
        val DOCUMENTS_PACKAGE: BySelector = By.pkg(Pattern.compile("com(?:\\.google)?\\.android\\.documentsui"))
        val TOOLBAR_TAGS = listOf(
            "reader-menu-back",
            "reader-menu-bookmark",
            "reader-menu-more",
            "reader-menu-navigation",
            "reader-menu-search",
            "reader-menu-speech",
            "reader-menu-appearance",
            "reader-menu-notes",
        )
        const val SCREENSHOT_CHUNK_SIZE = 3_000
    }
}
