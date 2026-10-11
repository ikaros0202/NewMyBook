package com.xinyue.reader.feature.importing

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xinyue.reader.core.data.DuplicateResolution
import com.xinyue.reader.core.data.ImportBatchState
import com.xinyue.reader.core.data.ImportItemState
import com.xinyue.reader.core.data.ImportItemStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun succeededItemExposesStartReadingAsPrimaryAction() {
        var openedBookId: String? = null
        render(
            item = sampleItem(
                status = ImportItemStatus.SUCCEEDED,
                bookId = "book-1",
            ),
            onOpenBook = { openedBookId = it },
        )

        compose.onNodeWithTag("import_open_book_work-0").assertIsDisplayed().performClick()
        compose.onNodeWithText("编辑书名和作者").assertIsDisplayed()
        assertEquals("book-1", openedBookId)
    }

    @Test
    fun failedItemDoesNotExposeStartReadingAction() {
        render(item = sampleItem(status = ImportItemStatus.FAILED))

        compose.onAllNodesWithText("开始阅读").assertCountEquals(0)
        compose.onNodeWithText("重试此项").assertIsDisplayed()
    }

    private fun render(
        item: ImportItemState,
        onOpenBook: (String) -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                ImportScreen(
                    uiState = ImportUiState(
                        batch = ImportBatchState(
                            batchId = "batch-1",
                            items = listOf(item),
                        ),
                    ),
                    onBack = {},
                    onOpenBook = onOpenBook,
                    onSelectFiles = {},
                    onSelectKnownEncoding = {},
                    onResolveDuplicate = { _, _ -> },
                    onRetry = {},
                    onEditImportedBook = {},
                    onDismissError = {},
                )
            }
        }
    }

    private fun sampleItem(
        status: ImportItemStatus,
        bookId: String? = null,
    ) = ImportItemState(
        workId = "work-0",
        batchId = "batch-1",
        index = 0,
        total = 1,
        attempt = 0,
        uriString = "content://books/1",
        displayName = "测试小说.txt",
        preferredCharsetName = "UTF-8",
        duplicateResolution = DuplicateResolution.ASK,
        status = status,
        progressPercent = if (status == ImportItemStatus.FAILED) 100 else 0,
        bookId = bookId,
        existingBookId = null,
        existingBookTitle = null,
        errorMessage = if (status == ImportItemStatus.FAILED) "导入失败，请检查文件后重试" else null,
    )
}
