package com.xinyue.reader.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.BookGroup
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySearchAndMenusUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun longTitleAndBookActionsUseSeparateRegions() {
        var expanded by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                LibraryBookCard(
                    book = sampleBook(title = LONG_TITLE),
                    progressFraction = 0.42,
                    selectionMode = false,
                    selected = false,
                    menuExpanded = expanded,
                    onOpen = {},
                    onEnterSelection = {},
                    onToggleSelection = {},
                    onMenuExpandedChange = { expanded = it },
                    onEdit = {},
                    onStatistics = {},
                    onDelete = {},
                )
            }
        }

        val title = compose.onNodeWithTag("library_book_title_book-1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag("library_book_menu_button_book-1").fetchSemanticsNode().boundsInRoot
        assertTrue("title $title overlaps menu $menu", title.right <= menu.left)
        compose.onNodeWithText("编辑").assertDoesNotExist()
        compose.onNodeWithTag("library_book_menu_button_book-1").performClick()
        compose.onNodeWithTag("library_book_menu_book-1").assertIsDisplayed()
        val triggerInScreen = compose.onNodeWithTag("library_book_menu_button_book-1")
            .fetchSemanticsNode().screenBounds()
        val firstMenuItemInScreen = compose.onNodeWithText("编辑")
            .fetchSemanticsNode().screenBounds()
        assertTrue(
            "book menu item $firstMenuItemInScreen is not below trigger $triggerInScreen",
            firstMenuItemInScreen.top >= triggerInScreen.bottom,
        )
        compose.onNodeWithText("编辑").assertIsDisplayed()
        compose.onNodeWithText("统计").assertIsDisplayed()
        compose.onNodeWithText("删除").assertIsDisplayed()
    }

    @Test
    fun sortMenuIsBelowItsOwnTriggerAndAutomaticHeadingIsGone() {
        var expanded by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                Column {
                    LibraryGroupBar(
                        groups = emptyList(),
                        selectedFilter = LibraryFilter.All,
                        onFilterSelected = {},
                        onCreateGroup = {},
                        onRenameGroup = {},
                        onDeleteGroup = {},
                    )
                    LibrarySortSelector(
                        selected = LibrarySort.RECENT,
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        onSelected = {},
                    )
                }
            }
        }

        compose.onNodeWithText("自动视图").assertDoesNotExist()
        compose.onNodeWithTag("library_sort_button").performClick()
        val trigger = compose.onNodeWithTag("library_sort_button")
            .fetchSemanticsNode().screenBounds()
        val menuItem = compose.onNodeWithText("导入时间")
            .fetchSemanticsNode().screenBounds()
        assertTrue("menu item $menuItem is not below trigger $trigger", menuItem.top >= trigger.bottom)
        val root = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            .map(SemanticsNode::screenBounds)
            .maxBy { it.width }
        assertTrue("menu item $menuItem exceeds root $root", menuItem.right <= root.right)
    }

    @Test
    fun searchScreenDistinguishesBlankLiveResultsAndNoResults() {
        val books = listOf(
            sampleBook(id = "a", title = "山城夜话", author = "林间客"),
            sampleBook(id = "b", title = "山海旧闻", author = "纸上旅人"),
            sampleBook(id = "c", title = "山海短篇集", author = "远舟"),
        )
        var query by mutableStateOf("")
        compose.setContent {
            val matches = if (query.isBlank()) emptyList() else books.filter { it.matchesLibraryQuery(query) }
            MaterialTheme {
                LibrarySearchScreen(
                    uiState = LibraryUiState(
                        books = matches,
                        query = query,
                        hasAnyBooks = true,
                    ),
                    fieldModifier = androidx.compose.ui.Modifier,
                    listState = rememberLazyListState(),
                    onQueryChanged = { query = it },
                    onBack = {},
                    bookItem = { book -> Text(book.title) },
                )
            }
        }

        compose.onNodeWithText("输入书名、作者或系列开始搜索").assertIsDisplayed()
        compose.onNodeWithText("找到 0 本").assertDoesNotExist()
        compose.onNodeWithTag("library_search_field").performTextInput("山")
        compose.onNodeWithText("找到 3 本").assertIsDisplayed()
        compose.onNodeWithTag("library_search_field").performTextReplacement("不存在")
        compose.onNodeWithText("没有找到相关书籍，换个书名、作者或系列试试").assertIsDisplayed()
    }

    @Test
    fun editDialogSubmitsSeriesOrderAndMultipleCollections() {
        var submittedSeries: String? = null
        var submittedOrder: Int? = null
        var submittedCollections: Set<String> = emptySet()
        val first = sampleGroup("collection-1", "科幻")
        val second = sampleGroup("collection-2", "收藏")
        compose.setContent {
            MaterialTheme {
                RenameBookDialog(
                    book = sampleBook(title = "长夜列车").copy(
                        seriesName = "星海纪事",
                        seriesOrder = 2,
                    ),
                    groups = listOf(first, second),
                    selectedCollectionIds = setOf(first.id),
                    onConfirm = { _, _, series, order, collections ->
                        submittedSeries = series
                        submittedOrder = order
                        submittedCollections = collections
                    },
                    onDismiss = {},
                    isCoverUpdating = false,
                    onPickCover = {},
                    onClearCover = {},
                )
            }
        }

        compose.onNodeWithTag("library-series-name").performTextReplacement("星海新篇")
        compose.onNodeWithTag("library-series-order").performTextReplacement("3")
        compose.onNodeWithTag("library-collection-${second.id}").performClick()
        compose.onNodeWithText("保存").performClick()

        assertTrue(submittedSeries == "星海新篇")
        assertTrue(submittedOrder == 3)
        assertTrue(submittedCollections == setOf(first.id, second.id))
    }

    private fun sampleBook(
        id: String = "book-1",
        title: String,
        author: String = "纸上旅人",
    ) = Book(
        id = id,
        title = title,
        author = author,
        originalFileName = "public-long-title.txt",
        originalPath = "books/book-1/original.txt",
        normalizedPath = "books/book-1/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "public-long-title",
        contentLength = 100,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = null,
    )

    private fun sampleGroup(id: String, name: String) = BookGroup(
        id = id,
        name = name,
        sortOrder = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )

    private companion object {
        const val LONG_TITLE = "这是一本拥有二十个中文字符长度的测试书名"
    }
}

private fun SemanticsNode.screenBounds(): Rect {
    val topLeft = positionOnScreen
    return Rect(
        left = topLeft.x,
        top = topLeft.y,
        right = topLeft.x + size.width,
        bottom = topLeft.y + size.height,
    )
}
