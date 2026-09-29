package com.xinyue.reader

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderWindowMatrixTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    @Test
    fun readerStaysUsableInsideCurrentWindowAndActivityRecreation() {
        compose.onNodeWithTag("nav_library", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("library_root").assertIsDisplayed()
        compose.onNodeWithText(FIXTURE_TITLE).performClick()
        awaitReader()

        assertInsideDisplay(compose.onNodeWithTag("reader_root").fetchSemanticsNode().boundsInRoot, "reader root")
        assertInsideDisplay(currentPageBounds(), "reader page")
        openReaderMenu()
        val progressText = compose.onAllNodesWithText("全书 ", substring = true)
            .fetchSemanticsNodes()
            .first { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text) }
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            .joinToString(separator = "") { it.text }
        MENU_TAGS.forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertMinimumTouchTarget(bounds, tag)
            assertInsideDisplay(bounds, tag)
        }

        compose.activityRule.scenario.recreate()
        awaitReader()
        assertEquals(1, compose.onAllNodesWithTag("reader_root").fetchSemanticsNodes().size)
        openReaderMenu()
        compose.onNodeWithText(progressText).assertIsDisplayed()
    }

    private fun awaitReader() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("reader_root").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("reader_root").assertIsDisplayed()
    }

    private fun openReaderMenu() {
        if (compose.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isNotEmpty()) return
        val bounds = currentPageBounds()
        check(device.click(bounds.center.x.toInt(), bounds.center.y.toInt())) { "reader menu tap failed" }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("reader-menu-navigation").fetchSemanticsNodes().isNotEmpty()
        }
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

    private fun assertMinimumTouchTarget(bounds: Rect, label: String) {
        val density = compose.activity.resources.displayMetrics.density
        val minimumPx = MINIMUM_TOUCH_DP * density
        assertTrue("$label width ${bounds.width} is below 48dp ($minimumPx px)", bounds.width >= minimumPx)
        assertTrue("$label height ${bounds.height} is below 48dp ($minimumPx px)", bounds.height >= minimumPx)
    }

    private fun assertInsideDisplay(bounds: Rect, label: String) {
        assertTrue("$label has no usable width", bounds.width > 0f)
        assertTrue("$label has no usable height", bounds.height > 0f)
        assertTrue("$label crosses the left display edge", bounds.left >= 0f)
        assertTrue("$label crosses the top display edge", bounds.top >= 0f)
        assertTrue("$label crosses the right display edge", bounds.right <= device.displayWidth.toFloat())
        assertTrue("$label crosses the bottom display edge", bounds.bottom <= device.displayHeight.toFloat())
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
        const val FIXTURE_TITLE = "sample-novel"
        const val MINIMUM_TOUCH_DP = 48f
    }
}
