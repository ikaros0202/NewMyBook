package com.xinyue.reader.feature.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xinyue.reader.core.domain.model.ImportedFont
import com.xinyue.reader.core.domain.model.ReaderColorTheme
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import com.xinyue.reader.core.domain.model.ReaderThemePreset
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSettingsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun startsInQuickMode_andMoreSettingsReusesTheDraft() {
        val sepia = ReaderColorTheme.SEPIA.builtInPalette()
        var edit by mutableStateOf(
            ReaderAppearanceEdit(
                scope = ReaderSettingsScope.GLOBAL,
                original = ReaderSettings(),
                preview = ReaderSettings(),
                originalStableAnchorOffset = 0,
            ),
        )
        compose.setContent {
            MaterialTheme {
                ReaderSettingsSheet(
                    edit = edit,
                    importedFonts = emptyList(),
                    themes = ReaderThemeManager.BUILT_IN_THEMES,
                    activeThemeId = "built-in-paper",
                    themeSchedule = ReaderThemeSchedule(),
                    manualThemeOverride = null,
                    bookTitle = "测试书",
                    bookOverrides = ReaderSettingsOverrides(),
                    errorMessage = null,
                    onScopeChanged = { scope -> edit = edit.copy(scope = scope) },
                    onPreview = { settings -> edit = edit.copy(preview = settings) },
                    onCommit = {},
                    onCancel = {},
                    onClearCurrentBookOverrides = {},
                    onApplyTheme = {},
                    onCreateTheme = {},
                    onCopyTheme = { _, _ -> },
                    onRenameTheme = { _, _ -> },
                    onUpdateTheme = {},
                    onDeleteTheme = {},
                    onUpdateThemeSchedule = {},
                    onImportFont = {},
                    onRemoveImportedFont = {},
                )
            }
        }

        compose.onNodeWithTag("reader-quick-settings").assertIsDisplayed()
        compose.onAllNodesWithTag("reader-theme-built-in-paper").assertCountEquals(0)
        compose.onNodeWithTag("reader-quick-settings-theme-sepia").performClick()
        compose.onNodeWithTag("reader-quick-settings-more").assertIsDisplayed()
        compose.onNodeWithTag("reader-quick-settings-more").performClick()
        compose.onAllNodesWithTag("reader-quick-settings").assertCountEquals(0)
        compose.onNodeWithTag("reader-theme-built-in-paper").assertIsDisplayed()
        assertTrue(edit.preview.backgroundArgb == sepia.backgroundArgb)
        compose.onNodeWithTag("reader-settings-back-to-quick").performClick()
        compose.onNodeWithTag("reader-quick-settings").assertIsDisplayed()
        assertTrue(edit.preview.backgroundArgb == sepia.backgroundArgb)
    }

    @Test
    fun currentBookQuickModeHidesGlobalOnlyPageAndBrightnessControls() {
        compose.setContent {
            MaterialTheme {
                ReaderSettingsSheet(
                    edit = ReaderAppearanceEdit(
                        scope = ReaderSettingsScope.CURRENT_BOOK,
                        original = ReaderSettings(),
                        preview = ReaderSettings(),
                        originalStableAnchorOffset = 0,
                    ),
                    importedFonts = emptyList(),
                    themes = ReaderThemeManager.BUILT_IN_THEMES,
                    activeThemeId = "built-in-paper",
                    themeSchedule = ReaderThemeSchedule(),
                    manualThemeOverride = null,
                    bookTitle = "测试书",
                    bookOverrides = ReaderSettingsOverrides(),
                    errorMessage = null,
                    onScopeChanged = {},
                    onPreview = {},
                    onCommit = {},
                    onCancel = {},
                    onClearCurrentBookOverrides = {},
                    onApplyTheme = {},
                    onCreateTheme = {},
                    onCopyTheme = { _, _ -> },
                    onRenameTheme = { _, _ -> },
                    onUpdateTheme = {},
                    onDeleteTheme = {},
                    onUpdateThemeSchedule = {},
                    onImportFont = {},
                    onRemoveImportedFont = {},
                )
            }
        }

        compose.onAllNodesWithTag("reader-quick-settings-page-method").assertCountEquals(0)
        compose.onAllNodesWithTag("reader-quick-settings-brightness-slider").assertCountEquals(0)
        compose.onAllNodesWithTag("reader-quick-settings-brightness-toggle").assertCountEquals(0)
        compose.onNodeWithTag("reader-quick-settings-global-only-note").assertIsDisplayed()
    }

    @Test
    fun fullModePlacesCommonTypographyBeforeThemeCards_andSaveRemainsAvailable() {
        var committed = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme {
                    ReaderSettingsSheet(
                        edit = ReaderAppearanceEdit(
                            scope = ReaderSettingsScope.GLOBAL,
                            original = ReaderSettings(),
                            preview = ReaderSettings(fontSizeSp = 22f),
                            originalStableAnchorOffset = 0,
                        ),
                        importedFonts = emptyList(),
                        themes = ReaderThemeManager.BUILT_IN_THEMES,
                        activeThemeId = "built-in-paper",
                        themeSchedule = ReaderThemeSchedule(),
                        manualThemeOverride = null,
                        bookTitle = null,
                        bookOverrides = ReaderSettingsOverrides(),
                        errorMessage = null,
                        onScopeChanged = {},
                        onPreview = {},
                        onCommit = { committed = true },
                        onCancel = {},
                        onClearCurrentBookOverrides = {},
                        onApplyTheme = {},
                        onCreateTheme = {},
                        onCopyTheme = { _, _ -> },
                        onRenameTheme = { _, _ -> },
                        onUpdateTheme = {},
                        onDeleteTheme = {},
                        onUpdateThemeSchedule = {},
                        onImportFont = {},
                        onRemoveImportedFont = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("reader-quick-settings").assertIsDisplayed()
        compose.onNodeWithTag("reader-quick-settings-more").performClick()
        compose.onAllNodesWithTag("reader-quick-settings").assertCountEquals(0)
        compose.onNodeWithTag("reader-settings-full").assertIsDisplayed()
        val commonTop = compose.onNodeWithTag("reader-typography-common").fetchSemanticsNode().boundsInRoot.top
        val themeTop = compose.onNodeWithTag("reader-theme-built-in-paper").fetchSemanticsNode().boundsInRoot.top
        assertTrue("common typography must precede the first theme card", commonTop < themeTop)
        compose.onNodeWithText("保存").performClick()
        assertTrue(committed)
    }

    @Test
    fun fullModeCancelUsesAnIndependentJourneyAndDoesNotCommit() {
        var committed = false
        var cancelled = false
        compose.setContent {
            MaterialTheme {
                ReaderSettingsSheet(
                    edit = ReaderAppearanceEdit(
                        scope = ReaderSettingsScope.GLOBAL,
                        original = ReaderSettings(),
                        preview = ReaderSettings(fontSizeSp = 22f),
                        originalStableAnchorOffset = 0,
                    ),
                    importedFonts = emptyList(),
                    themes = ReaderThemeManager.BUILT_IN_THEMES,
                    activeThemeId = "built-in-paper",
                    themeSchedule = ReaderThemeSchedule(),
                    manualThemeOverride = null,
                    bookTitle = null,
                    bookOverrides = ReaderSettingsOverrides(),
                    errorMessage = null,
                    onScopeChanged = {},
                    onPreview = {},
                    onCommit = { committed = true },
                    onCancel = { cancelled = true },
                    onClearCurrentBookOverrides = {},
                    onApplyTheme = {},
                    onCreateTheme = {},
                    onCopyTheme = { _, _ -> },
                    onRenameTheme = { _, _ -> },
                    onUpdateTheme = {},
                    onDeleteTheme = {},
                    onUpdateThemeSchedule = {},
                    onImportFont = {},
                    onRemoveImportedFont = {},
                )
            }
        }

        compose.onNodeWithTag("reader-quick-settings").assertIsDisplayed()
        compose.onNodeWithTag("reader-quick-settings-more").performClick()
        compose.onNodeWithTag("reader-settings-full").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertTrue(cancelled)
        assertTrue(!committed)
    }

    @Test
    fun fullModeForwardsCommonAdvancedThemeFontAndScheduleCallbacksThroughUi() {
        val customTheme = ReaderThemePreset(
            id = "custom-test",
            name = "测试主题",
            settings = ReaderSettings(),
            builtIn = false,
            updatedAtEpochMillis = 0L,
        )
        val importedFont = ImportedFont(
            id = "font-test",
            displayName = "测试字体",
            contentSha256 = "test-sha",
            sizeBytes = 1L,
            createdAtEpochMillis = 0L,
        )
        var edit by mutableStateOf(
            ReaderAppearanceEdit(
                scope = ReaderSettingsScope.GLOBAL,
                original = ReaderSettings(),
                preview = ReaderSettings(),
                originalStableAnchorOffset = 0,
            ),
        )
        val callbackOrder = mutableListOf<String>()
        val themeCallbacks = mutableListOf<String>()
        val fontCallbacks = mutableListOf<String>()
        val schedules = mutableListOf<ReaderThemeSchedule>()
        var schedule by mutableStateOf(ReaderThemeSchedule())
        val themes = ReaderThemeManager.BUILT_IN_THEMES + customTheme

        compose.setContent {
            MaterialTheme {
                ReaderSettingsSheet(
                    edit = edit,
                    importedFonts = listOf(importedFont),
                    themes = themes,
                    activeThemeId = "built-in-paper",
                    themeSchedule = schedule,
                    manualThemeOverride = null,
                    bookTitle = "测试书",
                    bookOverrides = ReaderSettingsOverrides(),
                    errorMessage = null,
                    onScopeChanged = { scope ->
                        edit = edit.copy(scope = scope)
                        callbackOrder += "scope:$scope"
                    },
                    onPreview = { settings ->
                        edit = edit.copy(preview = settings)
                        callbackOrder += "preview"
                    },
                    onCommit = {},
                    onCancel = {},
                    onClearCurrentBookOverrides = {},
                    onApplyTheme = { id ->
                        themeCallbacks += "apply:$id"
                        callbackOrder += "theme:apply"
                    },
                    onCreateTheme = { name ->
                        themeCallbacks += "create:$name"
                        callbackOrder += "theme:create"
                    },
                    onCopyTheme = { id, name ->
                        themeCallbacks += "copy:$id:$name"
                        callbackOrder += "theme:copy"
                    },
                    onRenameTheme = { id, name ->
                        themeCallbacks += "rename:$id:$name"
                        callbackOrder += "theme:rename"
                    },
                    onUpdateTheme = { id ->
                        themeCallbacks += "update:$id"
                        callbackOrder += "theme:update"
                    },
                    onDeleteTheme = { id ->
                        themeCallbacks += "delete:$id"
                        callbackOrder += "theme:delete"
                    },
                    onUpdateThemeSchedule = { nextSchedule ->
                        schedule = nextSchedule
                        schedules += nextSchedule
                        callbackOrder += "schedule:${nextSchedule.mode}"
                    },
                    onImportFont = { fontCallbacks += "import" },
                    onRemoveImportedFont = { id -> fontCallbacks += "remove:$id" },
                )
            }
        }

        compose.onNodeWithTag("reader-quick-settings-more").performClick()
        compose.onNodeWithTag("reader-settings-common").assertIsDisplayed()
        compose.onNodeWithText("宋体", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("导入字体").performScrollTo().performClick()
        compose.onNodeWithText("删除字体").performScrollTo().performClick()
        compose.onNodeWithTag("reader-settings-advanced").performScrollTo().performClick()
        compose.onNodeWithTag("reader-focus-band-toggle").performScrollTo().performClick()

        compose.onNodeWithTag("reader-theme-apply-built-in-sepia").performScrollTo().performClick()
        compose.onNodeWithTag("reader-theme-copy-built-in-paper").performScrollTo().performClick()
        compose.onNodeWithText("主题名称").performTextClearance()
        compose.onNodeWithText("主题名称").performTextInput("复制主题")
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithTag("reader-theme-create").performScrollTo().performClick()
        compose.onNodeWithText("主题名称").performTextInput("新主题")
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithTag("reader-theme-copy-custom-test").performScrollTo().performClick()
        compose.onNodeWithText("主题名称").performTextClearance()
        compose.onNodeWithText("主题名称").performTextInput("自定义副本")
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithTag("reader-theme-rename-custom-test").performScrollTo().performClick()
        compose.onNodeWithText("主题名称").performTextClearance()
        compose.onNodeWithText("主题名称").performTextInput("重命名主题")
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithTag("reader-theme-update-custom-test").performScrollTo().performClick()
        compose.onNodeWithTag("reader-theme-delete-custom-test").performScrollTo().performClick()
        compose.onNodeWithTag("reader-theme-dialog-delete-confirm").performClick()

        compose.onNodeWithTag("reader-theme-schedule-mode-follow_system").performScrollTo().performClick()
        compose.onNodeWithTag("reader-theme-schedule-mode-fixed_time").performScrollTo().performClick()
        compose.onNodeWithTag("reader-theme-schedule-light-built-in-sepia").performScrollTo().performClick()
        compose.onNodeWithTag("reader-theme-schedule-manual-override").performScrollTo().performClick()

        assertTrue(callbackOrder.indexOf("preview") >= 0)
        assertTrue(callbackOrder.indexOf("schedule:FOLLOW_SYSTEM") < callbackOrder.indexOf("schedule:FIXED_TIME"))
        assertTrue(themeCallbacks.contains("apply:built-in-sepia"))
        assertTrue(themeCallbacks.contains("create:新主题"))
        assertTrue(themeCallbacks.contains("copy:built-in-paper:复制主题"))
        assertTrue(themeCallbacks.contains("copy:custom-test:自定义副本"))
        assertTrue(themeCallbacks.contains("rename:custom-test:重命名主题"))
        assertTrue(themeCallbacks.contains("update:custom-test"))
        assertTrue(themeCallbacks.contains("delete:custom-test"))
        assertTrue(fontCallbacks == listOf("import", "remove:font-test"))
        assertTrue(schedules.any { it.mode.name == "FOLLOW_SYSTEM" })
        assertTrue(schedules.any { it.mode.name == "FIXED_TIME" && it.lightThemeId == "built-in-sepia" })
        assertTrue(edit.preview.font == ReaderFontRef.Serif)
        assertTrue(edit.preview.focusBand.enabled)
    }

    @Test
    fun hostScrollHandlesTheFullSheetAtTwoHundredPercentWithoutNestedScroll() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme {
                    ReaderSettingsSheet(
                        edit = ReaderAppearanceEdit(
                            scope = ReaderSettingsScope.GLOBAL,
                            original = ReaderSettings(),
                            preview = ReaderSettings(fontSizeSp = 22f),
                            originalStableAnchorOffset = 0,
                        ),
                        importedFonts = emptyList(),
                        themes = ReaderThemeManager.BUILT_IN_THEMES,
                        activeThemeId = "built-in-paper",
                        themeSchedule = ReaderThemeSchedule(),
                        manualThemeOverride = null,
                        bookTitle = null,
                        bookOverrides = ReaderSettingsOverrides(),
                        errorMessage = null,
                        onScopeChanged = {},
                        onPreview = {},
                        onCommit = {},
                        onCancel = {},
                        onClearCurrentBookOverrides = {},
                        onApplyTheme = {},
                        onCreateTheme = {},
                        onCopyTheme = { _, _ -> },
                        onRenameTheme = { _, _ -> },
                        onUpdateTheme = {},
                        onDeleteTheme = {},
                        onUpdateThemeSchedule = {},
                        onImportFont = {},
                        onRemoveImportedFont = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("reader-quick-settings").assertIsDisplayed()
        compose.onNodeWithTag("reader-quick-settings-more").performClick()
        compose.onNodeWithTag("reader-settings-full").assertIsDisplayed()
        compose.onNodeWithTag("reader-settings-advanced").performScrollTo().performClick()
        compose.onNodeWithTag("reader-settings-full-advanced-controls").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("保存").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
    }
}
