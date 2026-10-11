package com.xinyue.reader.feature.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.BackupOptions

@Composable
fun BackupPrivacyDialog(
    options: BackupOptions,
    onIncludeBookTextChanged: (Boolean) -> Unit,
    onIncludeFontsChanged: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出未加密备份") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "备份包含书籍信息、笔记、进度和设置，未加密。请保存到可信位置。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                BackupOptionRow(
                    checked = options.includeBookText,
                    title = "包含小说原文",
                    consequence = if (options.includeBookText) {
                        "备份可独立恢复书籍正文"
                    } else {
                        "关闭后，仅能更新本机已有的相同书籍，不能恢复新书正文"
                    },
                    onCheckedChange = onIncludeBookTextChanged,
                )
                BackupOptionRow(
                    checked = options.includeFonts,
                    title = "包含导入字体",
                    consequence = if (options.includeFonts) {
                        "在另一台设备上保留相同字体"
                    } else {
                        "关闭后，缺失的字体将使用系统字体"
                    },
                    onCheckedChange = onIncludeFontsChanged,
                )
            }
        },
        confirmButton = {
            Button(shape = MaterialTheme.shapes.medium, onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("选择保存位置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun BackupOptionRow(
    checked: Boolean,
    title: String,
    consequence: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(top = 14.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics { contentDescription = "$title，${if (checked) "已选" else "未选"}，$consequence" },
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(consequence, style = MaterialTheme.typography.bodySmall)
        }
    }
}
