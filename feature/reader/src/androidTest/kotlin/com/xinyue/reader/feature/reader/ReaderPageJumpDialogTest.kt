package com.xinyue.reader.feature.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xinyue.reader.core.text.DetectedChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderPageJumpDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun invalidPageCannotJumpAndValidPageOnlyCommitsOnConfirmation() {
        var jumpedPage: Int? = null
        var openedDirectory = false
        compose.setContent {
            MaterialTheme {
                ReaderPageJumpDialog(
                    pages = listOf(ReaderPage(0, 100), ReaderPage(100, 200), ReaderPage(200, 300)),
                    anchorOffset = 0,
                    chapters = listOf(DetectedChapter("第一章", 0), DetectedChapter("第二章", 202)),
                    isLoading = false,
                    progress = 1f,
                    error = null,
                    onRetry = {},
                    onJump = { jumpedPage = it },
                    onOpenDirectory = { openedDirectory = true },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithTag("reader-jump-page-input").performTextReplacement("4")
        compose.onNodeWithTag("reader-jump-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("reader-jump-page-input").performTextReplacement("3")
        compose.onNodeWithText("第二章").assertExists()
        assertEquals(null, jumpedPage)
        compose.onNodeWithTag("reader-jump-confirm").assertIsEnabled().performClick()
        assertEquals(3, jumpedPage)
        compose.onNodeWithTag("reader-jump-directory").performClick()
        assertTrue(openedDirectory)
    }
}
