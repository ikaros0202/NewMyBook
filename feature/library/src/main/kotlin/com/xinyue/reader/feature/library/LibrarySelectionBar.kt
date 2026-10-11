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

private enum class CollectionMenuMode {
    ROOT,
    MOVE,
    ADD,
    REMOVE,
}

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
    var collectionMenuMode by remember { mutableStateOf<CollectionMenuMode?>(null) }
    var statusMenuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .semantics { stateDescription = "已选择 $selectedCount 本书" }
            .testTag("library-selection-bar"),
    ) {
        Text("已选择 $selectedCount 本", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = onSelectAll, enabled = !actionInProgress) { Text("全选当前结果") }
            TextButton(
                onClick = { collectionMenuMode = CollectionMenuMode.ROOT },
                enabled = !actionInProgress,
            ) { Text("集合") }
            DropdownMenu(
                expanded = collectionMenuMode != null,
                onDismissRequest = { collectionMenuMode = null },
            ) {
                when (collectionMenuMode) {
                    CollectionMenuMode.ROOT -> {
                        DropdownMenuItem(
                            text = { Text("移动到") },
                            onClick = { collectionMenuMode = CollectionMenuMode.MOVE },
                        )
                        if (onAddToCollection != null) {
                            DropdownMenuItem(
                                text = { Text("加入集合") },
                                enabled = groups.isNotEmpty(),
                                onClick = { collectionMenuMode = CollectionMenuMode.ADD },
                            )
                        }
                        if (onRemoveFromCollection != null) {
                            DropdownMenuItem(
                                text = { Text("移出集合") },
                                enabled = groups.isNotEmpty(),
                                onClick = { collectionMenuMode = CollectionMenuMode.REMOVE },
                            )
                        }
                        if (onClearCollections != null) {
                            DropdownMenuItem(
                                text = { Text("清空集合") },
                                onClick = {
                                    collectionMenuMode = null
                                    onClearCollections()
                                },
                            )
                        }
                    }
                    CollectionMenuMode.MOVE -> {
                        DropdownMenuItem(
                            text = { Text("返回集合操作") },
                            onClick = { collectionMenuMode = CollectionMenuMode.ROOT },
                        )
                        DropdownMenuItem(
                            text = { Text("未加入集合") },
                            onClick = {
                                collectionMenuMode = null
                                onMove(null)
                            },
                        )
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = {
                                    collectionMenuMode = null
                                    onMove(group.id)
                                },
                            )
                        }
                    }
                    CollectionMenuMode.ADD -> {
                        DropdownMenuItem(
                            text = { Text("返回集合操作") },
                            onClick = { collectionMenuMode = CollectionMenuMode.ROOT },
                        )
                        onAddToCollection?.let { addToCollection ->
                            groups.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text(group.name) },
                                    onClick = {
                                        collectionMenuMode = null
                                        addToCollection(group.id)
                                    },
                                )
                            }
                        }
                    }
                    CollectionMenuMode.REMOVE -> {
                        DropdownMenuItem(
                            text = { Text("返回集合操作") },
                            onClick = { collectionMenuMode = CollectionMenuMode.ROOT },
                        )
                        onRemoveFromCollection?.let { removeFromCollection ->
                            groups.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text(group.name) },
                                    onClick = {
                                        collectionMenuMode = null
                                        removeFromCollection(group.id)
                                    },
                                )
                            }
                        }
                    }
                    null -> Unit
                }
            }

            TextButton(
                onClick = { statusMenuExpanded = true },
                enabled = !actionInProgress,
            ) { Text("状态") }
            DropdownMenu(
                expanded = statusMenuExpanded,
                onDismissRequest = { statusMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("标记已读完") },
                    onClick = {
                        statusMenuExpanded = false
                        onMarkFinished(true)
                    },
                )
                DropdownMenuItem(
                    text = { Text("取消已读完") },
                    onClick = {
                        statusMenuExpanded = false
                        onMarkFinished(false)
                    },
                )
            }
            TextButton(onClick = onDelete, enabled = !actionInProgress) { Text("删除") }
            TextButton(onClick = onCancel, enabled = !actionInProgress) { Text("取消选择") }
        }
    }
}
