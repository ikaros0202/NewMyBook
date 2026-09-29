package com.xinyue.reader.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.BookGroup
import com.xinyue.reader.core.domain.model.LibraryGridDensity
import com.xinyue.reader.core.domain.model.LibraryLayoutMode
import com.xinyue.reader.core.domain.model.LibraryLayoutPreference
import com.xinyue.reader.core.ui.XinYueIcons

internal enum class PrimaryImportPlacement { INLINE_EMPTY, TOP_BAR }

internal fun primaryImportPlacement(hasAnyBooks: Boolean): PrimaryImportPlacement =
    if (hasAnyBooks) PrimaryImportPlacement.TOP_BAR else PrimaryImportPlacement.INLINE_EMPTY

internal sealed interface LibraryTransientMenu {
    data object None : LibraryTransientMenu
    data object Sort : LibraryTransientMenu
    data object Layout : LibraryTransientMenu
    data object Options : LibraryTransientMenu
    data class Book(val bookId: String) : LibraryTransientMenu
}

@Composable
fun LibraryRoute(
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    onOpenBook: (String) -> Unit,
    onImport: () -> Unit,
    onOpenStatistics: (String?) -> Unit,
    onOpenBackup: () -> Unit = {},
    showTopActions: Boolean = true,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var coverBookId by remember { mutableStateOf<String?>(null) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val bookId = coverBookId
        coverBookId = null
        if (bookId != null && uri != null) viewModel.importCover(bookId, uri.toString())
    }

    LibraryScreen(
        uiState = uiState,
        onImport = onImport,
        onOpenBook = onOpenBook,
        onDismissError = viewModel::dismissError,
        onQueryChanged = viewModel::setQuery,
        onSortChanged = viewModel::setSort,
        onFilterChanged = viewModel::setFilter,
        onViewChanged = viewModel::setView,
        onLayoutPreferenceChanged = viewModel::setLayoutPreference,
        onCreateGroup = viewModel::createGroup,
        onRenameGroup = viewModel::renameGroup,
        onDeleteGroup = viewModel::deleteGroup,
        onEditBook = { bookId, title, author ->
            viewModel.updateBookMetadata(bookId, title, author)
        },
        onEditBookAssets = { bookId, title, author, seriesName, seriesOrder, collectionIds ->
            viewModel.updateBookMetadata(
                bookId = bookId,
                title = title,
                author = author,
                seriesName = seriesName,
                seriesOrder = seriesOrder,
                collectionIds = collectionIds,
            )
        },
        onDeleteBook = viewModel::deleteBook,
        onPickCover = { bookId ->
            coverBookId = bookId
            coverPicker.launch(arrayOf("image/*"))
        },
        onClearCover = viewModel::clearCover,
        onEnterSelection = viewModel::enterSelection,
        onToggleSelection = viewModel::toggleSelection,
        onSelectAllVisible = viewModel::selectAllVisible,
        onClearSelection = viewModel::clearSelection,
        onMoveSelected = viewModel::moveSelectedBooks,
        onAddSelectedToCollection = viewModel::addSelectedBooksToCollection,
        onRemoveSelectedFromCollection = viewModel::removeSelectedBooksFromCollection,
        onClearSelectedCollections = viewModel::clearSelectedCollections,
        onMarkSelectedFinished = viewModel::markSelectedFinished,
        onDeleteSelected = viewModel::requestDeleteSelected,
        onUndoDelete = viewModel::undoPendingDeletion,
        onOpenStatistics = onOpenStatistics,
        onOpenBackup = onOpenBackup,
        showTopActions = showTopActions,
        isSearchActive = isSearchActive,
        onSearchActiveChange = onSearchActiveChange,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onImport: () -> Unit,
    onOpenBook: (String) -> Unit,
    onDismissError: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSortChanged: (LibrarySort) -> Unit,
    onFilterChanged: (LibraryFilter) -> Unit,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onEditBook: (String, String, String?) -> Unit,
    onDeleteBook: (String) -> Unit,
    onPickCover: (String) -> Unit,
    onClearCover: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAllVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onMoveSelected: (String?) -> Unit,
    onMarkSelectedFinished: (Boolean) -> Unit,
    onDeleteSelected: () -> Unit,
    onUndoDelete: () -> Unit,
    onOpenStatistics: (String?) -> Unit,
    onOpenBackup: () -> Unit = {},
    onViewChanged: (LibraryView) -> Unit = {},
    onLayoutPreferenceChanged: (LibraryLayoutPreference) -> Unit = {},
    onEditBookAssets: (String, String, String?, String?, Int?, Set<String>) -> Unit =
        { bookId, title, author, _, _, _ -> onEditBook(bookId, title, author) },
    onAddSelectedToCollection: (String) -> Unit = onMoveSelected,
    onRemoveSelectedFromCollection: (String) -> Unit = {},
    onClearSelectedCollections: () -> Unit = {},
    showTopActions: Boolean = true,
    isSearchActive: Boolean = false,
    onSearchActiveChange: (Boolean) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var bookToRename by remember { mutableStateOf<Book?>(null) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }
    var creatingGroup by remember { mutableStateOf(false) }
    var groupToRename by remember { mutableStateOf<BookGroup?>(null) }
    var groupToDelete by remember { mutableStateOf<BookGroup?>(null) }
    var transientMenu by remember { mutableStateOf<LibraryTransientMenu>(LibraryTransientMenu.None) }
    val shelfGridState = rememberLazyGridState()
    val searchListState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onDismissError()
    }
    LaunchedEffect(uiState.pendingDeletion) {
        val pending = uiState.pendingDeletion ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "已选择删除 ${pending.bookIds.size} 本书",
            actionLabel = "撤销",
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoDelete()
    }

    val closeSearch = {
        transientMenu = LibraryTransientMenu.None
        keyboardController?.hide()
        onQueryChanged("")
        onSearchActiveChange(false)
    }
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(280)
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    SharedTransitionLayout(
        modifier = Modifier.fillMaxSize(),
    ) {
    AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        targetState = isSearchActive,
        transitionSpec = {
            (fadeIn(tween(280)) + scaleIn(initialScale = 0.98f)) togetherWith fadeOut(tween(180))
        },
        label = "library-search-transition",
    ) { active ->
        val sharedField = Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState("library-search-container"),
            animatedVisibilityScope = this@AnimatedContent,
            boundsTransform = { _, _ -> tween(280) },
        )
    if (active) {
        LibrarySearchScreen(
            uiState = uiState,
            fieldModifier = sharedField.focusRequester(searchFocusRequester),
            listState = searchListState,
            onQueryChanged = onQueryChanged,
            onBack = closeSearch,
            bookItem = { book ->
                LibraryBookCard(
                    book = book,
                    progressFraction = uiState.progressFractions[book.id] ?: 0.0,
                    selectionMode = false,
                    selected = false,
                    menuExpanded = transientMenu == LibraryTransientMenu.Book(book.id),
                    onOpen = { onOpenBook(book.id) },
                    onEnterSelection = { onEnterSelection(book.id) },
                    onToggleSelection = {},
                    onMenuExpandedChange = { expanded ->
                        transientMenu = if (expanded) {
                            LibraryTransientMenu.Book(book.id)
                        } else {
                            LibraryTransientMenu.None
                        }
                    },
                    onEdit = {
                        transientMenu = LibraryTransientMenu.None
                        bookToRename = book
                    },
                    onStatistics = {
                        transientMenu = LibraryTransientMenu.None
                        onOpenStatistics(book.id)
                    },
                    onDelete = {
                        transientMenu = LibraryTransientMenu.None
                        bookToDelete = book
                    },
                    layoutMode = LibraryLayoutMode.COMPACT_LIST,
                )
            },
        )
    } else {
    Scaffold(
        modifier = Modifier.testTag("library_root"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "书架",
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                        )
                        Surface(
                            onClick = {
                                transientMenu = LibraryTransientMenu.None
                                onSearchActiveChange(true)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .then(sharedField)
                                .testTag("library_search_trigger"),
                            shape = RectangleShape,
                            color = androidx.compose.ui.graphics.Color.Transparent,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    painter = painterResource(XinYueIcons.Search),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "搜索书名、作者或系列",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (primaryImportPlacement(uiState.hasAnyBooks) == PrimaryImportPlacement.TOP_BAR) {
                        IconButton(
                            onClick = onImport,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("library_import"),
                        ) {
                            Icon(
                                painter = painterResource(XinYueIcons.Add),
                                contentDescription = "导入 TXT",
                            )
                        }
                    }
                    LibraryLayoutSelector(
                        preference = uiState.layoutPreference,
                        expanded = transientMenu == LibraryTransientMenu.Layout,
                        onExpandedChange = { expanded ->
                            transientMenu = if (expanded) LibraryTransientMenu.Layout else LibraryTransientMenu.None
                        },
                        onSelected = { preference ->
                            transientMenu = LibraryTransientMenu.None
                            onLayoutPreferenceChanged(preference)
                        },
                    )
                    LibraryShelfOptionsMenu(
                        selectedView = uiState.view,
                        selectedSort = uiState.sort,
                        expanded = transientMenu == LibraryTransientMenu.Options,
                        onExpandedChange = { expanded ->
                            transientMenu = if (expanded) LibraryTransientMenu.Options else LibraryTransientMenu.None
                        },
                        onViewSelected = { view ->
                            transientMenu = LibraryTransientMenu.None
                            onViewChanged(view)
                        },
                        onSortSelected = { sort ->
                            transientMenu = LibraryTransientMenu.None
                            onSortChanged(sort)
                        },
                        onCreateGroup = {
                            transientMenu = LibraryTransientMenu.None
                            creatingGroup = true
                        },
                    )
                    if (showTopActions) {
                        TextButton(
                            onClick = onOpenBackup,
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("library_backup"),
                        ) { Text("备份") }
                        TextButton(onClick = { onOpenStatistics(null) }) { Text("统计") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            val isGrid = uiState.layoutPreference.mode == LibraryLayoutMode.COVER_GRID
            val columnCount = if (isGrid) {
                libraryGridColumns(
                    widthDp = maxWidth.value,
                    fontScale = LocalDensity.current.fontScale,
                    density = uiState.layoutPreference.gridDensity,
                )
            } else {
                1
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("library-shelf-list"),
                state = shelfGridState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(if (isGrid) 16.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isGrid) 12.dp else 0.dp),
            ) {
                if (uiState.isSelectionMode) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LibrarySelectionBar(
                            selectedCount = uiState.selectedBookIds.size,
                            groups = uiState.groups,
                            actionInProgress = uiState.selectionActionInProgress,
                            onSelectAll = onSelectAllVisible,
                            onMove = onMoveSelected,
                            onAddToCollection = onAddSelectedToCollection,
                            onRemoveFromCollection = onRemoveSelectedFromCollection,
                            onClearCollections = onClearSelectedCollections,
                            onMarkFinished = onMarkSelectedFinished,
                            onDelete = onDeleteSelected,
                            onCancel = onClearSelection,
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LibraryGroupBar(
                        groups = uiState.groups,
                        selectedFilter = uiState.filter,
                        onFilterSelected = onFilterChanged,
                        onCreateGroup = {},
                        onRenameGroup = { groupToRename = it },
                        onDeleteGroup = { groupToDelete = it },
                    )
                }
                if (uiState.books.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ShelfEmptyMessage(
                            shelfIsEmpty = !uiState.hasAnyBooks,
                            query = uiState.query,
                            onClearQuery = { onQueryChanged("") },
                            onImport = onImport,
                        )
                    }
                }
                if (uiState.view == LibraryView.BOOKS) {
                    gridItems(uiState.books, key = Book::id) { book ->
                        ShelfBookItem(
                            book = book,
                            uiState = uiState,
                            transientMenu = transientMenu,
                            onTransientMenuChanged = { transientMenu = it },
                            onOpenBook = onOpenBook,
                            onEnterSelection = onEnterSelection,
                            onToggleSelection = onToggleSelection,
                            onEdit = { bookToRename = it },
                            onStatistics = onOpenStatistics,
                            onDelete = { bookToDelete = it },
                        )
                    }
                } else {
                    uiState.sections.forEach { section ->
                        item(
                            key = "section-header:${section.key}",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            Text(
                                text = "${section.label} · ${section.books.size}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .testTag("library-section-${section.key}"),
                            )
                        }
                        gridItems(section.books, key = { book -> "${section.key}:${book.id}" }) { book ->
                            ShelfBookItem(
                                book = book,
                                uiState = uiState,
                                transientMenu = transientMenu,
                                onTransientMenuChanged = { transientMenu = it },
                                onOpenBook = onOpenBook,
                                onEnterSelection = onEnterSelection,
                                onToggleSelection = onToggleSelection,
                                onEdit = { bookToRename = it },
                                onStatistics = onOpenStatistics,
                                onDelete = { bookToDelete = it },
                            )
                        }
                    }
                }
            }
        }
    }
    }
    }
    }

    bookToRename?.let { book ->
        RenameBookDialog(
            book = book,
            groups = uiState.groups,
            selectedCollectionIds = uiState.collectionIdsByBook[book.id].orEmpty(),
            onConfirm = { title, author, seriesName, seriesOrder, collectionIds ->
                onEditBookAssets(book.id, title, author, seriesName, seriesOrder, collectionIds)
                bookToRename = null
            },
            onDismiss = { bookToRename = null },
            isCoverUpdating = book.id in uiState.coverUpdatingBookIds,
            onPickCover = { onPickCover(book.id) },
            onClearCover = { onClearCover(book.id) },
        )
    }
    bookToDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("删除《${book.title}》？") },
            text = { Text("书籍文件、阅读进度和书签将从本机删除，此操作无法撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBook(book.id)
                        bookToDelete = null
                    },
                    modifier = Modifier.testTag("library-delete-confirm"),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { bookToDelete = null }) { Text("取消") } },
        )
    }
    if (creatingGroup) {
        GroupNameDialog(
            title = "新建集合",
            onConfirm = {
                onCreateGroup(it)
                creatingGroup = false
            },
            onDismiss = { creatingGroup = false },
        )
    }
    groupToRename?.let { group ->
        GroupNameDialog(
            title = "重命名集合",
            initialName = group.name,
            onConfirm = {
                onRenameGroup(group.id, it)
                groupToRename = null
            },
            onDismiss = { groupToRename = null },
        )
    }
    groupToDelete?.let { group ->
        DeleteGroupDialog(
            group = group,
            onConfirm = {
                onDeleteGroup(group.id)
                groupToDelete = null
            },
            onDismiss = { groupToDelete = null },
        )
    }
}

