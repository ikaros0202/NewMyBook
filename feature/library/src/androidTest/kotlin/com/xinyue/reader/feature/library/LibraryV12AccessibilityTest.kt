package com.xinyue.reader.feature.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xinyue.reader.core.domain.model.Book
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryV12AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectionActionsRemainReachableAndAnnounceStateAtTwoHundredPercentFont() {
        compose.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, 2f)) {
                MaterialTheme {
                    LibrarySelectionBar(
                        selectedCount = 2,
                        groups = emptyList(),
                        actionInProgress = false,
                        onSelectAll = {},
                        onMove = {},
                        onMarkFinished = {},
                        onDelete = {},
                        onCancel = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("library-selection-bar")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已选择 2 本书"))
        listOf("全选当前结果", "取消选择").forEach { label ->
            val action = compose.onNode(hasText(label) and hasClickAction())
            action.performScrollTo().assertIsDisplayed()
            val bounds = action.fetchSemanticsNode().boundsInRoot
            val minimum = 48f * compose.density.density
            assertTrue("$label width ${bounds.width}px is below 48dp ($minimum px)", bounds.width >= minimum)
            assertTrue("$label height ${bounds.height}px is below 48dp ($minimum px)", bounds.height >= minimum)
        }
    }

    @Test
    fun libraryViewSelectorExposesFourReachableModesAtTwoHundredPercentFont() {
        var selected by mutableStateOf(LibraryView.BOOKS)
        compose.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, 2f)) {
                MaterialTheme {
                    LibraryViewSelector(selected = selected, onSelected = { selected = it })
                }
            }
        }

        compose.onNodeWithTag("library-view-button").performClick()
        listOf(
            "书籍" to "books",
            "作者" to "authors",
            "系列" to "series",
            "集合" to "collections",
        ).forEach { (label, tag) ->
            val action = compose.onNodeWithTag("library-view-$tag")
            action.assertIsDisplayed()
            val bounds = action.fetchSemanticsNode().boundsInRoot
            val minimum = 48f * compose.density.density
            assertTrue("$label height ${bounds.height}px is below 48dp ($minimum px)", bounds.height >= minimum)
        }
        compose.onNodeWithTag("library-view-series").performClick()
        assertTrue(selected == LibraryView.SERIES)
    }

    @Test
    fun searchFieldNamesTitleAndAuthorAndCanClearTheQuery() {
        var query by mutableStateOf("")
        compose.setContent {
            MaterialTheme {
                LibrarySearchScreen(
                    uiState = LibraryUiState(query = query, hasAnyBooks = true),
                    fieldModifier = Modifier,
                    listState = rememberLazyListState(),
                    onQueryChanged = { query = it },
                    onBack = {},
                    bookItem = {},
                )
            }
        }

        compose.onNodeWithText("搜索书名、作者或系列").assertIsDisplayed()
        compose.onNodeWithTag("library_search_field").performTextInput("林川")
        compose.onNodeWithContentDescription("清除搜索").performClick()
        assertTrue(query.isEmpty())
    }

    @Test
    fun longTitleAndMenuRemainSeparateAtTwoHundredPercentFont() {
        val longTitle = "这是一本拥有二十个中文字符长度的测试书名"
        compose.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, 2f)) {
                MaterialTheme {
                    LibraryBookCard(
                        book = sampleBook(longTitle),
                        progressFraction = 0.42,
                        selectionMode = false,
                        selected = false,
                        menuExpanded = false,
                        onOpen = {},
                        onEnterSelection = {},
                        onToggleSelection = {},
                        onMenuExpandedChange = {},
                        onEdit = {},
                        onStatistics = {},
                        onDelete = {},
                    )
                }
            }
        }

        val title = compose.onNodeWithTag("library_book_title_book-1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag("library_book_menu_button_book-1").fetchSemanticsNode().boundsInRoot
        assertTrue("title $title overlaps menu $menu", title.right <= menu.left)
        compose.onNodeWithContentDescription(longTitle).assertIsDisplayed()
        val minimum = 48f * compose.density.density
        assertTrue("menu width ${menu.width}px is below 48dp ($minimum px)", menu.width >= minimum)
        assertTrue("menu height ${menu.height}px is below 48dp ($minimum px)", menu.height >= minimum)
    }

    private fun sampleBook(title: String) = Book(
        id = "book-1",
        title = title,
        author = "纸上旅人",
        originalFileName = "public-long-title.txt",
        originalPath = "books/book-1/original.txt",
        normalizedPath = "books/book-1/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "public-long-title",
        contentLength = 100,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = null,
    )
}
