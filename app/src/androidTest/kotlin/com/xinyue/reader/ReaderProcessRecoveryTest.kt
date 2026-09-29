package com.xinyue.reader

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.xinyue.reader.core.data.RestoreDatabaseGateway
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Executed in two separate instrumentation processes around an external am force-stop. */
@RunWith(AndroidJUnit4::class)
class ReaderProcessRecoveryTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    private val context: Context get() = instrumentation.targetContext.applicationContext
    private val gateway: RestoreDatabaseGateway
        get() = EntryPointAccessors.fromApplication(context, BackupDebugEntryPoint::class.java)
            .restoreDatabaseGateway()

    @Test
    fun prepareStableProgressForProcessDeath() {
        compose.onNodeWithTag("nav_library", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("library_root").assertIsDisplayed()
        compose.onNodeWithText(FIXTURE_TITLE).performClick()
        awaitReader()
        openReaderMenu()
        compose.onNodeWithTag("reader-menu-more").performClick()
        compose.onNodeWithText("定位进度").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("reader-progress-preview-snippet").fetchSemanticsNodes().isNotEmpty()
        }
        val progressSlider = compose.onNodeWithTag("reader-progress-slider")
        val currentFraction = progressSlider.fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
            .current
        val targetFraction = if (currentFraction < 0.5f) 0.75f else 0.25f
        progressSlider.performSemanticsAction(SemanticsActions.SetProgress) { set -> set(targetFraction) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("返回原位置").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("从这里继续").performClick()

        device.pressHome()
        val snapshot = awaitPersistedProgress()
        val book = snapshot.books.single { it.title == FIXTURE_TITLE }
        val progress = snapshot.progress.single { it.bookId == book.id }
        assertTrue("stable progress must move beyond the beginning", progress.offset > 0L)
        assertTrue("stable progress must stay inside the fixture", progress.offset < progress.contentLength)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BOOK_ID, book.id)
            .putLong(KEY_OFFSET, progress.offset)
            .putLong(KEY_CONTENT_LENGTH, progress.contentLength)
            .commit()
            .also { assertTrue("recovery expectation was not committed", it) }
    }

    @Test
    fun verifyStableProgressAfterProcessDeath() {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val expectedBookId = requireNotNull(preferences.getString(KEY_BOOK_ID, null)) {
            "process-recovery prepare stage did not store a book id"
        }
        val expectedOffset = preferences.getLong(KEY_OFFSET, -1L)
        val expectedLength = preferences.getLong(KEY_CONTENT_LENGTH, -1L)
        assertTrue("process-recovery prepare stage did not store an offset", expectedOffset > 0L)
        assertTrue("process-recovery prepare stage did not store content length", expectedLength > expectedOffset)

        val restored = runBlocking { gateway.snapshot() }
        val restoredProgress = restored.progress.single { it.bookId == expectedBookId }
        assertEquals("stable Room anchor changed across process death", expectedOffset, restoredProgress.offset)
        assertEquals("content length changed across process death", expectedLength, restoredProgress.contentLength)

        compose.onNodeWithTag("nav_library", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("library_root").assertIsDisplayed()
        compose.onNodeWithText(FIXTURE_TITLE).performClick()
        awaitReader()
        openReaderMenu()
        val expectedPercent = (expectedOffset.toDouble() / expectedLength * 100).toInt().coerceIn(0, 100)
        compose.onNodeWithText("全书 $expectedPercent%").assertIsDisplayed()
        assertEquals(1, compose.onAllNodesWithTag("reader_root").fetchSemanticsNodes().size)
    }

    private fun awaitPersistedProgress() = runBlocking {
        repeat(100) {
            val snapshot = gateway.snapshot()
            val fixtureId = snapshot.books.singleOrNull { book -> book.title == FIXTURE_TITLE }?.id
            val progress = snapshot.progress.singleOrNull { item -> item.bookId == fixtureId }
            if (progress != null && progress.offset > 0L) return@runBlocking snapshot
            Thread.sleep(100)
        }
        error("stable progress was not persisted after backgrounding")
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

    private companion object {
        const val FIXTURE_TITLE = "sample-novel"
        const val PREFERENCES = "e2_process_recovery"
        const val KEY_BOOK_ID = "book_id"
        const val KEY_OFFSET = "offset"
        const val KEY_CONTENT_LENGTH = "content_length"
    }
}
