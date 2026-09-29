package com.xinyue.reader.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.BookGroup
import com.xinyue.reader.core.domain.model.LibraryGridDensity
import com.xinyue.reader.core.domain.model.LibraryLayoutMode
import com.xinyue.reader.core.domain.model.LibraryLayoutPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class LibraryCompactControlsUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun shelfOptionsMenuRetainsViewSortAndCreateCallbacks() {
        var selectedView by mutableStateOf(LibraryView.BOOKS)
        var selectedSort by mutableStateOf(LibrarySort.RECENT)
        var created = false
        setLibraryScreen(
            uiState = LibraryUiState(
                books = listOf(sampleBook()),
                hasAnyBooks = true,
            ),
            onViewChanged = { selectedView = it },
            onSortChanged = { selectedSort = it },
            onCreateGroup = { created = true },
        )

        compose.onNodeWithTag("library-options-button").performClick()
        compose.onNodeWithText("系列").performClick()
        assertEquals(LibraryView.SERIES, selectedView)
        compose.onNodeWithTag("library-options-button").performClick()
        compose.onNodeWithText("书名").performClick()
        assertEquals(LibrarySort.TITLE, selectedSort)
        compose.onNodeWithTag("library-options-button").performClick()
        compose.onNodeWithText("新建集合").performClick()
        compose.onNodeWithText("集合名称").performTextInput("测试集合")
        compose.onNodeWithText("保存").performClick()
        assertTrue(created)
    }

    @Test
    fun statusFiltersExposeThreeStatesAndSecondTapClearsSelection() {
        var selected by mutableStateOf<LibraryFilter>(LibraryFilter.All)
        compose.setContent {
            MaterialTheme {
                LibraryGroupBar(
                    groups = emptyList(),
                    selectedFilter = selected,
                    onFilterSelected = { selected = it },
                    onCreateGroup = {},
                    onRenameGroup = {},
                    onDeleteGroup = {},
                )
            }
        }

        listOf("已读", "阅读中", "未读", "集合").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("全部").assertDoesNotExist()
        compose.onNodeWithText("阅读中").performClick()
        assertEquals(LibraryFilter.Reading, selected)
        compose.onNodeWithText("阅读中").performClick()
        assertEquals(LibraryFilter.All, selected)

        compose.onNodeWithTag("library-filter-more").performClick()
        listOf("最近阅读", "未加入集合").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun emptyGroupsDoNotReserveAGroupRow() {
        compose.setContent {
            MaterialTheme {
                LibraryGroupBar(
                    groups = emptyList(),
                    selectedFilter = LibraryFilter.All,
                    onFilterSelected = {},
                    onCreateGroup = {},
                    onRenameGroup = {},
                    onDeleteGroup = {},
                )
            }
        }

        compose.onNodeWithText("自定义分组").assertDoesNotExist()
        compose.onNodeWithText("新建集合").assertDoesNotExist()
    }

    @Test
    fun existingGroupChipRetainsSelectionRenameAndDeleteCallbacks() {
        val group = sampleGroup()
        var selected by mutableStateOf<LibraryFilter>(LibraryFilter.All)
        var renamed: BookGroup? = null
        var deleted: BookGroup? = null
        compose.setContent {
            MaterialTheme {
                LibraryGroupBar(
                    groups = listOf(group),
                    selectedFilter = selected,
                    onFilterSelected = { selected = it },
                    onCreateGroup = {},
                    onRenameGroup = { renamed = it },
                    onDeleteGroup = { deleted = it },
                )
            }
        }

        compose.onNodeWithTag("library-filter-more").performClick()
        compose.onNodeWithText(group.name).performClick()
        assertEquals(LibraryFilter.Group(group.id), selected)
        compose.onNodeWithTag("library-filter-more").performClick()
        compose.onNodeWithText("重命名当前集合").performClick()
        compose.onNodeWithTag("library-filter-more").performClick()
        compose.onNodeWithText("删除当前集合").performClick()
        assertEquals(group, renamed)
        assertEquals(group, deleted)
    }

    @Test
    fun compactControlsExposeSelectedStateAndRemainReachableAtTwoHundredPercentFont() {
        var selectedView by mutableStateOf(LibraryView.BOOKS)
        compose.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, 2f)) {
                MaterialTheme {
                    Column {
                        LibraryViewSelector(selected = selectedView, onSelected = { selectedView = it })
                        LibraryGroupBar(
                            groups = listOf(sampleGroup()),
                            selectedFilter = LibraryFilter.All,
                            onFilterSelected = {},
                            onCreateGroup = {},
                            onRenameGroup = {},
                            onDeleteGroup = {},
                        )
                    }
                }
            }
        }

        val viewButton = compose.onNodeWithTag("library-view-button").assertIsDisplayed()
        assertAtLeast48Dp(viewButton)
        viewButton.performClick()
        compose.onNodeWithTag("library-view-series").assertIsDisplayed().performClick()
        assertEquals(LibraryView.SERIES, selectedView)

        listOf("阅读中", "未读", "已读").forEach { label ->
            val node = compose.onNode(hasText(label) and hasClickAction()).assertIsDisplayed()
            assertAtLeast48Dp(node)
        }
        assertAtLeast48Dp(compose.onNodeWithTag("library-filter-more").assertIsDisplayed())
    }

    @Test
    fun firstBookStartsWithinCompactContentBudget() {
        setLibraryScreen(
            uiState = LibraryUiState(
                books = listOf(sampleBook()),
                hasAnyBooks = true,
            ),
        )

        val root = compose.onNodeWithTag("library_root").fetchSemanticsNode().boundsInRoot
        val firstBook = compose.onNodeWithTag("library_book_card_book-1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val maximumTop = 220f * compose.density.density
        assertTrue(
            "first book top ${firstBook.top - root.top}px exceeds compact budget $maximumTop px",
            firstBook.top - root.top < maximumTop,
        )
    }

    @Test
    fun realLibraryScreenKeepsCompactActionsReachableAtNarrowWidthAndLargeFont() {
        var selectedView by mutableStateOf(LibraryView.BOOKS)
        var selectedSort by mutableStateOf(LibrarySort.RECENT)
        var created = false
        setLibraryScreen(
            uiState = LibraryUiState(
                books = listOf(sampleBook()),
                hasAnyBooks = true,
            ),
            onViewChanged = { selectedView = it },
            onSortChanged = { selectedSort = it },
            onCreateGroup = { created = true },
            hostModifier = Modifier
                .width(320.dp)
                .height(720.dp)
                .testTag("narrow-library-host"),
            fontScale = 2f,
        )

        val host = compose.onNodeWithTag("narrow-library-host")
            .fetchSemanticsNode().boundsInRoot
        val options = compose.onNodeWithTag("library-options-button").assertIsDisplayed()
        assertWithinHost(options, host)
        assertAtLeast48Dp(options)
        options.performClick()
        val view = compose.onNodeWithTag("library-view-series")
            .assertIsDisplayed()
        assertAtLeast48Dp(view)
        view.performClick()
        assertEquals(LibraryView.SERIES, selectedView)

        options.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("书名").performClick()
        assertEquals(LibrarySort.TITLE, selectedSort)

        options.performClick()
        val create = compose.onNodeWithTag("library-create-group").assertIsDisplayed()
        create.performClick()
        compose.onNodeWithText("取消").performClick()
        assertTrue(!created)
    }

    @Test
    fun realLibrarySortMenuExposesCurrentAndNonCurrentSelectedState() {
        var selectedSort by mutableStateOf(LibrarySort.RECENT)
        setLibraryScreen(
            uiState = LibraryUiState(
                books = listOf(sampleBook()),
                hasAnyBooks = true,
            ),
            onSortChanged = { selectedSort = it },
        )

        compose.onNodeWithTag("library-options-button")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "书籍，按最近阅读排序",
                ),
            )
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("library-sort-option-recent")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithTag("library-sort-option-title")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .performClick()

        assertEquals(LibrarySort.TITLE, selectedSort)
    }

    @Test
    fun realLibraryScreenConfirmsGroupRenameAndDeleteCallbacks() {
        val group = sampleGroup()
        var renamedId: String? = null
        var renamedName: String? = null
        var deletedId: String? = null
        setLibraryScreen(
            uiState = LibraryUiState(
                books = listOf(sampleBook()),
                groups = listOf(group),
                filter = LibraryFilter.Group(group.id),
                hasAnyBooks = true,
            ),
            onRenameGroup = { id, name ->
                renamedId = id
                renamedName = name
            },
            onDeleteGroup = { deletedId = it },
        )

        compose.onNodeWithTag("library-filter-more").performScrollTo().performClick()
        compose.onNodeWithText("重命名当前集合").performClick()
        compose.onNodeWithText("集合名称").performTextReplacement("人文集合")
        compose.onNodeWithText("保存").performClick()
        assertEquals(group.id, renamedId)
        assertEquals("人文集合", renamedName)

        compose.onNodeWithTag("library-filter-more").performScrollTo().performClick()
        compose.onNodeWithText("删除当前集合").performClick()
        compose.onNodeWithTag("library-group-delete-confirm").performClick()
        assertEquals(group.id, deletedId)
    }

    @Test
    fun realLibraryScreenWithNoGroupsDoesNotReserveCustomGroupRow() {
        setLibraryScreen(
            uiState = LibraryUiState(
                books = listOf(sampleBook()),
                groups = emptyList(),
                hasAnyBooks = true,
            ),
        )

        val groupBar = compose.onNodeWithTag("library-group-bar")
            .performScrollTo()
            .assertIsDisplayed()
        val statusFilters = compose.onNodeWithTag("library-status-filters")
            .performScrollTo()
            .assertIsDisplayed()
        val groupBarBounds = groupBar.fetchSemanticsNode().boundsInRoot
        val statusBounds = statusFilters.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "empty group bar $groupBarBounds reserves more than status row $statusBounds",
            groupBarBounds.height <= statusBounds.height + 1f,
        )
        compose.onNodeWithTag("library-custom-groups-label").assertDoesNotExist()
        compose.onNodeWithTag("library-group-chips").assertDoesNotExist()
    }

    @Test
    fun layoutMenuEmitsListAndGridDensityPreferences() {
        var selected = LibraryLayoutPreference()
        setLibraryScreen(
            uiState = LibraryUiState(
                books = listOf(sampleBook()),
                hasAnyBooks = true,
            ),
            onLayoutPreferenceChanged = { selected = it },
        )

        compose.onNodeWithTag("library-layout-button").performClick()
        compose.onNodeWithTag("library-layout-list").performClick()
        assertEquals(LibraryLayoutMode.COMPACT_LIST, selected.mode)

        compose.onNodeWithTag("library-layout-button").performClick()
        compose.onNodeWithTag("library-density-compact").performClick()
        assertEquals(LibraryLayoutMode.COVER_GRID, selected.mode)
        assertEquals(LibraryGridDensity.COMPACT, selected.gridDensity)
    }

    @Test
    fun standardGridShowsThreeColumnsAndSixBooksOnCompactPhone() {
        val books = (1..9).map(::sampleBook)
        setLibraryScreen(
            uiState = LibraryUiState(
                books = books,
                hasAnyBooks = true,
                layoutPreference = LibraryLayoutPreference(
                    mode = LibraryLayoutMode.COVER_GRID,
                    gridDensity = LibraryGridDensity.STANDARD,
                ),
            ),
            hostModifier = Modifier
                .width(360.dp)
                .height(800.dp)
                .testTag("grid-library-host"),
            fontScale = 1f,
        )

        val firstRow = (1..3).map { index ->
            compose.onNodeWithTag("library_book_title_book-$index", useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
        }
        val secondRow = (4..6).map { index ->
            compose.onNodeWithTag("library_book_title_book-$index", useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
        }
        val tolerance = 4f * compose.density.density
        assertTrue(firstRow.all { abs(it.top - firstRow.first().top) <= tolerance })
        assertTrue(secondRow.all { abs(it.top - secondRow.first().top) <= tolerance })
        assertTrue(secondRow.first().top > firstRow.first().top)
    }

    private fun setLibraryScreen(
        uiState: LibraryUiState,
        onViewChanged: (LibraryView) -> Unit = {},
        onSortChanged: (LibrarySort) -> Unit = {},
        onCreateGroup: (String) -> Unit = {},
        onRenameGroup: (String, String) -> Unit = { _, _ -> },
        onDeleteGroup: (String) -> Unit = {},
        onLayoutPreferenceChanged: (LibraryLayoutPreference) -> Unit = {},
        hostModifier: Modifier = Modifier,
        fontScale: Float? = null,
    ) {
        compose.setContent {
            if (fontScale == null) {
                MaterialTheme {
                    LibraryScreen(
                        uiState = uiState,
                        onImport = {},
                        onOpenBook = {},
                        onDismissError = {},
                        onQueryChanged = {},
                        onSortChanged = onSortChanged,
                        onFilterChanged = {},
                        onCreateGroup = onCreateGroup,
                        onRenameGroup = onRenameGroup,
                        onDeleteGroup = onDeleteGroup,
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
                        onViewChanged = onViewChanged,
                        onLayoutPreferenceChanged = onLayoutPreferenceChanged,
                    )
                }
            } else {
                val systemDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, fontScale)) {
                    Box(modifier = hostModifier) {
                        MaterialTheme {
                            LibraryScreen(
                                uiState = uiState,
                                onImport = {},
                                onOpenBook = {},
                                onDismissError = {},
                                onQueryChanged = {},
                                onSortChanged = onSortChanged,
                                onFilterChanged = {},
                                onCreateGroup = onCreateGroup,
                                onRenameGroup = onRenameGroup,
                                onDeleteGroup = onDeleteGroup,
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
                                onViewChanged = onViewChanged,
                                onLayoutPreferenceChanged = onLayoutPreferenceChanged,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun assertWithinHost(node: SemanticsNodeInteraction, host: Rect) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue("$bounds overflows host $host", bounds.left >= host.left && bounds.right <= host.right)
        assertTrue("$bounds overflows host $host", bounds.top >= host.top && bounds.bottom <= host.bottom)
    }

    private fun assertAtLeast48Dp(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val minimum = 48f * compose.density.density
        assertTrue("$bounds height is below 48dp", bounds.height >= minimum)
    }

    private fun sampleBook() = Book(
        id = "book-1",
        title = "结构化 TXT 示例",
        author = "测试作者",
        originalFileName = "public-structure.txt",
        originalPath = "books/book-1/original.txt",
        normalizedPath = "books/book-1/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "public-structure",
        contentLength = 100,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = null,
    )

    private fun sampleBook(index: Int) = sampleBook().copy(
        id = "book-$index",
        title = "结构化 TXT 示例 $index",
        originalFileName = "public-structure-$index.txt",
        normalizedPath = "books/book-$index/content.txt",
        contentSha256 = "public-structure-$index",
        createdAtEpochMillis = index.toLong(),
    )

    private fun sampleGroup() = BookGroup(
        id = "collection-1",
        name = "科幻",
        sortOrder = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}
