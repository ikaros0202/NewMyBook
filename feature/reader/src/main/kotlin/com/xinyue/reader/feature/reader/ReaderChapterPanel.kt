package com.xinyue.reader.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.text.ChapterRuleSet
import com.xinyue.reader.core.text.DetectedChapter

@Composable
internal fun ReaderChapterPanel(
    chapters: List<DetectedChapter>,
    ruleSet: ChapterRuleSet,
    manuallyEdited: Boolean,
    isAnalyzing: Boolean,
    onSelect: (DetectedChapter) -> Unit,
    onReanalyze: (ChapterRuleSet) -> Unit,
    onAddAtCurrent: (String) -> Unit,
    onRename: (Int, String) -> Unit,
    onMoveToCurrent: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedRule by remember { mutableStateOf(ruleSet) }
    var addTitle by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<DetectedChapter?>(null) }
    var renameTitle by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<DetectedChapter?>(null) }
    var confirmReanalysis by remember { mutableStateOf(false) }
    LaunchedEffect(ruleSet) { selectedRule = ruleSet }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("章节目录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("识别规则", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChapterRuleSet.entries.forEach { rule ->
                        TextButton(
                            onClick = { selectedRule = rule },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        ) {
                            Text(if (rule == selectedRule) "✓ ${rule.label}" else rule.label)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (manuallyEdited) "当前目录含人工修改" else "当前目录由规则生成",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = { confirmReanalysis = true },
                        enabled = !isAnalyzing,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        if (isAnalyzing) CircularProgressIndicator() else Text("重新分析")
                    }
                }
                OutlinedTextField(
                    value = addTitle,
                    onValueChange = { addTitle = it },
                    label = { Text("新章节名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onAddAtCurrent(addTitle)
                        if (addTitle.isNotBlank()) addTitle = ""
                    },
                    enabled = addTitle.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                ) { Text("在当前页添加章节") }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(chapters, key = { it.startOffset }) { chapter ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = chapter.title,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onSelect(chapter) }
                                    .defaultMinSize(minHeight = 48.dp)
                                    .padding(vertical = 12.dp),
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(
                                    onClick = {
                                        renameTarget = chapter
                                        renameTitle = chapter.title
                                    },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                        .semantics { contentDescription = "重命名${chapter.title}" },
                                ) { Text("重命名") }
                                TextButton(
                                    onClick = { onMoveToCurrent(chapter.startOffset) },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                        .semantics { contentDescription = "移动${chapter.title}到当前页" },
                                ) { Text("移到当前页") }
                                TextButton(
                                    onClick = { deleteTarget = chapter },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                        .semantics { contentDescription = "删除${chapter.title}" },
                                ) { Text("删除") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )

    renameTarget?.let { chapter ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名章节") },
            text = {
                OutlinedTextField(
                    value = renameTitle,
                    onValueChange = { renameTitle = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(chapter.startOffset, renameTitle)
                        renameTarget = null
                    },
                    enabled = renameTitle.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }
    deleteTarget?.let { chapter ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除章节？") },
            text = { Text("只删除目录节点“${chapter.title}”，不会修改小说正文。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(chapter.startOffset)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
    if (confirmReanalysis) {
        AlertDialog(
            onDismissRequest = { confirmReanalysis = false },
            title = { Text("重新分析目录？") },
            text = {
                Text("将按“${selectedRule.label}”重建目录，并替换现有人工修改。小说正文不会改变。")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReanalysis = false
                    onReanalyze(selectedRule)
                }) { Text("重新分析") }
            },
            dismissButton = { TextButton(onClick = { confirmReanalysis = false }) { Text("取消") } },
        )
    }
}

private val ChapterRuleSet.label: String
    get() = when (this) {
        ChapterRuleSet.STANDARD -> "标准"
        ChapterRuleSet.BROAD -> "宽松"
        ChapterRuleSet.NUMBERED -> "含数字标题"
    }
