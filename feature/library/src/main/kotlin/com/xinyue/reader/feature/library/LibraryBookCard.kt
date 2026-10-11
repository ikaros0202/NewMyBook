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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
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
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        } else {
            Color.Transparent
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp)
                .padding(horizontal = 6.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BookCover(book = book, modifier = Modifier.width(56.dp).height(84.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
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
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                bookSecondaryLabel(book)?.let { secondary ->
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = if (book.finished) "已读完" else libraryProgressLabel(safeProgress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                )
            }
            BookMenuAnchor(
                book = book,
                visible = !selectionMode,
                expanded = menuExpanded,
                onExpandedChange = onMenuExpandedChange,
                onSelect = {
                    onMenuExpandedChange(false)
                    onEnterSelection()
                },
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .shadow(1.dp, RoundedCornerShape(4.dp), clip = false),
            ) {
                BookCover(book = book, modifier = Modifier.fillMaxSize())
            }
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = book.title }
                    .testTag("library_book_title_${book.id}"),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    bookSecondaryLabel(book)?.let { secondary ->
                        Text(
                            text = secondary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = libraryGridProgressLabel(book, safeProgress, showContinue),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    )
                }
                BookMenuAnchor(
                    book = book,
                    visible = !selectionMode,
                    expanded = menuExpanded,
                    onExpandedChange = onMenuExpandedChange,
                    onSelect = {
                        onMenuExpandedChange(false)
                        onEnterSelection()
                    },
                    onEdit = onEdit,
                    onStatistics = onStatistics,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun BookMenuAnchor(
    book: Book,
    visible: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: () -> Unit,
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
                text = { Text("选择") },
                onClick = onSelect,
                modifier = Modifier.testTag("library_book_select_${book.id}"),
            )
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

private fun libraryGridProgressLabel(
    book: Book,
    fraction: Double,
    showContinue: Boolean,
): String {
    val safeFraction = fraction.coerceIn(0.0, 1.0)
    val progress = when {
        book.finished -> "已读"
        safeFraction <= 0.0 -> "未读"
        else -> libraryProgressLabel(safeFraction).removePrefix("阅读进度 ")
    }
    return if (showContinue && !book.finished) "继续 · $progress" else progress
}
