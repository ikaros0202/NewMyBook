package com.xinyue.reader.feature.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderV12AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectionActionsRemainReachableAndAnnounceTheirPaneAtTwoHundredPercentFont() {
        compose.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, 2f)) {
                MaterialTheme {
                    ReaderSelectionActionBar(onAction = {})
                }
            }
        }

        compose.onNodeWithTag("reader_selection_actions")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "文本选择操作"))
        listOf("书签", "批注").forEach { label ->
            val action = compose.onNode(hasText(label) and hasClickAction())
            action.performScrollTo().assertIsDisplayed()
            val bounds = action.fetchSemanticsNode().boundsInRoot
            val minimum = 48f * compose.density.density
            assertTrue("$label width ${bounds.width}px is below 48dp ($minimum px)", bounds.width >= minimum)
            assertTrue("$label height ${bounds.height}px is below 48dp ($minimum px)", bounds.height >= minimum)
        }
    }
}