@Composable
private fun ShelfBookItem(
    book: Book,
    uiState: LibraryUiState,
    transientMenu: LibraryTransientMenu,
    onTransientMenuChanged: (LibraryTransientMenu) -> Unit,
    onOpenBook: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onEdit: (Book) -> Unit,
    onStatistics: (String?) -> Unit,
    onDelete: (Book) -> Unit,
) {
    LibraryBookCard(
        book = book,
        progressFraction = uiState.progressFractions[book.id] ?: 0.0,
        selectionMode = uiState.isSelectionMode,
        selected = book.id in uiState.selectedBookIds,
        menuExpanded = transientMenu == LibraryTransientMenu.Book(book.id),
        onOpen = { onOpenBook(book.id) },
        onEnterSelection = { onEnterSelection(book.id) },
        onToggleSelection = { onToggleSelection(book.id) },
        onMenuExpandedChange = { expanded ->
            onTransientMenuChanged(
                if (expanded) LibraryTransientMenu.Book(book.id) else LibraryTransientMenu.None,
            )
        },
        onEdit = {
            onTransientMenuChanged(LibraryTransientMenu.None)
            onEdit(book)
        },
        onStatistics = {
            onTransientMenuChanged(LibraryTransientMenu.None)
            onStatistics(book.id)
        },
        onDelete = {
            onTransientMenuChanged(LibraryTransientMenu.None)
            onDelete(book)
        },
        layoutMode = uiState.layoutPreference.mode,
        showContinue = uiState.view == LibraryView.BOOKS && book.id == uiState.continueBookId,
    )
}

