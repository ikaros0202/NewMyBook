package com.xinyue.reader.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.domain.model.ReaderThemePreset

internal sealed interface ReaderThemeDialogRequest {
    data object Create : ReaderThemeDialogRequest
    data class Copy(val source: ReaderThemePreset) : ReaderThemeDialogRequest
    data class Rename(val theme: ReaderThemePreset) : ReaderThemeDialogRequest
    data class Delete(val theme: ReaderThemePreset) : ReaderThemeDialogRequest
}

@Composable
internal fun ReaderThemeDialogHost(
    request: ReaderThemeDialogRequest?,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onCopy: (String, String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    when (request) {
        null -> Unit
        ReaderThemeDialogRequest.Create -> ReaderThemeNameDialog(
            title = stringResource(R.string.reader_theme_create_title),
            initialName = "",
            onDismiss = onDismiss,
            onConfirm = onCreate,
        )
        is ReaderThemeDialogRequest.Copy -> ReaderThemeNameDialog(
            title = stringResource(R.string.reader_theme_copy_title),
            initialName = stringResource(R.string.reader_theme_copy_default_name, request.source.name),
            onDismiss = onDismiss,
            onConfirm = { onCopy(request.source.id, it) },
        )
        is ReaderThemeDialogRequest.Rename -> ReaderThemeNameDialog(
            title = stringResource(R.string.reader_theme_rename_title),
            initialName = request.theme.name,
            onDismiss = onDismiss,
            onConfirm = { onRename(request.theme.id, it) },
        )
        is ReaderThemeDialogRequest.Delete -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.reader_theme_delete_title, request.theme.name)) },
            text = { Text(stringResource(R.string.reader_theme_delete_explanation)) },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(request.theme.id); onDismiss() },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("reader-theme-dialog-delete-confirm"),
                ) { Text(stringResource(R.string.reader_theme_delete)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.reader_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReaderThemeNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val normalized = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= ReaderThemeManager.MAX_THEME_NAME_LENGTH) name = it },
                    label = { Text(stringResource(R.string.reader_theme_name_label)) },
                    supportingText = {
                        Text(stringResource(R.string.reader_theme_name_count, name.length))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(normalized); onDismiss() },
                enabled = normalized.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.reader_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.reader_cancel))
            }
        },
    )
}
