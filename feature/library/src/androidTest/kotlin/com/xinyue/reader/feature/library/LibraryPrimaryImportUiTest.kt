package com.xinyue.reader.feature.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryPrimaryImportUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyShelfShowsOnlyInlinePrimaryImport() {
        render(LibraryUiState(hasAnyBooks = false))

        compose.onNodeWithTag("library_empty_import").assertIsDisplayed()
        compose.onAllNodesWithTag("library_import").assertCountEquals(0)
    }

    @Test
    fun existingShelfUsesTopBarImportAndNoDuplicateInlineImport() {
        render(LibraryUiState(hasAnyBooks = true))

        compose.onNodeWithTag("library_import").assertIsDisplayed()
        compose.onAllNodesWithTag("library_empty_import").assertCountEquals(0)
        compose.onNodeWithContentDescription("导入 TXT").assertIsDisplayed()
    }

    private fun render(state: LibraryUiState) {
        compose.setContent {
            MaterialTheme {
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
                    onPickCover = {},
                    onClearCover = {},
                    onEnterSelection = {},
                    onToggleSelection = {},
                    onSelectAllVisible = {},
                    onClearSelection = {},
                    onMoveSelected = {},
                    onMarkSelectedFinished = {},
                    onDeleteSelected = {},
                    onUndoDelete = {},
                    onOpenStatistics = {},
                    showTopActions = false,
                    isSearchActive = false,
                    onSearchActiveChange = {},
                )
            }
        }
    }
}