@Composable
private fun LibraryShelfOptionsMenu(
    selectedView: LibraryView,
    selectedSort: LibrarySort,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onViewSelected: (LibraryView) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onCreateGroup: () -> Unit,
) {
    Box(Modifier.wrapContentSize(Alignment.TopEnd)) {
        IconButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier
                .size(48.dp)
                .testTag("library-options-button")
                .semantics {
                    contentDescription = "书架选项"
                    stateDescription = "${selectedView.label}，按${selectedSort.label}排序"
                },
        ) {
            Icon(
                painter = painterResource(XinYueIcons.Sort),
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.testTag("library-options-menu"),
        ) {
            DropdownMenuItem(
                text = { Text("浏览方式") },
                onClick = {},
                enabled = false,
            )
            LibraryView.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = selectedCheck(option == selectedView),
                    onClick = { onViewSelected(option) },
                    modifier = Modifier
                        .testTag("library-view-${option.name.lowercase()}")
                        .semantics { selected = option == selectedView },
                )
            }
            DropdownMenuItem(
                text = { Text("排序") },
                onClick = {},
                enabled = false,
            )
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = selectedCheck(option == selectedSort),
                    onClick = { onSortSelected(option) },
                    modifier = Modifier
                        .testTag("library-sort-option-${option.name.lowercase()}")
                        .semantics { selected = option == selectedSort },
                )
            }
            DropdownMenuItem(
                text = { Text("新建集合") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(XinYueIcons.Add),
                        contentDescription = null,
                    )
                },
                onClick = onCreateGroup,
                modifier = Modifier.testTag("library-create-group"),
            )
        }
    }
}

