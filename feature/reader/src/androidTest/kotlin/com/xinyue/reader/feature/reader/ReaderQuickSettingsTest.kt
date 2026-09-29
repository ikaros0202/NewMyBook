package com.xinyue.reader.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xinyue.reader.core.domain.model.ReaderColorTheme
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderPageAnimation
import com.xinyue.reader.core.domain.model.ReaderSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderQuickSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun quickSettingsKeepRequiredOrderAndAccessibleTargetsAtTwoHundredPercentFontScale() {
        compose.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, 2f)) {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        ReaderQuickSettings(
                            modifier = Modifier,
                            settings = ReaderSettings(brightness = 0.5f),
                            scope = ReaderSettingsScope.GLOBAL,
                            onScopeChanged = {},
                            onSettingsChange = {},
                            onMoreSettings = {},
                        )
                    }
                }
            }
        }

        val requiredOrder = listOf(
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
        val semantics = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().flattenTestTags()
        requiredOrder.zipWithNext().forEach { (before, after) ->
            assertTrue(
                "$before must precede $after",
                semantics.indexOf(before) >= 0 && semantics.indexOf(before) < semantics.indexOf(after),
            )
        }

        val interactiveTags = listOf(
            "reader-quick-settings-scope-global",
            "reader-quick-settings-scope-current-book",
            "reader-quick-settings-font-size-slider",
            "reader-quick-settings-line-height-slider",
            "reader-quick-settings-paragraph-spacing-slider",
            "reader-quick-settings-indent-0",
            "reader-quick-settings-indent-1",
            "reader-quick-settings-indent-2",
            "reader-quick-settings-page-slide",
            "reader-quick-settings-page-cover",
            "reader-quick-settings-page-none",
            "reader-quick-settings-font-system",
            "reader-quick-settings-font-serif",
            "reader-quick-settings-font-sans-serif",
            "reader-quick-settings-theme-paper",
            "reader-quick-settings-theme-sepia",
            "reader-quick-settings-theme-green",
            "reader-quick-settings-theme-dark",
            "reader-quick-settings-theme-oled_black",
            "reader-quick-settings-brightness-slider",
            "reader-quick-settings-brightness-toggle",
            "reader-quick-settings-more",
        )
        interactiveTags.forEach { tag ->
            val node = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val minimum = 48f * compose.density.density
            assertTrue("$tag width is below 48dp", bounds.width >= minimum)
            assertTrue("$tag height is below 48dp", bounds.height >= minimum)
        }

        compose.onNodeWithTag("reader-quick-settings-indent-0").assertIsSelected()
    }

    @Test
    fun indentPresetsEmitZeroOneAndTwoEmCallbacks() {
        var settings by mutableStateOf(ReaderSettings())
        val values = mutableListOf<Float>()
        compose.setContent {
            MaterialTheme {
                ReaderQuickSettings(
                    settings = settings,
                    scope = ReaderSettingsScope.GLOBAL,
                    onScopeChanged = {},
                    onSettingsChange = {
                        settings = it
                        values += it.firstLineIndentEm
                    },
                    onMoreSettings = {},
                )
            }
        }

        listOf("0", "1", "2").forEach { indent ->
            compose.onNodeWithTag("reader-quick-settings-indent-$indent").performClick()
        }

        assertTrue(values == listOf(0f, 1f, 2f))
    }

    @Test
    fun themeSelectionUsesEffectivePaletteAndEmitsExactSepiaArgb() {
        var settings by mutableStateOf(ReaderSettings())
        compose.setContent {
            MaterialTheme {
                ReaderQuickSettings(
                    settings = settings,
                    scope = ReaderSettingsScope.GLOBAL,
                    onScopeChanged = {},
                    onSettingsChange = { settings = it },
                    onMoreSettings = {},
                )
            }
        }

        val sepia = ReaderColorTheme.SEPIA.builtInPalette()
        compose.onNodeWithTag("reader-quick-settings-theme-paper").assertIsSelected()
        compose.onNodeWithTag("reader-quick-settings-theme-sepia").performClick()

        assertTrue(settings.colorTheme == ReaderColorTheme.SEPIA)
        assertTrue(settings.foregroundArgb == sepia.foregroundArgb)
        assertTrue(settings.backgroundArgb == sepia.backgroundArgb)
    }

    @Test
    fun invalidImportedFontReferenceSelectsSystemAfterNormalization() {
        compose.setContent {
            MaterialTheme {
                ReaderQuickSettings(
                    settings = ReaderSettings(font = ReaderFontRef.Imported("  ")),
                    scope = ReaderSettingsScope.GLOBAL,
                    onScopeChanged = {},
                    onSettingsChange = {},
                    onMoreSettings = {},
                )
            }
        }

        compose.onNodeWithTag("reader-quick-settings-font-system").assertIsSelected()
        compose.onNodeWithTag("reader-quick-settings-font-serif").assertIsNotSelected()
    }

    @Test
    fun scopePageMethodFontThemeBrightnessAndMoreSettingsEmitObservableCallbacks() {
        var settings by mutableStateOf(ReaderSettings(brightness = 0.5f))
        var selectedScope by mutableStateOf(ReaderSettingsScope.GLOBAL)
        var moreSettingsClicked = false
        compose.setContent {
            MaterialTheme {
                ReaderQuickSettings(
                    settings = settings,
                    scope = selectedScope,
                    onScopeChanged = { selectedScope = it },
                    onSettingsChange = { settings = it },
                    onMoreSettings = { moreSettingsClicked = true },
                )
            }
        }

        compose.onNodeWithTag("reader-quick-settings-scope-current-book").performClick()
        assertTrue(selectedScope == ReaderSettingsScope.CURRENT_BOOK)
        compose.onAllNodesWithTag("reader-quick-settings-page-method").assertCountEquals(0)
        compose.onAllNodesWithTag("reader-quick-settings-brightness-slider").assertCountEquals(0)
        compose.onAllNodesWithTag("reader-quick-settings-brightness-toggle").assertCountEquals(0)
        compose.onNodeWithTag("reader-quick-settings-global-only-note").assertIsDisplayed()
        compose.onNodeWithTag("reader-quick-settings-scope-global").performClick()
        compose.onNodeWithTag("reader-quick-settings-page-method").assertIsDisplayed()
        compose.onNodeWithTag("reader-quick-settings-brightness-toggle").assertIsDisplayed()
        compose.onNodeWithTag("reader-quick-settings-page-cover").performClick()
        compose.onNodeWithTag("reader-quick-settings-font-serif").performClick()
        compose.onNodeWithTag("reader-quick-settings-theme-sepia").performClick()
        compose.onNodeWithTag("reader-quick-settings-brightness-toggle").performClick()
        compose.onNodeWithTag("reader-quick-settings-more").performClick()

        assertTrue(selectedScope == ReaderSettingsScope.GLOBAL)
        assertTrue(settings.pageAnimation == ReaderPageAnimation.COVER)
        assertTrue(settings.font == ReaderFontRef.Serif)
        assertTrue(settings.colorTheme == ReaderColorTheme.SEPIA)
        val sepia = ReaderColorTheme.SEPIA.builtInPalette()
        assertTrue(settings.foregroundArgb == sepia.foregroundArgb)
        assertTrue(settings.backgroundArgb == sepia.backgroundArgb)
        assertTrue(settings.brightness == -1f)
        assertTrue(moreSettingsClicked)
    }

    @Test
    fun currentBookScopeOmitsGlobalPageAndBrightnessControls() {
        compose.setContent {
            MaterialTheme {
                ReaderQuickSettings(
                    settings = ReaderSettings(),
                    scope = ReaderSettingsScope.CURRENT_BOOK,
                    onScopeChanged = {},
                    onSettingsChange = {},
                    onMoreSettings = {},
                )
            }
        }

        compose.onAllNodesWithTag("reader-quick-settings-page-method").assertCountEquals(0)
        compose.onAllNodesWithTag("reader-quick-settings-brightness-slider").assertCountEquals(0)
        compose.onAllNodesWithTag("reader-quick-settings-brightness-toggle").assertCountEquals(0)
        compose.onNodeWithTag("reader-quick-settings-global-only-note").assertIsDisplayed()
    }
}

private fun SemanticsNode.flattenTestTags(): List<String> =
    listOfNotNull(
        if (config.contains(SemanticsProperties.TestTag)) config[SemanticsProperties.TestTag] else null,
    ) + children.flatMap { it.flattenTestTags() }
