package com.xinyue.reader.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.RestoreConflict
import com.xinyue.reader.core.domain.model.RestoreConflictChoice
import com.xinyue.reader.core.domain.model.RestoreConflictResolution

data class RestoreConflictSelection(
    val choice: RestoreConflictChoice,
    val renamedValue: String = "",
)

fun defaultConflictSelections(conflicts: List<RestoreConflict>): Map<String, RestoreConflictSelection> =
    conflicts.associate { it.id to RestoreConflictSelection(it.suggestedChoice) }

fun conflictSelectionsComplete(
    conflicts: List<RestoreConflict>,
    selections: Map<String, RestoreConflictSelection>,
): Boolean = conflicts.all { conflict ->
    val selection = selections[conflict.id] ?: return@all false
    selection.choice in conflict.allowedChoices &&
        (selection.choice != RestoreConflictChoice.RENAME_BACKUP || selection.renamedValue.isNotBlank())
}

fun toRestoreResolutions(
    conflicts: List<RestoreConflict>,
    selections: Map<String, RestoreConflictSelection>,
): List<RestoreConflictResolution> {
    require(conflictSelectionsComplete(conflicts, selections)) { "恢复冲突尚未全部选择" }
    return conflicts.map { conflict ->
        val selection = selections.getValue(conflict.id)
        RestoreConflictResolution(
            conflictId = conflict.id,
            choice = selection.choice,
            renamedValue = selection.renamedValue.trim().takeIf {
                selection.choice == RestoreConflictChoice.RENAME_BACKUP
            },
        )
    }
}

@Composable
fun RestoreConflictControl(
    conflict: RestoreConflict,
    selection: RestoreConflictSelection,
    onSelectionChanged: (RestoreConflictSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(conflict.id) { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${conflict.label}，当前选择${selection.choice.label()}，${selection.choice.consequence()}"
            }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(conflict.label, style = MaterialTheme.typography.titleSmall)
        Text(conflict.kind.label(), style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(selection.choice.consequence(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { expanded = true }) { Text(selection.choice.label()) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                conflict.allowedChoices.sortedBy { it.ordinal }.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text("${choice.label()}：${choice.consequence()}") },
                        onClick = {
                            expanded = false
                            onSelectionChanged(selection.copy(choice = choice))
                        },
                    )
                }
            }
        }
        if (selection.choice == RestoreConflictChoice.RENAME_BACKUP) {
            OutlinedTextField(
                value = selection.renamedValue,
                onValueChange = { onSelectionChanged(selection.copy(renamedValue = it)) },
                label = { Text("备份副本名称") },
                supportingText = { Text("以新名称保留备份项，不覆盖本机同名项") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal fun RestoreConflictChoice.label(): String = when (this) {
    RestoreConflictChoice.KEEP_LOCAL -> "保留本机"
    RestoreConflictChoice.USE_BACKUP -> "使用备份"
    RestoreConflictChoice.RENAME_BACKUP -> "重命名备份"
    RestoreConflictChoice.COPY_AS_NEW -> "作为副本"
    RestoreConflictChoice.KEEP_BOTH -> "保留两份"
}

internal fun RestoreConflictChoice.consequence(): String = when (this) {
    RestoreConflictChoice.KEEP_LOCAL -> "本机数据不变，跳过相应备份值"
    RestoreConflictChoice.USE_BACKUP -> "用备份值替换对应本机值"
    RestoreConflictChoice.RENAME_BACKUP -> "保留本机项并新增一个改名后的备份项"
    RestoreConflictChoice.COPY_AS_NEW -> "保留本机项并把备份内容恢复为新副本"
    RestoreConflictChoice.KEEP_BOTH -> "两个版本都保留，冲突项使用新标识"
}

private fun com.xinyue.reader.core.domain.model.RestoreConflictKind.label(): String = when (this) {
    com.xinyue.reader.core.domain.model.RestoreConflictKind.BOOK_ID -> "书籍标识冲突"
    com.xinyue.reader.core.domain.model.RestoreConflictKind.PROGRESS -> "阅读进度冲突"
    com.xinyue.reader.core.domain.model.RestoreConflictKind.ANNOTATION -> "标注冲突"
    com.xinyue.reader.core.domain.model.RestoreConflictKind.GROUP -> "分组冲突"
    com.xinyue.reader.core.domain.model.RestoreConflictKind.THEME -> "主题冲突"
    com.xinyue.reader.core.domain.model.RestoreConflictKind.FONT -> "字体冲突"
    com.xinyue.reader.core.domain.model.RestoreConflictKind.GLOBAL_SETTINGS -> "全局设置冲突"
    com.xinyue.reader.core.domain.model.RestoreConflictKind.BOOK_SETTINGS -> "单书设置冲突"
    com.xinyue.reader.core.domain.model.RestoreConflictKind.STATISTICS -> "阅读统计冲突"
}