@Composable
private fun LibraryLayoutSelector(
    preference: LibraryLayoutPreference,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (LibraryLayoutPreference) -> Unit,
) {
    Box(Modifier.wrapContentSize(Alignment.TopEnd)) {
        IconButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier
                .size(48.dp)
                .testTag("library-layout-button")
                .semantics {
                    contentDescription = when (preference.mode) {
                        LibraryLayoutMode.COVER_GRID -> "书架布局：封面网格"
                        LibraryLayoutMode.COMPACT_LIST -> "书架布局：紧凑列表"
                    }
                    stateDescription = libraryLayoutStateDescription(preference)
                },
        ) {
            Icon(
                painter = painterResource(
                    if (preference.mode == LibraryLayoutMode.COVER_GRID) {
                        XinYueIcons.GridView
                    } else {
                        XinYueIcons.ListView
                    },
                ),
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.testTag("library-layout-menu"),
        ) {
            DropdownMenuItem(
                text = { Text("封面网格") },
                trailingIcon = selectedCheck(preference.mode == LibraryLayoutMode.COVER_GRID),
                onClick = { onSelected(preference.copy(mode = LibraryLayoutMode.COVER_GRID)) },
                modifier = Modifier
                    .testTag("library-layout-grid")
                    .semantics { selected = preference.mode == LibraryLayoutMode.COVER_GRID },
            )
            DropdownMenuItem(
                text = { Text("紧凑列表") },
                trailingIcon = selectedCheck(preference.mode == LibraryLayoutMode.COMPACT_LIST),
                onClick = { onSelected(preference.copy(mode = LibraryLayoutMode.COMPACT_LIST)) },
                modifier = Modifier
                    .testTag("library-layout-list")
                    .semantics { selected = preference.mode == LibraryLayoutMode.COMPACT_LIST },
            )
            LibraryGridDensity.entries.forEach { density ->
                DropdownMenuItem(
                    text = { Text(density.label) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(XinYueIcons.GridView),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = selectedCheck(
                        preference.mode == LibraryLayoutMode.COVER_GRID &&
                            preference.gridDensity == density,
                    ),
                    onClick = {
                        onSelected(
                            preference.copy(
                                mode = LibraryLayoutMode.COVER_GRID,
                                gridDensity = density,
                            ),
                        )
                    },
                    modifier = Modifier
                        .testTag("library-density-${density.name.lowercase()}")
                        .semantics {
                            selected = preference.mode == LibraryLayoutMode.COVER_GRID &&
                                preference.gridDensity == density
                        },
                )
            }
        }
    }
}

@Composable
private fun selectedCheck(selected: Boolean): (@Composable () -> Unit)? = if (selected) {
    {
        Icon(
            painter = painterResource(XinYueIcons.Check),
            contentDescription = null,
        )
    }
} else {
    null
}

private val LibraryGridDensity.label: String
    get() = when (this) {
        LibraryGridDensity.COMFORTABLE -> "舒适密度"
        LibraryGridDensity.STANDARD -> "标准密度"
        LibraryGridDensity.COMPACT -> "紧凑密度"
    }

private fun libraryLayoutStateDescription(preference: LibraryLayoutPreference): String =
    if (preference.mode == LibraryLayoutMode.COMPACT_LIST) {
        "当前为紧凑列表"
    } else {
        "当前为${preference.gridDensity.label}"
    }

@Composable
private fun LibraryCompactControls(
    selectedView: LibraryView,
    onViewChanged: (LibraryView) -> Unit,
    selectedSort: LibrarySort,
    sortExpanded: Boolean,
    onSortExpandedChange: (Boolean) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onCreateGroup: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-compact-controls"),
    ) {
        val narrowLayout = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.5f
        val rowModifier = if (narrowLayout) {
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("library-compact-controls-scroll")
        } else {
            Modifier.fillMaxWidth()
        }
        LibraryCompactControlsRow(
            modifier = rowModifier,
            selectedView = selectedView,
            onViewChanged = onViewChanged,
            selectedSort = selectedSort,
            sortExpanded = sortExpanded,
            onSortExpandedChange = onSortExpandedChange,
            onSortSelected = onSortSelected,
            onCreateGroup = onCreateGroup,
            viewUsesRemainingWidth = !narrowLayout,
            viewScrollable = !narrowLayout,
        )
    }
}

