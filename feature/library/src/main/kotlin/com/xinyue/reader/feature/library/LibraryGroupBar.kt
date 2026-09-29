package com.xinyue.reader.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.BookGroup
import com.xinyue.reader.core.ui.XinYueIcons

@Composable
internal fun LibraryGroupBar(
    groups: List<BookGroup>,
    selectedFilter: LibraryFilter,
    onFilterSelected: (LibraryFilter) -> Unit,
    @Suppress("UNUSED_PARAMETER") onCreateGroup: () -> Unit,
    onRenameGroup: (BookGroup) -> Unit,
    onDeleteGroup: (BookGroup) -> Unit,
) {
    var moreExpanded by remember { mutableStateOf(false) }
    val selectedGroup = (selectedFilter as? LibraryFilter.Group)?.let { filter ->
        groups.firstOrNull { it.id == filter.groupId }
    }
    Column(modifier = Modifier.fillMaxWidth().testTag("library-group-bar")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("library-status-filters"),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        statusFilters.forEach { item ->
            val isSelected = selectedFilter == item.filter
            StatusFilterButton(
                label = item.label,
                selected = isSelected,
                onClick = {
                    onFilterSelected(if (isSelected) LibraryFilter.All else item.filter)
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(item.tag),
            )
        }

        Box {
            val moreSelected = selectedFilter == LibraryFilter.Recent ||
                selectedFilter == LibraryFilter.Uncollected ||
                selectedGroup != null
            Surface(
                onClick = { moreExpanded = true },
                color = androidx.compose.ui.graphics.Color.Transparent,
                shape = androidx.compose.ui.graphics.RectangleShape,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("library-filter-more")
                    .semantics { this.selected = moreSelected },
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedGroup?.name ?: if (moreSelected) selectedFilter.label else "集合",
                        color = if (moreSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Icon(
                        painter = painterResource(XinYueIcons.MoreHorizontal),
                        contentDescription = "更多集合筛选",
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = moreExpanded,
                onDismissRequest = { moreExpanded = false },
            ) {
                FilterMenuItem(
                    label = "全部书籍",
                    selected = selectedFilter == LibraryFilter.All,
                    tag = "library-filter-option-all",
                    onClick = {
                        moreExpanded = false
                        onFilterSelected(LibraryFilter.All)
                    },
                )
                FilterMenuItem(
                    label = "最近阅读",
                    selected = selectedFilter == LibraryFilter.Recent,
                    tag = "library-status-recent",
                    onClick = {
                        moreExpanded = false
                        onFilterSelected(LibraryFilter.Recent)
                    },
                )
                FilterMenuItem(
                    label = "未加入集合",
                    selected = selectedFilter == LibraryFilter.Uncollected,
                    tag = "library-status-uncollected",
                    onClick = {
                        moreExpanded = false
                        onFilterSelected(LibraryFilter.Uncollected)
                    },
                )
                groups.forEach { group ->
                    val groupFilter = LibraryFilter.Group(group.id)
                    FilterMenuItem(
                        label = group.name,
                        selected = selectedFilter == groupFilter,
                        tag = "library-group-${group.id}",
                        onClick = {
                            moreExpanded = false
                            onFilterSelected(groupFilter)
                        },
                    )
                }
                if (selectedGroup != null) {
                    DropdownMenuItem(
                        text = { Text("重命名当前集合") },
                        onClick = {
                            moreExpanded = false
                            onRenameGroup(selectedGroup)
                        },
                        modifier = Modifier.testTag("library-group-rename"),
                    )
                    DropdownMenuItem(
                        text = { Text("删除当前集合") },
                        onClick = {
                            moreExpanded = false
                            onDeleteGroup(selectedGroup)
                        },
                        modifier = Modifier.testTag("library-group-delete"),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun StatusFilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = androidx.compose.ui.graphics.RectangleShape,
        modifier = modifier.semantics { this.selected = selected },
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 2.dp)
                    .padding(top = 2.dp),
            ) {
                if (selected) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 2.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterMenuItem(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(XinYueIcons.Check),
                    contentDescription = null,
                )
            }
        } else {
            null
        },
        onClick = onClick,
        modifier = Modifier
            .testTag(tag)
            .semantics { this.selected = selected },
    )
}

private data class StatusFilterItem(
    val filter: LibraryFilter,
    val label: String,
    val tag: String,
)

private val statusFilters = listOf(
    StatusFilterItem(LibraryFilter.Finished, "已读", "library-status-finished"),
    StatusFilterItem(LibraryFilter.Reading, "阅读中", "library-status-reading"),
    StatusFilterItem(LibraryFilter.Unread, "未读", "library-status-unread"),
)

private val LibraryFilter.label: String
    get() = when (this) {
        LibraryFilter.All -> "全部"
        LibraryFilter.Recent -> "最近阅读"
        LibraryFilter.Unread -> "未读"
        LibraryFilter.Reading -> "阅读中"
        LibraryFilter.Finished -> "已读"
        LibraryFilter.Uncollected -> "未加入集合"
        is LibraryFilter.Group -> "集合"
    }
