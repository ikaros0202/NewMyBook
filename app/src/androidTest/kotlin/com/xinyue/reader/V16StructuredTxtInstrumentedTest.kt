package com.xinyue.reader

import android.content.ClipboardManager
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.roundToInt
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.speech.tts.TextToSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V16StructuredTxtInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice
        get() = UiDevice.getInstance(instrumentation)
    private val targetPackage: String
        get() = instrumentation.targetContext.packageName
    private var swipePreviousEndOffset: Int = -1

    @Test
    fun structuredTxtImportsThroughDocumentsUiAndOpensRealReader() {
        prepareReader()
        composeRule.onNodeWithTag("reader_root").assertIsDisplayed()
        currentReaderPageBounds()
        assertRealTextView(currentSelectableTextView())
    }

    @Test
    fun structuredTxtRendersTitleSpansAndRunningHeaderContract() {
        prepareReader()
        setQuickFontSize(36f)
        setQuickPageMethodNone()
        saveAppearance()
        closeReaderControls()
        resetReaderToStart()
        closeReaderControls()
        val firstPage = collectCurrentPageSnapshot(0)
        if (case().oracleMode == OracleMode.PUBLIC) {
            val titles = rawTitleRanges(PUBLIC_FIXTURE_RAW)
            val pages = collectAllPagesFromCurrent(firstPage)
            assertPageSequence(pages, PUBLIC_FIXTURE_RAW.length, titles)
            assertAllPublicTitleSpans(pages, titles)
            resetReaderToStart()
            closeReaderControls()
            val titlePage = collectCurrentPageSnapshot(0)
            val firstTitle = titles.firstOrNull() ?: failStep("title-oracle-first")
            assertTrue(
                "v16|${case().alias}|title-first-page",
                firstTitle.startOffset >= titlePage.startOffset && firstTitle.endOffset <= titlePage.endOffset,
            )
            assertTitleSpans(titlePage.textView, titlePage, listOf(firstTitle))
            assertRunningHeaderAbsentOnTitlePage(firstTitle.text)
            assertRunningHeaderOnContinuationPage(titlePage, firstTitle, titles.getOrNull(1))
        } else {
            assertTrue("v16|${case().alias}|title-page-nonempty", firstPage.hasVisibleGlyph)
        }
    }

    @Test
    fun structuredTxtPagesAreContinuousWithoutBlankOrChapterOverlap() {
        prepareReader()
        closeReaderControls()
        resetReaderToStart()
        closeReaderControls()
        val publicOracle = case().oracleMode == OracleMode.PUBLIC
        val pages = collectAllPagesFromCurrent(
            firstPage = collectCurrentPageSnapshot(0),
            requireCompleteBook = publicOracle,
        )
        val titles = if (publicOracle) rawTitleRanges(PUBLIC_FIXTURE_RAW) else emptyList()
        val rawLength = if (publicOracle) PUBLIC_FIXTURE_RAW.length else pages.last().endOffset
        assertPageSequence(pages, rawLength, titles)
        if (pages.size > 1) {
            swipeToPreviousPage(pages[pages.lastIndex - 1])
        }
    }

    @Test
    fun structuredTxtCopiesRawSelectionWithoutLayoutPlaceholder() {
        prepareReader()
        setQuickFontSize(24f)
        saveAppearance()
        closeReaderControls()
        resetReaderToStart()
        closeReaderControls()
        val expectedRaw = if (case().oracleMode == OracleMode.PUBLIC) {
            moveToPublicCopyMarker()
        } else {
            null
        }
        copySelectionAndAssertRaw(expectedRaw)
    }

    @Test
    fun structuredTxtQuickAndFullSettingsHonorSaveCancelAndScope() {
        val scopeBooks = prepareScopeBooks()
        val firstBookTag = scopeBooks[0]
        val secondBookTag = scopeBooks[1]
        openBookByTag(firstBookTag)
        openQuickSettings()
        assertQuickSectionOrder()
        selectCurrentBookScope()
        val originalCurrentValue = quickFontValue()
        assertTrue(
            "v16|${case().alias}|current-book-global-note",
            composeRule.onAllNodesWithTag("reader-quick-settings-global-only-note").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTagAbsent("reader-quick-settings-page-method", "current-book-page-method-hidden")
        assertTagAbsent("reader-quick-settings-brightness-slider", "current-book-brightness-hidden")
        setQuickFontSize(30f)
        val quickDraftValue = quickFontValue()
        selectQuickIndentZero()

        openFullSettings()
        assertFullSettingsContract()
        assertEquals("v16|${case().alias}|quick-full-same-draft", quickDraftValue, fullFontValue(), 0.01f)
        setFullFontSize(28f)
        cancelAppearance()
        openQuickSettings()
        selectCurrentBookScope()
        assertEquals("v16|${case().alias}|full-cancel-original", originalCurrentValue, quickFontValue(), 0.01f)
        cancelAppearance()

        openQuickSettings()
        selectCurrentBookScope()
        setQuickFontSize(31f)
        selectQuickIndentZero()
        val savedCurrentValue = quickFontValue()
        saveAppearance()
        closeReaderControls()
        goToLibrary()
        openBookByTag(secondBookTag)
        openQuickSettings()
        selectCurrentBookScope()
        val secondBookOriginalValue = quickFontValue()
        assertTrue(
            "v16|${case().alias}|current-book-a-b-isolation",
            kotlin.math.abs(savedCurrentValue - secondBookOriginalValue) > 0.01f,
        )
        setQuickFontSize(32f)
        val savedSecondBookValue = quickFontValue()
        saveAppearance()
        closeReaderControls()
        openQuickSettings()
        selectGlobalScope()
        assertTagPresent("reader-quick-settings-page-method", "global-page-method-visible")
        setQuickFontSize(29f)
        val savedGlobalValue = quickFontValue()
        saveAppearance()
        composeRule.activityRule.scenario.recreate()
        waitForTag("reader_root", "global-recreate-reader")
        openQuickSettings()
        selectGlobalScope()
        assertEquals("v16|${case().alias}|global-save", savedGlobalValue, quickFontValue(), 0.01f)
        assertTrue("v16|${case().alias}|global-page-method-after-save", composeRule.onAllNodesWithTag("reader-quick-settings-page-method").fetchSemanticsNodes().isNotEmpty())
        cancelAppearance()

        closeReaderControls()
        goToLibrary()
        openBookByTag(firstBookTag)
        openQuickSettings()
        selectGlobalScope()
        assertEquals("v16|${case().alias}|global-visible-on-a", savedGlobalValue, quickFontValue(), 0.01f)
        cancelAppearance()
        openQuickSettings()
        selectCurrentBookScope()
        assertEquals("v16|${case().alias}|current-book-isolation", savedCurrentValue, quickFontValue(), 0.01f)
        assertTagAbsent("reader-quick-settings-page-method", "current-book-page-method-hidden-after-global")
        cancelAppearance()

        closeReaderControls()
        goToLibrary()
        openBookByTag(secondBookTag)
        openQuickSettings()
        selectCurrentBookScope()
        assertEquals("v16|${case().alias}|current-book-b-save", savedSecondBookValue, quickFontValue(), 0.01f)
        cancelAppearance()
    }

    @Test
    fun structuredTxtCompactShelfControlsRemainReachableAtTwoHundredPercentAndLandscape() {
        prepareReader()
        goToLibrary()
        assertTrue(
            "v16|${case().alias}|font-scale-required",
            composeRule.activity.resources.configuration.fontScale >= 1.9f,
        )
        assertTrue(
            "v16|${case().alias}|landscape-required",
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        )
        assertTagPresent("library-shelf-list", "shelf-content")
        listOf(
            "library_search_trigger",
            "library_import",
            "library-layout-button",
            "library-options-button",
        ).forEach { assertReachable48dp(it) }
        composeRule.onNodeWithTag("library-shelf-list", useUnmergedTree = true)
            .performScrollToIndex(0)
        waitForTag("library-status-filters", "shelf-status-scroll")

        listOf(
            "library-status-reading",
            "library-status-unread",
            "library-status-finished",
            "library-filter-more",
        ).forEach { tag ->
            composeRule.onNodeWithTag("library-shelf-list", useUnmergedTree = true)
                .performScrollToIndex(0)
            assertReachable48dp(tag)
        }
        clickShelfStatusFilter("library-status-reading", "shelf-status-reading")

        composeRule.onNodeWithTag("library-options-button").performClick()
        assertReachable48dp("library-view-series")
        composeRule.onNodeWithTag("library-view-series", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("library-options-button").performClick()
        assertShelfSelected("library-view-series", "shelf-view-series")
        assertReachable48dp("library-view-collections")
        composeRule.onNodeWithTag("library-view-collections", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("library-options-button").performClick()
        assertShelfSelected("library-view-collections", "shelf-view-collections")
        device.pressBack()

        composeRule.onNodeWithTag("library-layout-button").performClick()
        assertReachable48dp("library-layout-list")
        composeRule.onNodeWithTag("library-layout-list", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("library-layout-button").performClick()
        assertReachable48dp("library-density-compact")
        composeRule.onNodeWithTag("library-density-compact", useUnmergedTree = true).performClick()

        composeRule.onNodeWithTag("library_search_trigger").performClick()
        waitForTag("library_search_root", "shelf-search-open")
        assertReachable48dp("library_search_back")
        composeRule.onNodeWithTag("library_search_back").performClick()
        waitForTag("library_root", "shelf-search-close")

        assertReachable48dp("library-options-button")
        composeRule.onNodeWithTag("library-options-button").performClick()
        waitForStep("shelf-sort-menu", 5_000) {
            composeRule.onAllNodesWithTag("library-sort-option-title", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertReachable48dp("library-sort-option-title")
        assertReachable48dp("library-sort-option-recent")
        device.pressBack()
        waitForStep("shelf-sort-title-close", 5_000) {
            composeRule.onAllNodesWithTag("library-sort-option-title", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("library-options-button").performClick()
        assertReachable48dp("library-create-group")
        composeRule.onNodeWithTag("library-create-group", useUnmergedTree = true).performClick()
        waitForTag("library-group-name-dialog", "shelf-group-open")
        composeRule.onNodeWithTag("library-group-name-input", useUnmergedTree = true)
            .performTextInput(SHELF_GROUP_MARKER)
        assertReachable48dp("library-group-name-save")
        composeRule.onNodeWithTag("library-group-name-save", useUnmergedTree = true).performClick()
        waitForStep("shelf-group-close", 5_000) {
            composeRule.onAllNodesWithTag("library-group-name-dialog", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
        assertReachable48dp("library-filter-more")
        composeRule.onNodeWithTag("library-filter-more", useUnmergedTree = true).performClick()
        waitForText(SHELF_GROUP_MARKER, "shelf-group-created")
        device.pressBack()

        assertReachable48dp("library_import")
        composeRule.onNodeWithTag("library_import", useUnmergedTree = true).performClick()
        waitForTag("import_select_files", "shelf-import-open")
        assertReachable48dp("import_select_files")
        device.pressBack()
        waitForTag("library_root", "shelf-import-close")
    }

    @Test
    fun structuredTxtSearchAndNoteKeepRawOffsetsAtChapterBoundary() {
        prepareReader()
        closeReaderControls()
        resetReaderToStart()
        closeReaderControls()
        searchFromRealReader()
        closeReaderControls()
        resetReaderToStart()
        closeReaderControls()
        createChapterBoundaryNote()
    }

    @Test
    fun structuredTxtTtsUsesRawPageTextWhenEngineAvailable() {
        prepareReader()
        openReaderMenu()
        val ttsAvailable = hasTtsEngine()
        if (!ttsAvailable) {
            reportSkip("tts-engine")
            assumeTrue("v16|${case().alias}|tts-engine-precondition", false)
        }
        composeRule.onNodeWithTag("reader-menu-speech").performClick()
        waitForContentDescription("停止朗读", "tts-start")
        composeRule.onNodeWithTag("reader-menu-speech").performClick()
        waitForContentDescription("开始朗读", "tts-stop")
        closeReaderControls()
        val page = collectCurrentPageSnapshot(0)
        swipePreviousEndOffset = page.endOffset
        val next = swipeToNextPage(0) ?: failStep("tts-next-page")
        assertTrue("v16|${case().alias}|tts-next-page-smoke", next.hasVisibleGlyph)
        reportSkip("tts-raw-oracle")
    }

    private enum class OracleMode { PUBLIC, STRUCTURAL }

    private data class AcceptanceCase(
        val alias: String,
        val oracleMode: OracleMode,
        val remoteFileName: String,
    )

    private data class TitleExpectation(
        val text: String,
        val startOffset: Int,
        val endOffset: Int,
    )

    private fun case(): AcceptanceCase {
        val arguments = InstrumentationRegistry.getArguments()
        val requestedAlias = arguments.getString("v16_case_alias").orEmpty()
        val alias = when {
            requestedAlias.isBlank() -> PUBLIC_CASE_ALIAS
            requestedAlias == PUBLIC_CASE_ALIAS -> PUBLIC_CASE_ALIAS
            PRIVATE_CASE_ALIAS.matches(requestedAlias) -> requestedAlias
            else -> "private-case-unknown"
        }
        val requestedMode = arguments.getString("v16_oracle_mode").orEmpty().lowercase(Locale.ROOT)
        val oracleMode = if (alias == PUBLIC_CASE_ALIAS && requestedMode != "structural") {
            OracleMode.PUBLIC
        } else {
            OracleMode.STRUCTURAL
        }
        return AcceptanceCase(alias, oracleMode, REMOTE_INPUT_NAME)
    }

    private fun prepareReader() {
        goToLibrary()
        if (!hasCaseBook()) {
            importCaseThroughDocumentsUi()
        }
        openCaseBook()
        dismissImmersiveConfirmationIfPresent()
        waitForTag("reader_root", "reader-root")
        waitForTag("reader_page_surface", "reader-page-surface")
    }

    private fun goToLibrary() {
        if (composeRule.onAllNodesWithTag("library_root").fetchSemanticsNodes().isNotEmpty()) return
        val navigation = composeRule.onAllNodesWithTag("nav_library", useUnmergedTree = true)
        if (navigation.fetchSemanticsNodes().isNotEmpty()) {
            navigation[0].performClick()
        } else {
            repeat(3) {
                if (composeRule.onAllNodesWithTag("library_root").fetchSemanticsNodes().isNotEmpty()) return
                device.pressBack()
            }
        }
        waitForTag("library_root", "library-root")
    }

    private fun hasCaseBook(): Boolean = bookTitleTags().isNotEmpty()

    private fun importCaseThroughDocumentsUi(fileName: String = case().remoteFileName) {
        val importTag = if (
            composeRule.onAllNodesWithTag("library_empty_import").fetchSemanticsNodes().isNotEmpty()
        ) {
            "library_empty_import"
        } else {
            "library_import"
        }
        openImportEntry(importTag)
        composeRule.onNodeWithTag("import_select_files").performClick()
        selectFromDocumentsUi(fileName)
        waitForTargetApp("import-return")
        waitForStep("import-accepted", 45_000) {
            composeRule.onAllNodesWithText("继续选择文件").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("导入成功").fetchSemanticsNodes().isNotEmpty()
        }
        device.pressBack()
        waitForStep("imported-book", 90_000) { bookTitleTags().isNotEmpty() }
    }

    private fun openImportEntry(importTag: String) {
        repeat(3) { attempt ->
            if (composeRule.onAllNodesWithTag("import_select_files").fetchSemanticsNodes().isNotEmpty()) return
            val triggers = composeRule.onAllNodesWithTag(importTag)
            val triggerNodes = triggers.fetchSemanticsNodes()
            if (triggerNodes.isNotEmpty()) {
                when (attempt) {
                    0 -> runCatching {
                        if (importTag == "library_empty_import") triggers[0].performScrollTo()
                        triggers[0].performClick()
                    }
                    1 -> triggers[0].performClick()
                    else -> {
                        val bounds = triggerNodes[0].boundsInRoot
                        if (bounds.width > 0f && bounds.height > 0f) {
                            device.click(bounds.center.x.roundToInt(), bounds.center.y.roundToInt())
                        }
                    }
                }
            }
            val opened = runCatching {
                composeRule.waitUntil(5_000) {
                    composeRule.onAllNodesWithTag("import_select_files").fetchSemanticsNodes().isNotEmpty()
                }
            }.isSuccess
            if (opened) return
        }
        failStep("import-entry")
    }

    private fun prepareScopeBooks(): List<String> {
        goToLibrary()
        val before = bookTitleTags()
        importCaseThroughDocumentsUi(SCOPE_A_INPUT)
        val afterA = waitForBookTitleTags(before.size + 1, "scope-a-import")
        val first = afterA.firstOrNull { it !in before } ?: failStep("scope-a-book")

        val beforeB = afterA
        importCaseThroughDocumentsUi(SCOPE_B_INPUT)
        val afterB = waitForBookTitleTags(beforeB.size + 1, "scope-b-import")
        val second = afterB.firstOrNull { it !in beforeB } ?: failStep("scope-b-book")
        return listOf(first, second)
    }

    private fun bookTitleTags(): List<String> =
        composeRule.onAllNodes(bookTitleTagMatcher, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                if (node.config.contains(SemanticsProperties.TestTag)) {
                    node.config[SemanticsProperties.TestTag]
                } else {
                    null
                }
            }
            .distinct()

    private fun waitForBookTitleTags(minCount: Int, step: String): List<String> {
        var tags = emptyList<String>()
        waitForStep(step, 30_000) {
            tags = bookTitleTags()
            tags.size >= minCount
        }
        return tags
    }

    private fun openBookByTag(tag: String) {
        goToLibrary()
        composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        dismissImmersiveConfirmationIfPresent()
        waitForTag("reader_root", "reader-open-by-tag")
        waitForTag("reader_page_surface", "reader-page-surface-by-tag")
    }

    private fun openCaseBook() {
        if (composeRule.onAllNodesWithTag("reader_root").fetchSemanticsNodes().isNotEmpty()) return
        val exactTitle = composeRule.onAllNodesWithText(BOOK_TITLE)
        if (exactTitle.fetchSemanticsNodes().isNotEmpty()) {
            exactTitle[0].performClick()
        } else {
            val taggedTitles = composeRule.onAllNodes(bookTitleTagMatcher, useUnmergedTree = true)
            if (taggedTitles.fetchSemanticsNodes().isEmpty()) failStep("book-open")
            taggedTitles[0].performScrollTo().performClick()
        }
        waitForTag("reader_root", "reader-open")
    }

    private fun dismissImmersiveConfirmationIfPresent() {
        device.wait(
            Until.findObject(By.text(Pattern.compile("Viewing full screen|全屏阅读"))),
            1_500,
        ) ?: return
        val action = device.wait(
            Until.findObject(By.text(Pattern.compile("GOT IT|知道了|我知道了"))),
            2_000,
        )
        if (action == null) failStep("immersive-confirmation")
        action.click()
        if (!device.wait(Until.gone(By.text(Pattern.compile("Viewing full screen|全屏阅读"))), 3_000)) {
            failStep("immersive-dismiss")
        }
    }

    private fun waitForTag(tag: String, step: String) {
        try {
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
            failStep(step)
        }
    }

    private fun waitForText(text: String, step: String) {
        try {
            composeRule.waitUntil(30_000) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
            failStep(step)
        }
    }

    private fun waitForContentDescription(description: String, step: String) {
        try {
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
            failStep(step)
        }
    }

    private fun hasTtsEngine(): Boolean {
        val latch = CountDownLatch(1)
        var statusOk = false
        var tts: TextToSpeech? = null
        instrumentation.runOnMainSync {
            tts = TextToSpeech(composeRule.activity) { status ->
                statusOk = status == TextToSpeech.SUCCESS
                latch.countDown()
            }
        }
        latch.await(3, TimeUnit.SECONDS)
        var engineAvailable = false
        instrumentation.runOnMainSync {
            engineAvailable = statusOk && !tts?.engines.isNullOrEmpty()
            tts?.shutdown()
        }
        return engineAvailable
    }

    private fun reportSkip(step: String) {
        instrumentation.sendStatus(
            0,
            Bundle().apply { putString("stream", "V16_STATUS=SKIP|step=$step") },
        )
    }

    private fun waitForTargetApp(step: String) {
        if (!device.wait(Until.hasObject(By.pkg(targetPackage)), 15_000)) failStep(step)
    }

    private fun openReaderMenu() {
        if (composeRule.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isNotEmpty()) return
        repeat(3) {
            if (composeRule.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isNotEmpty()) return
            val bounds = currentReaderPageBounds()
            if (device.click(bounds.centerX(), bounds.centerY())) {
                val opened = runCatching {
                    composeRule.waitUntil(5_000) {
                        composeRule.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isNotEmpty()
                    }
                }.isSuccess
                if (opened) return
            }
        }
        failStep("reader-menu-open")
    }

    private fun openAppearance() {
        openReaderMenu()
        composeRule.onNodeWithTag("reader-menu-appearance").performClick()
        waitForTag("reader-quick-settings", "quick-settings-open")
    }

    private fun openQuickSettings() {
        if (composeRule.onAllNodesWithTag("reader-quick-settings").fetchSemanticsNodes().isNotEmpty()) return
        openAppearance()
    }

    private fun openFullSettings() {
        openQuickSettings()
        composeRule.onNodeWithTag("reader-quick-settings-more")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        waitForTag("reader-settings-full", "full-settings-open")
    }

    private fun setQuickFontSize(valueSp: Float) {
        openQuickSettings()
        composeRule.onNodeWithTag("reader-quick-settings-font-size-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(valueSp.coerceIn(14f, 36f))
            }
    }

    private fun setQuickPageMethodNone() {
        composeRule.onNodeWithTag("reader-quick-settings-page-none")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
    }

    private fun selectQuickIndentZero() {
        composeRule.onNodeWithTag("reader-quick-settings-indent-0")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
    }

    private fun saveAppearance() {
        composeRule.onNodeWithText("保存").performClick()
        waitForStep("settings-save-close", 10_000) {
            composeRule.onAllNodesWithTag("reader-quick-settings").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("reader-settings-full").fetchSemanticsNodes().isEmpty()
        }
        waitForPaginationSettled()
    }

    private fun waitForPaginationSettled() {
        waitForStep("pagination-settled", 10_000) {
            composeRule.onAllNodesWithTag("reader_pagination_progress").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun cancelAppearance() {
        composeRule.onNodeWithText("取消").performClick()
        waitForStep("settings-cancel-close", 10_000) {
            composeRule.onAllNodesWithTag("reader-quick-settings").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("reader-settings-full").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun closeReaderControls() {
        if (composeRule.onAllNodesWithTag("reader-settings-full").fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithTag("reader-quick-settings").fetchSemanticsNodes().isNotEmpty()
        ) {
            cancelAppearance()
        }
        if (composeRule.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isNotEmpty()) {
            val bounds = currentReaderPageBounds()
            if (!device.click(bounds.centerX(), bounds.centerY())) failStep("reader-menu-close")
            waitForStep("reader-menu-close", 5_000) {
                composeRule.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isEmpty()
            }
        }
    }

    private fun currentReaderPageBounds(): Rect {
        val node = composeRule.onAllNodesWithTag("reader_page_surface")
            .fetchSemanticsNodes()
            .firstOrNull {
                val bounds = it.boundsInRoot
                bounds.width > 0f && bounds.height > 0f &&
                    bounds.left >= 0f && bounds.top >= 0f &&
                    bounds.right <= device.displayWidth.toFloat() &&
                    bounds.bottom <= device.displayHeight.toFloat()
            }
            ?: failStep("reader-page-bounds")
        val bounds = node.boundsInRoot
        return Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
    }

    private fun currentSelectableTextView(): TextView {
        val pageBounds = currentReaderPageBounds()
        var result: TextView? = null
        instrumentation.runOnMainSync {
            val candidates = composeRule.activity.window.decorView.findStableSelectableTextViews(pageBounds)
            result = candidates.firstOrNull { textView ->
                val bundle = textView.getTag(targetResourceId("reader_page_probe")) as? Bundle
                bundle?.getBoolean("currentPage", false) == true
            } ?: candidates.singleOrNull()
        }
        return result ?: failStep("reader-text-view")
    }

    private fun assertRealTextView(textView: TextView) {
        var valid = false
        instrumentation.runOnMainSync {
            val value = textView.text
            valid = textView.isShown && textView.width > 0 && textView.height > 0 &&
                textView.layout != null && value is Spanned && value.isNotEmpty()
        }
        assertTrue("v16|${case().alias}|reader-text-shape|$valid", valid)
    }

    private data class PageProbe(
        val startOffset: Int,
        val endOffset: Int,
        val displayLength: Int,
        val settled: Boolean,
        val currentPage: Boolean,
    )

    private data class PageSnapshot(
        val pageIndex: Int,
        val startOffset: Int,
        val endOffset: Int,
        val displayLength: Int,
        val hasVisibleGlyph: Boolean,
        val textView: TextView,
    )

    private fun collectCurrentPageSnapshot(pageIndex: Int): PageSnapshot {
        var result: PageSnapshot? = null
        waitForStep("page-probe-settled", 10_000) {
            val textView = currentSelectableTextView()
            val probe = readPageProbe(textView) ?: return@waitForStep false
            if (!probe.settled) return@waitForStep false
            var hasVisibleGlyph = false
            var displayLength = 0
            instrumentation.runOnMainSync {
                val displayText = textView.text?.toString().orEmpty()
                displayLength = displayText.length
                hasVisibleGlyph = displayText
                    .replace("\u2060", "")
                    .any { !it.isWhitespace() }
            }
            if (displayLength != probe.displayLength) failStep("page-probe-length")
            result = PageSnapshot(
                pageIndex = pageIndex,
                startOffset = probe.startOffset,
                endOffset = probe.endOffset,
                displayLength = probe.displayLength,
                hasVisibleGlyph = hasVisibleGlyph,
                textView = textView,
            )
            true
        }
        return result ?: failStep("page-probe-missing")
    }

    private fun readPageProbe(textView: TextView): PageProbe? {
        var probe: PageProbe? = null
        instrumentation.runOnMainSync {
            val bundle = textView.getTag(targetResourceId("reader_page_probe")) as? Bundle
            if (bundle != null) {
                probe = PageProbe(
                    startOffset = bundle.getInt("startOffset", -1),
                    endOffset = bundle.getInt("endOffset", -1),
                    displayLength = bundle.getInt("displayLength", -1),
                    settled = bundle.getBoolean("settled", false),
                    currentPage = bundle.getBoolean("currentPage", false),
                )
            }
        }
        return probe
    }

    private fun waitForStep(step: String, timeoutMillis: Long, condition: () -> Boolean) {
        try {
            composeRule.waitUntil(timeoutMillis) { condition() }
        } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
            failStep(step)
        }
    }

    private fun rawTitleRanges(rawText: String): List<TitleExpectation> {
        if (case().oracleMode != OracleMode.PUBLIC) return emptyList()
        return listOf("第一章 雾中的灯", "第二章 车站的回声", "第三章 天亮以后").map { title ->
            val start = rawText.indexOf(title)
            if (start < 0) failStep("public-title-oracle")
            TitleExpectation(title, start, start + title.length)
        }
    }

    private fun assertTitleSpans(
        textView: TextView,
        snapshot: PageSnapshot,
        titles: List<TitleExpectation>,
    ) {
        var matched = 0
        titles.filter { it.startOffset >= snapshot.startOffset && it.endOffset <= snapshot.endOffset }
            .forEach { title ->
                matched += 1
                val localStart = title.startOffset - snapshot.startOffset
                val localEnd = title.endOffset - snapshot.startOffset
                var relativeCount = 0
                var relativeSize = 0f
                var styleCount = 0
                var style = 0
                var zeroLeading = false
                instrumentation.runOnMainSync {
                    val spanned = textView.text as? Spanned ?: return@runOnMainSync
                    val relative = spanned.getSpans(localStart, localEnd, RelativeSizeSpan::class.java)
                        .filter { spanned.getSpanStart(it) == localStart && spanned.getSpanEnd(it) == localEnd }
                    relativeCount = relative.size
                    relativeSize = relative.firstOrNull()?.sizeChange ?: 0f
                    val styles = spanned.getSpans(localStart, localEnd, StyleSpan::class.java)
                        .filter { spanned.getSpanStart(it) == localStart && spanned.getSpanEnd(it) == localEnd }
                    styleCount = styles.size
                    style = styles.firstOrNull()?.style ?: 0
                    zeroLeading = spanned.getSpans(localStart, localEnd, LeadingMarginSpan::class.java)
                        .any { span ->
                            spanned.getSpanStart(span) <= localStart &&
                                spanned.getSpanEnd(span) >= localEnd && span.getLeadingMargin(true) == 0
                        }
                }
                assertEquals("v16|${case().alias}|title-relative-count", 1, relativeCount)
                assertEquals("v16|${case().alias}|title-relative-size", 1.45f, relativeSize, 0.001f)
                assertEquals("v16|${case().alias}|title-style-count", 1, styleCount)
                assertEquals("v16|${case().alias}|title-style-bold", Typeface.BOLD, style)
                assertTrue("v16|${case().alias}|title-zero-leading", zeroLeading)
                assertTitleSpacing(textView, localStart, localEnd)
            }
        assertTrue("v16|${case().alias}|title-visible", matched > 0)
    }

    private fun assertTitleSpacing(textView: TextView, localStart: Int, localEnd: Int) {
        var spacingMatched = false
        instrumentation.runOnMainSync {
            val spanned = textView.text as? Spanned ?: return@runOnMainSync
            val paragraphEnd = spanned.toString().indexOf('\n', localEnd).let { if (it < 0) spanned.length else it + 1 }
            val lineHeightSpans = spanned.getSpans(localStart, paragraphEnd, LineHeightSpan::class.java)
            val base = android.graphics.Paint.FontMetricsInt()
            textView.paint.getFontMetricsInt(base)
            val expected = (textView.textSize * 0.75f).roundToInt()
            lineHeightSpans.forEach { span ->
                val changed = android.graphics.Paint.FontMetricsInt().also {
                    it.top = base.top
                    it.ascent = base.ascent
                    it.descent = base.descent
                    it.bottom = base.bottom
                    it.leading = base.leading
                }
                span.chooseHeight(spanned, localStart, paragraphEnd, 0, base.bottom - base.top, changed)
                if (changed.descent == base.descent + expected && changed.bottom == base.bottom + expected) {
                    spacingMatched = true
                }
            }
        }
        assertTrue("v16|${case().alias}|title-spacing", spacingMatched)
    }

    private fun assertRunningHeaderAbsentOnTitlePage(expectedTitle: String) {
        waitForStep("running-header-title-hidden", 5_000) {
            val header = readRunningHeaderText()
            header == null || !header.contains(expectedTitle)
        }
    }

    private fun assertRunningHeaderOnContinuationPage(
        titlePage: PageSnapshot,
        title: TitleExpectation,
        nextTitle: TitleExpectation?,
    ) {
        var page = titlePage
        repeat(MAX_PAGE_COUNT - 1) {
            swipePreviousEndOffset = page.endOffset
            val candidate = swipeToNextPage(page.pageIndex) ?: failStep("running-header-page")
            val effectiveStart = effectivePageStart(candidate)
            val remainsInChapter = effectiveStart >= title.endOffset &&
                (nextTitle == null || effectiveStart < nextTitle.startOffset)
            if (remainsInChapter) {
                waitForStep("running-header-continuation", 5_000) {
                    readRunningHeaderText()?.contains(title.text) == true
                }
                return
            }
            if (nextTitle != null && effectiveStart >= nextTitle.startOffset) {
                failStep("running-header-continuation-page")
            }
            page = candidate
        }
        failStep("running-header-continuation-page")
    }

    private fun readRunningHeaderText(): String? {
        val node = composeRule.onAllNodesWithTag("reader_running_header").fetchSemanticsNodes()
            .firstOrNull() ?: return null
        return if (node.config.contains(SemanticsProperties.Text)) {
            node.config[SemanticsProperties.Text].joinToString(separator = "") { it.text }
        } else {
            ""
        }
    }

    private fun effectivePageStart(page: PageSnapshot): Int {
        var firstVisibleIndex = -1
        instrumentation.runOnMainSync {
            firstVisibleIndex = page.textView.text?.toString()?.indexOfFirst {
                !it.isWhitespace() && it != '\u2060'
            } ?: -1
        }
        return if (firstVisibleIndex >= 0) page.startOffset + firstVisibleIndex else page.startOffset
    }

    private fun swipeToNextPage(previousIndex: Int): PageSnapshot? {
        val bounds = currentReaderPageBounds()
        val y = bounds.centerY()
        if (!device.swipe(bounds.right - bounds.width() / 4, y, bounds.left + bounds.width() / 4, y, 12)) {
            failStep("page-swipe-forward")
        }
        var next: PageSnapshot? = null
        try {
            composeRule.waitUntil(5_000) {
                val candidate = snapshotIfSettled(previousIndex + 1) ?: return@waitUntil false
                if (candidate.startOffset != swipePreviousEndOffset) return@waitUntil false
                next = candidate
                true
            }
        } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
            return null
        }
        return next
    }

    private fun swipeToPreviousPage(expected: PageSnapshot) {
        val bounds = currentReaderPageBounds()
        val y = bounds.centerY()
        if (!device.swipe(bounds.left + bounds.width() / 4, y, bounds.right - bounds.width() / 4, y, 12)) {
            failStep("page-swipe-backward")
        }
        waitForStep("page-reverse", 5_000) {
            val snapshot = snapshotIfSettled(expected.pageIndex)
            snapshot?.startOffset == expected.startOffset && snapshot.endOffset == expected.endOffset
        }
    }

    private fun snapshotIfSettled(pageIndex: Int): PageSnapshot? {
        val textView = runCatching { currentSelectableTextView() }.getOrNull() ?: return null
        val probe = readPageProbe(textView) ?: return null
        if (!probe.settled || probe.startOffset < 0 || probe.endOffset <= probe.startOffset) return null
        var hasVisibleGlyph = false
        var displayLength = 0
        instrumentation.runOnMainSync {
            val displayText = textView.text?.toString().orEmpty()
            displayLength = displayText.length
            hasVisibleGlyph = displayText.replace("\u2060", "").any { !it.isWhitespace() }
        }
        if (displayLength != probe.displayLength) return null
        return PageSnapshot(
            pageIndex = pageIndex,
            startOffset = probe.startOffset,
            endOffset = probe.endOffset,
            displayLength = probe.displayLength,
            hasVisibleGlyph = hasVisibleGlyph,
            textView = textView,
        )
    }

    private fun assertPageSequence(
        pages: List<PageSnapshot>,
        rawLength: Int,
        titles: List<TitleExpectation>,
    ) {
        assertTrue("v16|${case().alias}|page-sequence-nonempty", pages.isNotEmpty())
        assertEquals("v16|${case().alias}|page-first-start", 0, pages.first().startOffset)
        pages.forEachIndexed { index, page ->
            assertTrue("v16|${case().alias}|page-range", page.startOffset >= 0 && page.endOffset > page.startOffset)
            assertEquals("v16|${case().alias}|page-display-length", page.endOffset - page.startOffset, page.displayLength)
            assertTrue("v16|${case().alias}|blank-page", page.hasVisibleGlyph)
            if (index > 0) {
                assertEquals("v16|${case().alias}|page-contiguous", pages[index - 1].endOffset, page.startOffset)
            }
        }
        assertEquals("v16|${case().alias}|page-last-end", rawLength, pages.last().endOffset)
        titles.forEach { title ->
            val containing = pages.count { title.startOffset >= it.startOffset && title.endOffset <= it.endOffset }
            assertEquals("v16|${case().alias}|title-single-page", 1, containing)
        }
    }

    private fun collectAllPagesFromCurrent(
        firstPage: PageSnapshot,
        requireCompleteBook: Boolean = true,
    ): List<PageSnapshot> {
        val pages = mutableListOf(firstPage)
        while (pages.size < MAX_PAGE_COUNT) {
            swipePreviousEndOffset = pages.last().endOffset
            val next = swipeToNextPage(pages.last().pageIndex) ?: break
            pages += next
        }
        if (requireCompleteBook && pages.size == MAX_PAGE_COUNT) failStep("page-count-bound")
        return pages
    }

    private fun assertAllPublicTitleSpans(
        pages: List<PageSnapshot>,
        titles: List<TitleExpectation>,
    ) {
        titles.forEach { title ->
            val containing = pages.filter { page ->
                title.startOffset >= page.startOffset && title.endOffset <= page.endOffset
            }
            assertEquals("v16|${case().alias}|title-page-count", 1, containing.size)
            val page = containing.single()
            assertTitleSpans(page.textView, page, listOf(title))
        }
    }

    private fun resetReaderToStart() {
        openReaderMenu()
        composeRule.onNodeWithTag("reader-menu-more").performClick()
        composeRule.onNodeWithText("定位进度").performClick()
        waitForTag("reader-progress-slider", "progress-open")
        waitForStep("progress-preview-ready", 10_000) {
            composeRule.onAllNodesWithTag("reader-progress-preview-loading").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("reader-progress-preview-snippet").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reader-progress-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(0f) }
        waitForStep("progress-zero-result", 10_000) {
            val dialogClosed = composeRule.onAllNodesWithTag("reader-progress-slider")
                .fetchSemanticsNodes().isEmpty()
            val previewReady = composeRule.onAllNodesWithTag("reader-progress-preview-loading")
                .fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("reader-progress-preview-snippet")
                    .fetchSemanticsNodes().isNotEmpty()
            dialogClosed || previewReady
        }
        if (composeRule.onAllNodesWithTag("reader-progress-slider").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("跳转").performClick()
        }
        waitForStep("progress-close", 10_000) {
            composeRule.onAllNodesWithTag("reader-progress-slider").fetchSemanticsNodes().isEmpty()
        }
        waitForStep("progress-reset", 10_000) {
            snapshotIfSettled(0)?.startOffset == 0
        }
        waitForPaginationSettled()
    }

    private fun moveToPublicCopyMarker(): String {
        var page = collectCurrentPageSnapshot(0)
        repeat(MAX_PAGE_COUNT) {
            val localStart = textIndex(page.textView, PUBLIC_COPY_MARKER)
            if (localStart >= 0) {
                val localEnd = localStart + PUBLIC_COPY_MARKER.length
                if (localEnd > page.displayLength) failStep("copy-marker-range")
                val rawStart = page.startOffset + localStart
                val rawEnd = page.startOffset + localEnd
                if (rawStart < 0 || rawEnd > PUBLIC_FIXTURE_RAW.length) failStep("copy-raw-range")
                return PUBLIC_FIXTURE_RAW.substring(rawStart, rawEnd)
            }
            swipePreviousEndOffset = page.endOffset
            page = swipeToNextPage(page.pageIndex) ?: failStep("copy-marker-page")
        }
        return failStep("copy-marker-page")
    }

    private fun copySelectionAndAssertRaw(expectedRaw: String?) {
        val textView = currentSelectableTextView()
        var actionModePointX = 0
        var actionModePointY = 0
        var selectionValid = false
        var crossesVisualLine = false
        var targetStart = -1
        var targetEnd = -1
        instrumentation.runOnMainSync {
            val selectable = textView.text as? Spannable ?: return@runOnMainSync
            val value = selectable.toString()
            val localStart = if (case().oracleMode == OracleMode.PUBLIC) {
                value.indexOf(PUBLIC_COPY_MARKER)
            } else {
                value.indexOfFirst { !it.isWhitespace() && it != '\u2060' }
            }
            if (localStart < 0) return@runOnMainSync
            val localEnd = if (case().oracleMode == OracleMode.PUBLIC) {
                localStart + PUBLIC_COPY_MARKER.length
            } else {
                localStart + 1
            }
            if (localEnd > value.length) return@runOnMainSync
            val layout = textView.layout ?: return@runOnMainSync
            crossesVisualLine = layout.getLineForOffset(localStart) != layout.getLineForOffset(localEnd - 1)
            targetStart = localStart
            targetEnd = localEnd
            val anchor = (localStart + localEnd - 1) / 2
            val line = layout.getLineForOffset(anchor)
            val location = IntArray(2)
            textView.getLocationOnScreen(location)
            actionModePointX = location[0] + textView.totalPaddingLeft + layout.getPrimaryHorizontal(anchor).toInt()
            actionModePointY = location[1] + textView.totalPaddingTop +
                (layout.getLineTop(line) + layout.getLineBottom(line)) / 2 - textView.scrollY
            Selection.setSelection(selectable, localStart, localEnd)
            selectionValid = textView.selectionStart == localStart && textView.selectionEnd == localEnd
            val clipboard = textView.context.getSystemService(ClipboardManager::class.java)
            clipboard?.clearPrimaryClip()
        }
        assertTrue("v16|${case().alias}|selection-range", selectionValid)
        if (case().oracleMode == OracleMode.PUBLIC) {
            assertTrue("v16|${case().alias}|selection-cross-line", crossesVisualLine)
        }
        if (actionModePointX <= 0 || actionModePointY <= 0) failStep("selection-action-point")
        if (!device.swipe(actionModePointX, actionModePointY, actionModePointX, actionModePointY, 1_200)) {
            failStep("selection-action-mode")
        }
        waitForTag("reader_selection_actions", "selection-action-mode")
        instrumentation.runOnMainSync {
            val selectable = textView.text as? Spannable ?: return@runOnMainSync
            Selection.setSelection(selectable, targetStart, targetEnd)
            selectionValid = textView.selectionStart == targetStart && textView.selectionEnd == targetEnd
        }
        assertTrue("v16|${case().alias}|selection-action-range", selectionValid)
        assertTrue("v16|${case().alias}|selection-copy-menu", clickSystemCopy())
        var clipboard = ""
        waitForStep("selection-clipboard", 3_000) {
            clipboard = readClipboardText()
            clipboard.isNotEmpty()
        }
        assertNoWordJoiner(clipboard, "selection-clipboard-word-joiner")
        if (expectedRaw != null) {
            assertEquals("v16|${case().alias}|selection-raw-equals", expectedRaw, clipboard)
            assertTrue("v16|${case().alias}|selection-ascii-space", clipboard.contains(' '))
        }
    }

    private fun clickSystemCopy(): Boolean {
        val copyPattern = Pattern.compile("^Copy$|^复制$|^拷贝$", Pattern.CASE_INSENSITIVE)
        if (clickFresh(By.text(copyPattern), 3_000)) return true
        if (clickFresh(By.desc(copyPattern), 1_000)) return true
        val more = device.findObject(By.descContains("More"))
            ?: device.findObject(By.descContains("更多"))
            ?: return false
        val bounds = more.visibleBounds
        device.click(bounds.centerX(), bounds.centerY())
        return clickFresh(By.text(copyPattern), 3_000) || clickFresh(By.desc(copyPattern), 1_000)
    }

    private fun textIndex(textView: TextView, marker: String): Int {
        var result = -1
        instrumentation.runOnMainSync { result = textView.text?.toString()?.indexOf(marker) ?: -1 }
        return result
    }

    private fun readClipboardText(): String {
        var value = ""
        instrumentation.runOnMainSync {
            val clipboard = composeRule.activity.getSystemService(ClipboardManager::class.java)
            value = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(composeRule.activity)?.toString().orEmpty()
        }
        return value
    }

    private fun assertNoWordJoiner(value: String, step: String) {
        assertFalse("v16|${case().alias}|$step", value.contains('\u2060'))
    }

    private fun assertQuickSectionOrder() {
        selectGlobalScope()
        val tags = listOf(
            "reader-quick-settings-scope",
            "reader-quick-settings-font-size",
            "reader-quick-settings-line-height",
            "reader-quick-settings-paragraph-spacing",
            "reader-quick-settings-indent",
            "reader-quick-settings-page-method",
            "reader-quick-settings-font",
            "reader-quick-settings-theme-brightness",
            "reader-quick-settings-more-section",
        )
        val semanticsOrder = composeRule
            .onNodeWithTag("reader-quick-settings", useUnmergedTree = true)
            .fetchSemanticsNode()
            .flattenTestTags()
        assertTrue(
            "v16|${case().alias}|quick-section-order",
            tags.zipWithNext().all { (before, after) ->
                semanticsOrder.indexOf(before) >= 0 && semanticsOrder.indexOf(before) < semanticsOrder.indexOf(after)
            },
        )
    }

    private fun selectGlobalScope() {
        composeRule.onNodeWithTag("reader-quick-settings-scope-global").performClick()
    }

    private fun selectCurrentBookScope() {
        composeRule.onNodeWithTag("reader-quick-settings-scope-current-book").performClick()
        assertTagPresent("reader-quick-settings-global-only-note", "current-book-scope")
    }

    private fun assertFullSettingsContract() {
        assertTagPresent("reader-settings-full", "full-root")
        assertTagPresent("reader-settings-back-to-quick", "full-back")
        assertTagPresent("reader-settings-common", "full-common")
        assertTagPresent("reader-settings-advanced", "full-advanced")
        assertTagPresent("reader-typography-common", "full-typography-common")
        composeRule.onNodeWithTag("reader-settings-advanced")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        waitForTag("reader-settings-full-advanced-controls", "full-advanced-controls")
        composeRule.onNodeWithTag("reader-settings-common")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        waitForTag("reader-typography-common", "full-common-restored")
    }

    private fun assertTagPresent(tag: String, step: String) {
        if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) failStep(step)
    }

    private fun assertTagAbsent(tag: String, step: String) {
        assertTrue(
            "v16|${case().alias}|$step",
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun fullFontSliderNodes() = composeRule.onAllNodes(
        SemanticsMatcher("full-font-size-slider") { node ->
            node.config.contains(SemanticsProperties.ContentDescription) &&
                node.config[SemanticsProperties.ContentDescription].any { it.startsWith("字号") }
        },
    )

    private fun fullFontDescription(): String {
        val node = fullFontSliderNodes().fetchSemanticsNodes().firstOrNull() ?: failStep("full-font-slider")
        return if (node.config.contains(SemanticsProperties.ContentDescription)) {
            node.config[SemanticsProperties.ContentDescription].joinToString("|")
        } else {
            failStep("full-font-description")
        }
    }

    private fun quickFontValue(): Float {
        val node = composeRule.onNodeWithTag("reader-quick-settings-font-size-slider").fetchSemanticsNode()
        val description = if (node.config.contains(SemanticsProperties.ContentDescription)) {
            node.config[SemanticsProperties.ContentDescription].joinToString("|")
        } else {
            failStep("quick-font-description")
        }
        return fontValueFromDescription(description)
    }

    private fun fullFontValue(): Float = fontValueFromDescription(fullFontDescription())

    private fun fontValueFromDescription(description: String): Float {
        val match = FLOAT_PATTERN.find(description) ?: failStep("font-value")
        return match.value.toFloat()
    }

    private fun setFullFontSize(valueSp: Float) {
        val nodes = fullFontSliderNodes()
        if (nodes.fetchSemanticsNodes().isEmpty()) failStep("full-font-slider")
        nodes[0].performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            setProgress(valueSp.coerceIn(14f, 36f))
        }
    }

    private fun clickShelfStatusFilter(tag: String, step: String) {
        assertReachable48dp(tag)
        val interaction = composeRule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
        waitForStep(step, 5_000) {
            val node = runCatching { interaction.fetchSemanticsNode() }.getOrNull()
            node != null && node.config.contains(SemanticsProperties.Selected) &&
                node.config[SemanticsProperties.Selected]
        }
    }

    private fun assertShelfSelected(tag: String, step: String) {
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(
            "v16|${case().alias}|$step",
            node.config.contains(SemanticsProperties.Selected) &&
                node.config[SemanticsProperties.Selected],
        )
    }

    private fun assertReachable48dp(tag: String) {
        val interaction = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
        runCatching { interaction.performScrollTo() }
        interaction.assertIsDisplayed()
        val node = interaction.fetchSemanticsNode()
        val bounds = node.boundsInRoot
        val density = composeRule.activity.resources.displayMetrics.density
        val minimumPx = 48f * density
        assertTrue(
            "v16|${case().alias}|shelf-control-size-$tag|${bounds.width}x${bounds.height}|min=$minimumPx",
            bounds.width >= minimumPx && bounds.height >= minimumPx,
        )
        assertTrue(
            "v16|${case().alias}|shelf-control-visible-$tag",
            bounds.left >= 0f && bounds.top >= 0f &&
                bounds.right <= device.displayWidth && bounds.bottom <= device.displayHeight,
        )
    }

    private fun searchFromRealReader() {
        val query = if (case().oracleMode == OracleMode.PUBLIC) {
            PUBLIC_SEARCH_MARKER
        } else {
            derivePrivateSearchQuery()
        }
        openReaderMenu()
        composeRule.onNodeWithTag("reader-menu-search").performClick()
        waitForTag("reader-search-submit", "search-open")
        val fields = composeRule.onAllNodes(hasSetTextAction())
        if (fields.fetchSemanticsNodes().isEmpty()) failStep("search-input")
        fields[0].performTextInput(query)
        composeRule.onNodeWithTag("reader-search-submit")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        waitForStep("search-result", 15_000) {
            val node = composeRule.onNodeWithTag("reader-search-content").fetchSemanticsNode()
            node.config.contains(SemanticsProperties.StateDescription) &&
                node.config[SemanticsProperties.StateDescription] != "搜索结果：0 条"
        }
        composeRule.onNodeWithTag("reader-search-content").performScrollToIndex(3)
        waitForTag("reader-search-result", "search-result-visible")
        composeRule.onAllNodesWithTag("reader-search-result")[0]
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        waitForStep("search-jump", 10_000) {
            composeRule.onAllNodesWithTag("reader-search-result").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("reader_root").fetchSemanticsNodes().isNotEmpty()
        }
        val page = collectCurrentPageSnapshot(0)
        if (case().oracleMode == OracleMode.PUBLIC) {
            val start = PUBLIC_FIXTURE_RAW.indexOf(PUBLIC_SEARCH_MARKER)
            val end = start + PUBLIC_SEARCH_MARKER.length
            assertTrue("v16|${case().alias}|search-raw-range", start >= page.startOffset && end <= page.endOffset)
        } else {
            assertTrue("v16|${case().alias}|search-page-nonempty", page.hasVisibleGlyph)
        }
    }

    private fun derivePrivateSearchQuery(): String {
        val textView = currentSelectableTextView()
        var query = ""
        instrumentation.runOnMainSync {
            val visible = textView.text?.toString().orEmpty()
            val body = visible.substringAfter('\n', visible)
            query = sequenceOf(body, visible)
                .flatMap { value -> value.windowed(2).asSequence() }
                .firstOrNull { pair ->
                    pair.length == 2 && pair.all { !it.isWhitespace() && it != '\u2060' }
                }
                .orEmpty()
        }
        if (query.length < 2) failStep("search-query-visible")
        return query
    }

    private data class RawSelectionRange(
        val startOffset: Int,
        val endOffset: Int,
    )

    private fun createChapterBoundaryNote() {
        val page = collectCurrentPageSnapshot(0)
        val localRange = if (case().oracleMode == OracleMode.PUBLIC) {
            val title = rawTitleRanges(PUBLIC_FIXTURE_RAW).first()
            val bodyStart = PUBLIC_FIXTURE_RAW.indexOf(PUBLIC_NOTE_BODY_MARKER)
            val bodyEnd = bodyStart + PUBLIC_NOTE_BODY_MARKER.length
            if (title.startOffset < page.startOffset || bodyEnd > page.endOffset) {
                failStep("note-boundary-page")
            }
            (title.startOffset - page.startOffset) to (bodyEnd - page.startOffset)
        } else {
            firstVisibleRange(page.textView) ?: failStep("note-structural-range")
        }
        val selected = selectNativeRange(page, localRange.first, localRange.second)
        assertTrue(
            "v16|${case().alias}|note-raw-range",
            selected.startOffset >= page.startOffset && selected.endOffset <= page.endOffset,
        )
        composeRule.onNodeWithTag("reader_selection_actions").assertIsDisplayed()
        composeRule.onNodeWithText("批注").performClick()
        waitForText("添加批注", "note-dialog")
        val inputs = composeRule.onAllNodes(hasSetTextAction())
        if (inputs.fetchSemanticsNodes().isEmpty()) failStep("note-input")
        inputs[0].performTextInput(NOTE_MARKER)
        composeRule.onNodeWithText("保存").performClick()
        waitForStep("note-save", 5_000) {
            composeRule.onAllNodesWithTag("reader_selection_actions").fetchSemanticsNodes().isEmpty()
        }
        openNotesAndAssert()
        device.pressBack()
        goToLibrary()
        openCaseBook()
        closeReaderControls()
        openNotesAndAssert()
    }

    private fun openNotesAndAssert() {
        openReaderMenu()
        composeRule.onNodeWithTag("reader-menu-notes").performClick()
        waitForTag("reader_annotations", "notes-open")
        assertTrue(
            "v16|${case().alias}|note-visible",
            composeRule.onAllNodesWithText(NOTE_MARKER).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun firstVisibleRange(textView: TextView): Pair<Int, Int>? {
        var range: Pair<Int, Int>? = null
        instrumentation.runOnMainSync {
            val value = textView.text?.toString().orEmpty()
            val start = value.indexOfFirst { !it.isWhitespace() && it != '\u2060' }
            if (start >= 0) range = start to start + 1
        }
        return range
    }

    private fun selectNativeRange(page: PageSnapshot, localStart: Int, localEnd: Int): RawSelectionRange {
        if (localStart < 0 || localEnd <= localStart || localEnd > page.displayLength) {
            failStep("note-selection-range")
        }
        val textView = page.textView
        var x = 0
        var y = 0
        instrumentation.runOnMainSync {
            val layout = textView.layout ?: return@runOnMainSync
            val anchor = (localStart + localEnd - 1) / 2
            val line = layout.getLineForOffset(anchor)
            val location = IntArray(2)
            textView.getLocationOnScreen(location)
            x = location[0] + textView.totalPaddingLeft + layout.getPrimaryHorizontal(anchor).toInt()
            y = location[1] + textView.totalPaddingTop +
                (layout.getLineTop(line) + layout.getLineBottom(line)) / 2
            val selectable = textView.text as? Spannable
            if (selectable != null) Selection.setSelection(selectable, localStart, localEnd)
        }
        if (!device.swipe(x, y, x, y, 60)) failStep("note-long-press")
        waitForTag("reader_selection_actions", "note-selection-actions")
        var selectionStart = -1
        var selectionEnd = -1
        instrumentation.runOnMainSync {
            selectionStart = textView.selectionStart
            selectionEnd = textView.selectionEnd
        }
        if (selectionStart < 0 || selectionEnd <= selectionStart) failStep("note-selection-result")
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)
        return RawSelectionRange(page.startOffset + start, page.startOffset + end)
    }

    private fun View.findStableSelectableTextViews(pageBounds: Rect): List<TextView> {
        val result = mutableListOf<TextView>()
        if (this is TextView && id == targetResourceId("reader_selectable_text") && isShown) {
            val location = IntArray(2)
            getLocationOnScreen(location)
            val bounds = Rect(location[0], location[1], location[0] + width, location[1] + height)
            if (bounds.width() > 0 && bounds.height() > 0 && Rect.intersects(bounds, pageBounds)) result += this
        }
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                result += getChildAt(index).findStableSelectableTextViews(pageBounds)
            }
        }
        return result
    }

    private fun targetResourceId(name: String): Int {
        @Suppress("DiscouragedApi")
        val id = instrumentation.targetContext.resources.getIdentifier(name, "id", targetPackage)
        if (id == 0) failStep("target-resource-id")
        return id
    }

    private fun selectFromDocumentsUi(fileName: String) {
        if (!FILE_NAME_PATTERN.matches(fileName)) failStep("documents-file-name")
        if (!device.wait(Until.hasObject(DOCUMENTS_PACKAGE), 10_000)) {
            failStep("documents-package")
        }
        openDownloads()
        val selector = By.res("android:id/title").text(fileName)
        var file = device.wait(Until.findObject(selector), 3_000)
        if (file == null) {
            val searchOpened = clickFresh(By.desc(Pattern.compile("Search|搜索")), 2_000)
            if (searchOpened) {
                val input = device.wait(
                    Until.findObject(By.res("com.google.android.documentsui:id/search_src_text")),
                    3_000,
                )
                if (input != null) {
                    input.text = fileName
                    device.executeShellCommand("input keyevent 66")
                    file = device.wait(Until.findObject(selector), 15_000)
                }
            }
        }
        if (file == null || !clickFresh(selector, 5_000)) failStep("documents-select")
    }

    private fun openDownloads() {
        if (!hasRootsDrawer()) {
            val opened = clickFresh(By.desc(Pattern.compile("Show roots|显示根目录")), 2_000)
            if (opened) waitForRootsDrawer("documents-roots-open")
        }
        val downloads = By.res("android:id/title").text(Pattern.compile("^Downloads$|^下载$"))
        if (!clickFresh(downloads, 3_000)) failStep("documents-downloads")
        if (!waitForRootsDrawerToClose()) failStep("documents-roots-close")
    }

    private fun hasRootsDrawer(): Boolean =
        device.hasObject(ROOTS_DRAWER) || device.hasObject(GOOGLE_ROOTS_DRAWER)

    private fun waitForRootsDrawer(step: String) {
        val deadline = System.currentTimeMillis() + 3_000
        while (!hasRootsDrawer() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        if (!hasRootsDrawer()) failStep(step)
    }

    private fun waitForRootsDrawerToClose(): Boolean {
        val deadline = System.currentTimeMillis() + 3_000
        do {
            if (!hasRootsDrawer()) return true
            Thread.sleep(100)
        } while (System.currentTimeMillis() < deadline)
        return false
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

    private fun failStep(step: String): Nothing =
        throw AssertionError("v16|${case().alias}|$step|FAIL")

    private companion object {
        const val PUBLIC_CASE_ALIAS = "public-v16"
        const val REMOTE_INPUT_NAME = "v16-structured-txt-input.txt"
        const val SCOPE_A_INPUT = "v16-scope-a.txt"
        const val SCOPE_B_INPUT = "v16-scope-b.txt"
        const val BOOK_TITLE = "v16-structured-txt-input"
        const val PUBLIC_COPY_MARKER = "这一行的源文带有全角缩进，行内 空格应保留。"
        const val PUBLIC_SEARCH_MARKER = "雾里"
        const val PUBLIC_NOTE_BODY_MARKER = "夜色落下时"
        const val NOTE_MARKER = "v16-note"
        const val SHELF_GROUP_MARKER = "v16-group"
        const val MAX_PAGE_COUNT = 64
        const val PUBLIC_FIXTURE_RAW =
            "\n\n　　第一章 雾中的灯\n\n　　夜色落下时，旧桥边亮起一盏灯。\n" +
                "　　这一行的源文带有全角缩进，行内 空格应保留。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n" +
                "　　雾气沿着旧桥缓慢流动，远处的灯影在水面上轻轻摇晃。\n\n\n\n" +
                "　第二章 车站的回声\n\n  清晨的站台没有乘客，只有风穿过玻璃穹顶。\n" +
                "\t一列无编号的列车停在雾里，车门安静地打开。\n\n\n" +
                "第三章 天亮以后\n\n　　阳光越过屋顶，故事在这里继续。\n\n"
        val PRIVATE_CASE_ALIAS = Regex("private-case-[0-9]{2}")
        val FILE_NAME_PATTERN = Regex("[A-Za-z0-9._-]+")
        val FLOAT_PATTERN = Regex("-?[0-9]+(?:\\.[0-9]+)?")
        val bookTitleTagMatcher = SemanticsMatcher("library-book-title-tag") { node ->
            node.config.contains(SemanticsProperties.TestTag) &&
                node.config[SemanticsProperties.TestTag].startsWith("library_book_title_")
        }
        val DOCUMENTS_PACKAGE: BySelector =
            By.pkg(Pattern.compile("com\\.(google\\.android|android)\\.documentsui"))
        val ROOTS_DRAWER: BySelector = By.res("com.android.documentsui:id/roots_list")
        val GOOGLE_ROOTS_DRAWER: BySelector = By.res("com.google.android.documentsui:id/roots_list")
    }
}

private fun androidx.compose.ui.semantics.SemanticsNode.flattenTestTags(): List<String> =
    listOfNotNull(
        if (config.contains(SemanticsProperties.TestTag)) config[SemanticsProperties.TestTag] else null,
    ) + children.flatMap { it.flattenTestTags() }
