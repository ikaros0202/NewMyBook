package com.xinyue.reader.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.AnnotationExportFormat
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.ReaderAnnotation

@Composable
internal fun ReaderAnnotationPanel(
    annotations: List<ReaderAnnotation>,
    onSelect: (ReaderAnnotation) -> Unit,
    onEditNote: (ReaderAnnotation, String) -> Unit,
    onDelete: (ReaderAnnotation) -> Unit,
    onDismiss: () -> Unit,
    onExport: (AnnotationExportFormat, Boolean) -> Unit = { _, _ -> },
    isExporting: Boolean = false,
    exportMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<ReaderAnnotation?>(null) }
    var deleting by remember { mutableStateOf<ReaderAnnotation?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val visible = readerNotes(annotations)

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text("笔记") },
        text = {
            Column {
                exportMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (visible.isEmpty()) {
                    Text(
                        "当前书籍还没有笔记。长按选择正文后，可以添加批注。",
                        modifier = Modifier.padding(top = 18.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                        items(visible, key = ReaderAnnotation::id) { annotation ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onSelect(annotation) }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(
                                    text = "${annotation.kind.displayName()} · 位置 ${annotation.range.startOffset}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                annotation.note?.let { Text(it, maxLines = 3) }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(onClick = { editing = annotation }) { Text("编辑") }
                                    TextButton(onClick = { deleting = annotation }) { Text("删除") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = {
            TextButton(
                onClick = { exporting = true },
                enabled = !isExporting,
                modifier = Modifier.testTag("reader-annotation-export"),
            ) { Text(if (isExporting) "正在导出…" else "导出批注") }
        },
    )

    if (exporting) {
        var includeBookmarks by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { exporting = false },
            title = { Text("导出当前书批注") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("导出文件仅保存到你通过系统文件选择器指定的位置。")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { includeBookmarks = !includeBookmarks },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = includeBookmarks, onCheckedChange = null)
                        Text("包含书签")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        exporting = false
                        onExport(AnnotationExportFormat.MARKDOWN, includeBookmarks)
                    },
                ) { Text("导出 Markdown") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            exporting = false
                            onExport(AnnotationExportFormat.JSON, includeBookmarks)
                        },
                    ) { Text("导出 JSON") }
                    TextButton(onClick = { exporting = false }) { Text("取消") }
                }
            },
        )
    }

    editing?.let { annotation ->
        var note by remember(annotation.id) { mutableStateOf(annotation.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("编辑笔记") },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("批注内容") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEditNote(annotation, note)
                        editing = null
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
    }

    deleting?.let { annotation ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除笔记？") },
            text = { Text("删除后无法撤销，原始 TXT 不会被修改。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(annotation)
                        deleting = null
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

internal fun readerNotes(annotations: List<ReaderAnnotation>): List<ReaderAnnotation> =
    annotations.filter { annotation ->
        annotation.kind == AnnotationKind.NOTE ||
            (annotation.kind != AnnotationKind.BOOKMARK && !annotation.note.isNullOrBlank())
    }.sortedBy { it.range.startOffset }

private fun AnnotationKind.displayName(): String = when (this) {
    AnnotationKind.BOOKMARK -> "书签"
    AnnotationKind.HIGHLIGHT -> "高亮笔记"
    AnnotationKind.NOTE -> "批注"
}
