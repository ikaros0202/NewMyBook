package com.xinyue.reader.feature.backup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.data.BackupWorkStatus
import com.xinyue.reader.core.data.HandoffWorkType
import com.xinyue.reader.core.domain.model.HandoffBookSummary
import com.xinyue.reader.core.domain.model.HandoffPreview

@Composable
internal fun HandoffExportDialog(
    books: List<HandoffBookSummary>,
    selectedBookId: String?,
    includeBookText: Boolean,
    onBookSelected: (String) -> Unit,
    onIncludeBookTextChanged: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag("handoff_export_dialog"),
        onDismissRequest = onDismiss,
        title = { Text("导出单书阅读接力包") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("将这本书的阅读进度、笔记和设置带到另一台设备。接力包未加密，请妥善保存。")
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(books, key = { it.id }) { book ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = selectedBookId == book.id, role = Role.RadioButton, onClick = { onBookSelected(book.id) }),
                        ) {
                            RadioButton(
                                selected = selectedBookId == book.id,
                                onClick = null,
                            )
                            Column(Modifier.padding(top = 8.dp)) {
                                Text(book.title, style = MaterialTheme.typography.titleSmall)
                                val detail = listOfNotNull(book.author, book.seriesName).joinToString(" · ")
                                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = includeBookText, role = Role.Checkbox, onValueChange = onIncludeBookTextChanged),
                ) {
                    Checkbox(checked = includeBookText, onCheckedChange = null)
                    Text("包含小说正文", modifier = Modifier.padding(top = 12.dp))
                }
                Text(
                    if (includeBookText) {
                        "包含正文后，可在另一台设备直接导入这本书。"
                    } else {
                        "不包含正文时，另一台设备须先导入内容完全相同的 TXT。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(shape = MaterialTheme.shapes.medium, onClick = onConfirm, enabled = selectedBookId != null) { Text("选择保存位置") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun HandoffPreviewScreen(
    preview: HandoffPreview,
    selections: Map<String, RestoreConflictSelection>,
    canImport: Boolean,
    onSelectionChanged: (String, RestoreConflictSelection) -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("handoff_preview"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("阅读接力预览", style = MaterialTheme.typography.headlineSmall)
            Text(preview.title, style = MaterialTheme.typography.titleMedium)
            Text(if (preview.includesBookText) "接力包包含正文" else "已找到本机相同的书籍，接力包不含正文")
        }
        item {
            Text(
                when {
                    preview.importsNewBook -> "将导入为一本新书"
                    preview.incomingProgressIsNewer -> "将优先采用接力包中较新的阅读进度"
                    else -> "将保留较新的本机进度"
                },
            )
            Text(
                "新增标注 ${preview.annotationInsertCount} 条 · 冲突副本 " +
                    "${preview.annotationConflictCopyCount} 条 · 集合 ${preview.collectionCount} 个",
            )
            Text("同一接力包可重复应用；已合并的标注不会再次创建副本。")
        }
        if (preview.conflicts.isNotEmpty()) {
            item { Text("冲突处理", style = MaterialTheme.typography.titleMedium) }
            items(preview.conflicts, key = { it.id }) { conflict ->
                RestoreConflictControl(
                    conflict = conflict,
                    selection = selections.getValue(conflict.id),
                    onSelectionChanged = { onSelectionChanged(conflict.id, it) },
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("取消") }
                Button(shape = MaterialTheme.shapes.medium,
                    onClick = onImport,
                    enabled = canImport,
                    modifier = Modifier.testTag("handoff_import_confirm"),
                ) { Text(if (preview.importsNewBook) "导入并应用" else "应用接力状态") }
            }
        }
    }
}

@Composable
internal fun HandoffResultContent(
    state: BackupUiState,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val work = state.activeHandoffWork
    val succeeded = work?.status == BackupWorkStatus.SUCCEEDED
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).testTag("handoff_result"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (succeeded) "接力操作完成" else "接力操作未完成", style = MaterialTheme.typography.headlineSmall)
        when {
            succeeded && work.type == HandoffWorkType.EXPORT ->
                Text("接力包已完整写入并通过本地校验。")
            succeeded && work.type == HandoffWorkType.IMPORT -> {
                Text(
                    if (work.importedNewBook) "已导入新书并应用阅读状态。"
                    else "已将进度和笔记合并到本机的同一本书。",
                )
                Text("应用 ${work.appliedCount} 项 · 跳过 ${work.skippedCount} 项 · 冲突副本 ${work.conflictCopyCount} 项")
            }
            else -> Text(state.handoffMessage ?: "现有书架数据未受影响，可以返回后重试。")
        }
        Button(shape = MaterialTheme.shapes.medium, onClick = onReset) { Text("返回备份与恢复") }
    }
}