@Composable
private fun LibraryCompactControlsRow(
    modifier: Modifier,
    selectedView: LibraryView,
    onViewChanged: (LibraryView) -> Unit,
    selectedSort: LibrarySort,
    sortExpanded: Boolean,
    onSortExpandedChange: (Boolean) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onCreateGroup: () -> Unit,
    viewUsesRemainingWidth: Boolean,
    viewScrollable: Boolean,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LibraryViewSelector(
            selected = selectedView,
            onSelected = onViewChanged,
            modifier = if (viewUsesRemainingWidth) Modifier.weight(1f) else Modifier,
            scrollable = viewScrollable,
        )
        LibrarySortSelector(
            selected = selectedSort,
            expanded = sortExpanded,
            onExpandedChange = onSortExpandedChange,
            onSelected = onSortSelected,
            modifier = Modifier,
        )
        TextButton(
            onClick = onCreateGroup,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("library-create-group"),
        ) {
            Icon(
                painter = painterResource(XinYueIcons.Add),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text("新建集合", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
internal fun LibrarySortSelector(
    selected: LibrarySort,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (LibrarySort) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
    ) {
        Box(Modifier.wrapContentSize(Alignment.TopEnd)) {
            TextButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("library_sort_button")
                    .semantics {
                        contentDescription = "排序：${selected.label}"
                        stateDescription = "当前排序：${selected.label}"
                    },
            ) {
                Icon(
                    painter = painterResource(XinYueIcons.Sort),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(selected.label, modifier = Modifier.padding(start = 6.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.testTag("library_sort_menu"),
            ) {
                LibrarySort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        trailingIcon = if (option == selected) {
                            {
                                Icon(
                                    painter = painterResource(XinYueIcons.Check),
                                    contentDescription = null,
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .testTag("library-sort-option-${option.name.lowercase()}")
                            .semantics {
                                this.selected = option == selected
                            },
                        onClick = {
                            onExpandedChange(false)
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}

private val LibrarySort.label: String
    get() = when (this) {
        LibrarySort.RECENT -> "最近阅读"
        LibrarySort.IMPORTED -> "导入时间"
        LibrarySort.TITLE -> "书名"
    }

@Composable
private fun ShelfEmptyMessage(
    shelfIsEmpty: Boolean,
    query: String,
    onClearQuery: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(XinYueIcons.Library),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            when {
                shelfIsEmpty -> "书架还是空的"
                query.isNotBlank() -> "没有匹配“${query.trim()}”的书籍"
                else -> "当前视图没有书籍"
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (shelfIsEmpty) {
            Text(
                "导入手机中的 TXT 小说开始阅读",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Button(onClick = onImport, modifier = Modifier.testTag("library_empty_import")) {
                Icon(
                    painter = painterResource(XinYueIcons.Add),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text("选择 TXT 文件", modifier = Modifier.padding(start = 8.dp))
            }
        } else if (query.isNotBlank()) {
            TextButton(onClick = onClearQuery) { Text("清除搜索") }
        }
    }
}

internal fun libraryProgressLabel(fraction: Double): String {
    val safeFraction = fraction.coerceIn(0.0, 1.0)
    return when {
        safeFraction <= 0.0 -> "未开始"
        safeFraction < 0.01 -> "阅读进度 <1%"
        else -> "阅读进度 ${(safeFraction * 100).roundToInt()}%"
    }
}

@Composable
internal fun RenameBookDialog(
    book: Book,
    groups: List<BookGroup>,
    selectedCollectionIds: Set<String>,
    onConfirm: (String, String?, String?, Int?, Set<String>) -> Unit,
    onDismiss: () -> Unit,
    isCoverUpdating: Boolean,
    onPickCover: () -> Unit,
    onClearCover: () -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author.orEmpty()) }
    var seriesName by remember(book.id) { mutableStateOf(book.seriesName.orEmpty()) }
    var seriesOrderText by remember(book.id) { mutableStateOf(book.seriesOrder?.toString().orEmpty()) }
    var collectionIds by remember(book.id, selectedCollectionIds) {
        mutableStateOf(selectedCollectionIds)
    }
    val parsedSeriesOrder = seriesOrderText.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
    val seriesOrderInvalid = seriesOrderText.isNotBlank() &&
        (parsedSeriesOrder == null || parsedSeriesOrder !in 0..9_999 || seriesName.isBlank())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑书籍信息") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("书名") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("作者（可选）") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 10.dp),
                )
                OutlinedTextField(
                    value = seriesName,
                    onValueChange = { seriesName = it },
                    label = { Text("系列（可选）") },
                    singleLine = true,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .testTag("library-series-name"),
                )
                OutlinedTextField(
                    value = seriesOrderText,
                    onValueChange = { value ->
                        seriesOrderText = value.filter(Char::isDigit).take(4)
                    },
                    label = { Text("系列序号（可选，0–9999）") },
                    singleLine = true,
                    isError = seriesOrderInvalid,
                    supportingText = if (seriesOrderInvalid) {
                        { Text("填写序号前需填写系列，序号范围为 0–9999") }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .testTag("library-series-order"),
                )
                if (groups.isNotEmpty()) {
                    Text(
                        text = "所属集合",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    groups.forEach { group ->
                        val selected = group.id in collectionIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .sizeIn(minHeight = 48.dp)
                                .clickable {
                                    collectionIds = if (selected) {
                                        collectionIds - group.id
                                    } else {
                                        collectionIds + group.id
                                    }
                                }
                                .testTag("library-collection-${group.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = selected, onCheckedChange = null)
                            Text(group.name)
                        }
                    }
                }
                Text(
                    text = if (isCoverUpdating) "正在处理封面…" else if (book.customCoverPath != null) "正在使用自定义封面" else "正在使用默认封面",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row {
                    TextButton(
                        onClick = onPickCover,
                        enabled = !isCoverUpdating,
                        modifier = Modifier.testTag("library-cover-select"),
                    ) { Text(if (book.customCoverPath == null) "选择封面" else "更换封面") }
                    if (book.customCoverPath != null) {
                        TextButton(
                            onClick = onClearCover,
                            enabled = !isCoverUpdating,
                            modifier = Modifier.testTag("library-cover-clear"),
                        ) { Text("恢复默认封面") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        title,
                        author.takeIf(String::isNotBlank),
                        seriesName.takeIf(String::isNotBlank),
                        parsedSeriesOrder,
                        collectionIds,
                    )
                },
                enabled = title.isNotBlank() && !seriesOrderInvalid,
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
