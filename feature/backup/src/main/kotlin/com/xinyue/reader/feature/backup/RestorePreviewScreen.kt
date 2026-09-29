package com.xinyue.reader.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.RestoreMode
import com.xinyue.reader.core.domain.model.RestorePreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

@Composable
fun RestorePreviewScreen(
    preview: RestorePreview,
    selections: Map<String, RestoreConflictSelection>,
    mode: RestoreMode,
    canRestore: Boolean,
    onSelectionChanged: (String, RestoreConflictSelection) -> Unit,
    onModeChanged: (RestoreMode) -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("restore_preview"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("恢复预览", style = MaterialTheme.typography.headlineSmall)
            Text("备份时间：${formatTime(preview.createdAtEpochMillis)}")
            Text("格式版本：${preview.formatVersion}")
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("新增 ${preview.newBookCount} 本 · 重复 ${preview.duplicateBookCount} 本 · 冲突 ${preview.conflictCount} 项")
                    Text("暂存 ${formatBytes(preview.stagingBytes)} · 发布 ${formatBytes(preview.publishBytes)}")
                    Text("安全快照 ${formatBytes(preview.snapshotBytes)} · 至少需要 ${formatBytes(preview.requiredFreeBytes)} 可用空间")
                    Text(
                        "正文：${if (preview.options.includeBookText) "包含" else "未包含，不能独立恢复新书"}；" +
                            "字体：${if (preview.options.includeFonts) "包含" else "未包含"}",
                    )
                }
            }
        }
        item {
            Text("恢复方式", style = MaterialTheme.typography.titleMedium)
            RestoreModeRow(
                selected = mode == RestoreMode.MERGE,
                title = "安全合并（推荐）",
                consequence = "按正文哈希、新旧时间和下面的选择合并，不删除无关本机数据",
                onClick = { onModeChanged(RestoreMode.MERGE) },
            )
            RestoreModeRow(
                selected = mode == RestoreMode.OVERWRITE,
                title = "覆盖整个书架",
                consequence = if (preview.options.includeBookText) {
                    "先创建内部快照，再用备份替换全部用户数据；失败会回滚"
                } else {
                    "此备份未包含正文，不能用于覆盖整个书架"
                },
                enabled = preview.options.includeBookText,
                onClick = { onModeChanged(RestoreMode.OVERWRITE) },
            )
        }
        if (preview.conflicts.isNotEmpty()) {
            item { Text("逐项处理冲突", style = MaterialTheme.typography.titleMedium) }
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
                    onClick = onRestore,
                    enabled = canRestore,
                    modifier = Modifier.testTag("restore_confirm"),
                ) { Text(if (mode == RestoreMode.OVERWRITE) "继续覆盖" else "开始恢复") }
            }
        }
    }
}

@Composable
private fun RestoreModeRow(
    selected: Boolean,
    title: String,
    consequence: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = "$title，${if (selected) "已选择" else "未选择"}，$consequence" }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(consequence, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024) return "${kib.roundToLong()} KiB"
    return "${(kib / 1024.0 * 10).roundToLong() / 10.0} MiB"
}

private fun formatTime(epochMillis: Long): String = runCatching {
    TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}.getOrDefault("未知")

private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
