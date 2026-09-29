package com.xinyue.reader.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.xinyue.reader.core.domain.model.BookGroup

@Composable
internal fun GroupNameDialog(
    title: String,
    initialName: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val normalizedName = name.trim()
    val codePointCount = normalizedName.codePointCount(0, normalizedName.length)
    val error = when {
        normalizedName.isEmpty() -> null
        codePointCount > 40 -> "集合名称不能超过 40 个字符"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("library-group-name-dialog"),
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("集合名称") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { message -> ({ Text(message) }) },
                    modifier = Modifier.testTag("library-group-name-input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = normalizedName.isNotEmpty() && error == null,
                modifier = Modifier.testTag("library-group-name-save"),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun DeleteGroupDialog(
    group: BookGroup,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除集合“${group.name}”？") },
        text = { Text("删除集合后，集合内书籍将回到“未加入集合”，不会删除书籍。") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("library-group-delete-confirm"),
            ) { Text("删除集合") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
