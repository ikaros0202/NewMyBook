package com.xinyue.reader.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import com.xinyue.reader.core.domain.model.BookGroup

@Composable
internal fun LibrarySelectionBar(
    selectedCount: Int,
    groups: List<BookGroup>,
    actionInProgress: Boolean,
    onSelectAll: () -> Unit,
    onMove: (String?) -> Unit,
    onAddToCollection: ((String) -> Unit)? = null,
    onRemoveFromCollection: ((String) -> Unit)? = null,
    onClearCollections: (() -> Unit)? = null,
    onMarkFinished: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var moveMenuExpanded by remember { mutableStateOf(false) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var removeMenuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .semantics { stateDescription = "已选择 $selectedCount 本书" }
            .testTag("library-selection-bar"),
    ) {
        Text("已选择 $selectedCount 本", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = onSelectAll, enabled = !actionInProgress) { Text("全选当前结果") }
            TextButton(onClick = { moveMenuExpanded = true }, enabled = !actionInProgress) { Text("移动到") }
            DropdownMenu(
                expanded = moveMenuExpanded,
                onDismissRequest = { moveMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("未加入集合") },
                    onClick = {
                        moveMenuExpanded = false
                        onMove(null)
                    },
                )
                groups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group.name) },
                        onClick = {
                            moveMenuExpanded = false
                            onMove(group.id)
                        },
                    )
                }
            }
            if (onAddToCollection != null) {
                TextButton(
                    onClick = { addMenuExpanded = true },
                    enabled = !actionInProgress && groups.isNotEmpty(),
                ) { Text("加入集合") }
                DropdownMenu(
                    expanded = addMenuExpanded,
                    onDismissRequest = { addMenuExpanded = false },
                ) {
                    groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.name) },
                            onClick = {
                                addMenuExpanded = false
                                onAddToCollection(group.id)
                            },
                        )
                    }
                }
            }
            if (onRemoveFromCollection != null) {
                TextButton(
                    onClick = { removeMenuExpanded = true },
                    enabled = !actionInProgress && groups.isNotEmpty(),
                ) { Text("移出集合") }
                DropdownMenu(
                    expanded = removeMenuExpanded,
                    onDismissRequest = { removeMenuExpanded = false },
                ) {
                    groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.name) },
                            onClick = {
                                removeMenuExpanded = false
                                onRemoveFromCollection(group.id)
                            },
                        )
                    }
                }
            }
            if (onClearCollections != null) {
                TextButton(onClick = onClearCollections, enabled = !actionInProgress) {
                    Text("清空集合")
                }
            }
            TextButton(onClick = { onMarkFinished(true) }, enabled = !actionInProgress) { Text("标记已读完") }
            TextButton(onClick = { onMarkFinished(false) }, enabled = !actionInProgress) { Text("取消已读完") }
            TextButton(onClick = onDelete, enabled = !actionInProgress) { Text("删除") }
            TextButton(onClick = onCancel, enabled = !actionInProgress) { Text("取消选择") }
        }
    }
}
