package com.xinyue.reader.feature.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
            Column {
                Text(
                    "备份文件未加密，可能包含小说原文、私人批注、封面、字体、阅读设置、进度与统计。请只保存到可信位置。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                BackupOptionRow(
                    checked = options.includeBookText,
                    title = "包含小说原文",
                    consequence = if (options.includeBookText) {
                        "备份可独立恢复书籍正文"
                    } else {
                        "关闭后，新书正文不能从此备份独立恢复；已有同内容书籍仍可合并元数据"
                    },
                    onCheckedChange = onIncludeBookTextChanged,
                )
                BackupOptionRow(
                    checked = options.includeFonts,
                    title = "包含导入字体",
                    consequence = if (options.includeFonts) {
                        "备份可恢复导入字体及其设置引用"
                    } else {
                        "关闭后，导入字体不能独立恢复，相关设置将需要系统字体回退"
                    },
                    onCheckedChange = onIncludeFontsChanged,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
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
            .padding(top = 14.dp)
            .semantics { contentDescription = "$title，${if (checked) "已选" else "未选"}，$consequence" },
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(consequence, style = MaterialTheme.typography.bodySmall)
        }
    }
}
