package com.xinyue.reader.feature.backup

import androidx.compose.foundation.clickable
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("接力包未加密，包含进度、完成状态、书签、高亮、批注、单书设置、系列和集合信息。")
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(books, key = { it.id }) { book ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onBookSelected(book.id) },
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
                        .clickable { onIncludeBookTextChanged(!includeBookText) },
                ) {
                    Checkbox(checked = includeBookText, onCheckedChange = null)
                    Text("包含小说正文", modifier = Modifier.padding(top = 12.dp))
                }
                Text(
                    if (includeBookText) {
                        "包含正文会让接力包可在目标设备导入新书，也会携带完整私人小说内容，请只保存到可信位置。"
                    } else {
                        "默认不含正文；目标设备必须已有内容摘要完全一致的书籍，否则会拒绝应用。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = selectedBookId != null) { Text("选择保存位置") }
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
            Text(if (preview.includesBookText) "接力包包含正文" else "接力包不含正文，已完成精确内容摘要匹配")
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
                Button(
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
                    else "已把阅读状态安全合并到摘要一致的本机书籍。",
                )
                Text("应用 ${work.appliedCount} 项 · 跳过 ${work.skippedCount} 项 · 冲突副本 ${work.conflictCopyCount} 项")
            }
            else -> Text(state.handoffMessage ?: "现有书架数据未受影响，可以返回后重试。")
        }
        Button(onClick = onReset) { Text("返回本地数据") }
    }
}
