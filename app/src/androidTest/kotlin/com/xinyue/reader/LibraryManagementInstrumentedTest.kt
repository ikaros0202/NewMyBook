package com.xinyue.reader

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.BookGroup
import com.xinyue.reader.feature.library.LibraryFilter
import com.xinyue.reader.feature.library.LibraryScreen
import com.xinyue.reader.feature.library.LibraryUiState
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryManagementInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun coverGroupAndBookStatisticsActionsReachTheRealComposeSurface() {
        val actions = mutableListOf<String>()
        render(
            LibraryUiState(
                books = listOf(book()),
                groups = listOf(group()),
                filter = LibraryFilter.Group("group-1"),
                hasAnyBooks = true,
                readingMillisByBook = mapOf("book-1" to 125_000),
            ),
            actions,
        )

        compose.onNodeWithTag("library_book_menu_button_book-1").performClick()
        compose.onNodeWithTag("library_book_statistics_book-1").performClick()
        compose.onNodeWithTag("library_book_menu_button_book-1").performClick()
        compose.onNodeWithTag("library_book_edit_book-1").performClick()
        compose.onNodeWithText("选择封面").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("改名").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("删除分组").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("新建分组").performClick()
        compose.onNodeWithText("取消").performClick()

        assertTrue(actions.containsAll(listOf("statistics:book-1", "cover:book-1")))
    }

    @Test
    fun batchFinishedMoveDeleteAndUndoControlsAreReachable() {
        val actions = mutableListOf<String>()
        render(
            LibraryUiState(
                books = listOf(book()),
                groups = listOf(group()),
                hasAnyBooks = true,
                selectedBookIds = setOf("book-1"),
                isSelectionMode = true,
            ),
            actions,
        )

        compose.onNodeWithTag("library-selection-bar").assertIsDisplayed()
        compose.onNodeWithText("移动到").performClick()
        compose.onAllNodesWithText("测试分组")[1].performClick()
        compose.onNodeWithText("标记已读完").performClick()
        compose.onNodeWithText("取消已读完").performClick()
        compose.onNodeWithText("删除").performClick()

        assertTrue(actions.containsAll(listOf("move:group-1", "finished:true", "finished:false", "delete-selected")))
    }

    private fun render(state: LibraryUiState, actions: MutableList<String>) {
        compose.activity.setContent {
            LibraryScreen(
                uiState = state,
                onImport = {},
                onOpenBook = {},
                onDismissError = {},
                onQueryChanged = {},
                onSortChanged = {},
                onFilterChanged = {},
                onCreateGroup = {},
                onRenameGroup = { _, _ -> },
                onDeleteGroup = {},
                onEditBook = { _, _, _ -> },
                onDeleteBook = {},
                onPickCover = { actions += "cover:$it" },
                onClearCover = { actions += "cover-clear:$it" },
                onEnterSelection = {},
                onToggleSelection = {},
                onSelectAllVisible = { actions += "select-all" },
                onClearSelection = {},
                onMoveSelected = { actions += "move:$it" },
                onMarkSelectedFinished = { actions += "finished:$it" },
                onDeleteSelected = { actions += "delete-selected" },
                onUndoDelete = { actions += "undo" },
                onOpenStatistics = { actions += "statistics:$it" },
                isSearchActive = false,
                onSearchActiveChange = {},
            )
        }
    }

    private fun book() = Book(
        id = "book-1",
        title = "测试书",
        author = null,
        originalFileName = "public-fixture.txt",
        originalPath = "books/book-1/original.txt",
        normalizedPath = "books/book-1/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "public-fixture",
        contentLength = 100,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = null,
    )

    private fun group() = BookGroup(
        id = "group-1",
        name = "测试分组",
        sortOrder = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}
