package com.xinyue.reader.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xinyue.reader.core.text.DetectedChapter

/** Page numbers refer to the complete book under the current reader layout. */
@Composable
internal fun ReaderPageJumpDialog(
    pages: List<ReaderPage>?,
    anchorOffset: Int,
    chapters: List<DetectedChapter>,
    isLoading: Boolean,
    progress: Float,
    error: String?,
    onRetry: () -> Unit,
    onJump: (Int) -> Unit,
    onOpenDirectory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val total = pages?.size ?: 0
    val current = pages?.let { readerBookPageAtOffset(it, anchorOffset) } ?: 1
    var input by remember(pages, anchorOffset) { mutableStateOf(current.toString()) }
    val target = input.toIntOrNull()?.takeIf { it in 1..total }
    val canJump = !isLoading && error == null && target != null
    val selectedChapter = target?.let { pages?.getOrNull(it - 1) }?.let { page ->
        chapters.firstOrNull { it.startOffset in page.startOffset until page.endOffset }?.title
            ?: chapters.lastOrNull { it.startOffset <= page.startOffset }?.title ?: "正文"
    }
    val submit = { if (canJump) target?.let(onJump); Unit }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转位置") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TextButton(onClick = onOpenDirectory, modifier = Modifier.testTag("reader-jump-directory")) {
                    Text("目录选章")
                }
                HorizontalDivider()
                when {
                    isLoading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("正在计算全书页码 · ${(progress.coerceIn(0f, 1f) * 100).toInt()}%")
                    }
                    error != null -> {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("重新计算") }
                    }
                    total > 0 -> {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { value ->
                                if (value.length <= 9 && value.all { it in '0'..'9' }) input = value
                            },
                            modifier = Modifier.fillMaxWidth().testTag("reader-jump-page-input"),
                            label = { Text("目标页码（全书）") },
                            suffix = { Text("/ $total") },
                            singleLine = true,
                            isError = target == null && input.isNotEmpty(),
                            supportingText = {
                                Text(if (target == null) "请输入 1–$total 页" else "当前位置：第 $current 页")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { submit() }),
                        )
                        selectedChapter?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Text(
                            "页码随字号和版式变化",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> Text("当前没有可跳转的页面")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = canJump, modifier = Modifier.testTag("reader-jump-confirm")) {
                Text("跳转")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** One-based page containing an anchor, including the book-end anchor. */
internal fun readerBookPageAtOffset(pages: List<ReaderPage>, anchorOffset: Int): Int {
    var low = 0
    var high = pages.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (pages[middle].startOffset <= anchorOffset) low = middle + 1 else high = middle
    }
    return low.coerceIn(1, pages.size.coerceAtLeast(1))
}
