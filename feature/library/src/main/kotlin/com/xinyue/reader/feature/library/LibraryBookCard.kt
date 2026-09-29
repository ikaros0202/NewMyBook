package com.xinyue.reader.feature.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.LibraryLayoutMode
import com.xinyue.reader.core.ui.BookCover
import com.xinyue.reader.core.ui.XinYueIcons

@Composable
internal fun LibraryBookCard(
    book: Book,
    progressFraction: Double,
    selectionMode: Boolean,
    selected: Boolean,
    menuExpanded: Boolean,
    onOpen: () -> Unit,
    onEnterSelection: () -> Unit,
    onToggleSelection: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onStatistics: () -> Unit,
    onDelete: () -> Unit,
    layoutMode: LibraryLayoutMode = LibraryLayoutMode.COMPACT_LIST,
    showContinue: Boolean = false,
) {
    when (layoutMode) {
        LibraryLayoutMode.COVER_GRID -> CoverGridBookCard(
            book = book,
            progressFraction = progressFraction,
            selectionMode = selectionMode,
            selected = selected,
            menuExpanded = menuExpanded,
            onOpen = onOpen,
            onEnterSelection = onEnterSelection,
            onToggleSelection = onToggleSelection,
            onMenuExpandedChange = onMenuExpandedChange,
            onEdit = onEdit,
            onStatistics = onStatistics,
            onDelete = onDelete,
            showContinue = showContinue,
        )

        LibraryLayoutMode.COMPACT_LIST -> CompactListBookCard(
            book = book,
            progressFraction = progressFraction,
            selectionMode = selectionMode,
            selected = selected,
            menuExpanded = menuExpanded,
            onOpen = onOpen,
            onEnterSelection = onEnterSelection,
            onToggleSelection = onToggleSelection,
            onMenuExpandedChange = onMenuExpandedChange,
            onEdit = onEdit,
            onStatistics = onStatistics,
            onDelete = onDelete,
            showContinue = showContinue,
        )
    }
}

@Composable
private fun CompactListBookCard(
    book: Book,
    progressFraction: Double,
    selectionMode: Boolean,
    selected: Boolean,
    menuExpanded: Boolean,
    onOpen: () -> Unit,
    onEnterSelection: () -> Unit,
    onToggleSelection: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onStatistics: () -> Unit,
    onDelete: () -> Unit,
    showContinue: Boolean,
) {
    val safeProgress = progressFraction.coerceIn(0.0, 1.0)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_book_card_${book.id}")
            .semantics { this.selected = selected }
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelection() else onOpen() },
                onLongClick = onEnterSelection,
            ),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 82.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BookCover(book = book, modifier = Modifier.width(44.dp).height(66.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = book.title }
                            .testTag("library_book_title_${book.id}"),
                    )
                    if (showContinue) {
                        Text(
                            text = "继续",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                bookSecondaryLabel(book)?.let { secondary ->
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (book.finished) "已读完" else libraryProgressLabel(safeProgress),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LinearProgressIndicator(
                    progress = { safeProgress.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
            BookMenuAnchor(
                book = book,
                visible = !selectionMode,
                expanded = menuExpanded,
                onExpandedChange = onMenuExpandedChange,
                onEdit = onEdit,
                onStatistics = onStatistics,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun CoverGridBookCard(
    book: Book,
    progressFraction: Double,
    selectionMode: Boolean,
    selected: Boolean,
    menuExpanded: Boolean,
    onOpen: () -> Unit,
    onEnterSelection: () -> Unit,
    onToggleSelection: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onStatistics: () -> Unit,
    onDelete: () -> Unit,
    showContinue: Boolean,
) {
    val safeProgress = progressFraction.coerceIn(0.0, 1.0)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_book_card_${book.id}")
            .semantics { this.selected = selected }
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelection() else onOpen() },
                onLongClick = onEnterSelection,
            ),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(if (selected) 4.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                BookCover(book = book, modifier = Modifier.fillMaxSize())
                if (showContinue) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "继续",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
                BookMenuAnchor(
                    book = book,
                    visible = !selectionMode,
                    expanded = menuExpanded,
                    onExpandedChange = onMenuExpandedChange,
                    onEdit = onEdit,
                    onStatistics = onStatistics,
                    onDelete = onDelete,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            Text(
                text = book.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = book.title }
                    .testTag("library_book_title_${book.id}"),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = bookSecondaryLabel(book).orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (book.finished) "已读" else "${(safeProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            LinearProgressIndicator(
                progress = { safeProgress.toFloat() },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }
    }
}

@Composable
private fun BookMenuAnchor(
    book: Book,
    visible: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onStatistics: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(48.dp)) {
        if (visible) {
            IconButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("library_book_menu_button_${book.id}"),
            ) {
                Icon(
                    painter = painterResource(XinYueIcons.MoreVert),
                    contentDescription = "《${book.title}》更多操作",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = visible && expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.testTag("library_book_menu_${book.id}"),
        ) {
            DropdownMenuItem(
                text = { Text("编辑") },
                onClick = onEdit,
                modifier = Modifier.testTag("library_book_edit_${book.id}"),
            )
            DropdownMenuItem(
                text = { Text("统计") },
                onClick = onStatistics,
                modifier = Modifier.testTag("library_book_statistics_${book.id}"),
            )
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = onDelete,
                modifier = Modifier.testTag("library_book_delete_${book.id}"),
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
            )
        }
    }
}

private fun bookSecondaryLabel(book: Book): String? =
    book.author?.takeIf(String::isNotBlank)
        ?: book.seriesName?.takeIf(String::isNotBlank)
