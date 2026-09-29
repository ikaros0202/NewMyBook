package com.xinyue.reader.feature.importing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xinyue.reader.core.data.DuplicateResolution
import com.xinyue.reader.core.data.ImportItemState
import com.xinyue.reader.core.data.ImportItemStatus
import com.xinyue.reader.core.data.ImportPreflightItem
import com.xinyue.reader.core.domain.model.Book

@Composable
fun ImportRoute(
    onBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCharsetDialog by remember { mutableStateOf(false) }
    var selectedCharset by remember { mutableStateOf<String?>(null) }
    val automaticLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.prepareImport(uris.map { it.toString() })
    }
    val manualLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.enqueue(uris.map { it.toString() }, selectedCharset)
        selectedCharset = null
    }

    ImportScreen(
        uiState = uiState,
        onBack = onBack,
        onSelectFiles = { automaticLauncher.launch(arrayOf("text/plain")) },
        onSelectKnownEncoding = { showCharsetDialog = true },
        onResolveDuplicate = viewModel::resolveDuplicate,
        onRetry = viewModel::retry,
        onEditImportedBook = viewModel::editImportedBook,
        onDismissError = viewModel::dismissError,
    )

    if (showCharsetDialog) {
        CharsetDialog(
            onSelect = { charset ->
                selectedCharset = charset
                showCharsetDialog = false
                manualLauncher.launch(arrayOf("text/plain"))
            },
            onDismiss = { showCharsetDialog = false },
        )
    }
    uiState.encodingDecision?.let { item ->
        EncodingPreviewDialog(
            item = item,
            onConfirm = { charsetName -> viewModel.confirmEncoding(item, charsetName) },
            onDismiss = viewModel::cancelEncodingReview,
        )
    }
    uiState.editingBook?.let { book ->
        ImportedBookMetadataDialog(
            book = book,
            isSaving = uiState.isSavingMetadata,
            onConfirm = viewModel::saveImportedBookMetadata,
            onDismiss = viewModel::dismissBookMetadataEditor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    uiState: ImportUiState,
    onBack: () -> Unit,
    onSelectFiles: () -> Unit,
    onSelectKnownEncoding: () -> Unit,
    onResolveDuplicate: (ImportItemState, DuplicateResolution) -> Unit,
    onRetry: (ImportItemState) -> Unit,
    onEditImportedBook: (ImportItemState) -> Unit,
    onDismissError: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onDismissError()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入 TXT") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "可一次选择多本小说。导入会在后台继续，离开本页后进度不会丢失。",
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Column {
                    Button(
                        onClick = onSelectFiles,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.testTag("import_select_files"),
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        }
                        Text(if (uiState.batch == null) "选择 TXT 文件" else "继续选择文件")
                    }
                    TextButton(onClick = onSelectKnownEncoding, enabled = !uiState.isSubmitting) {
                        Text("已知文件编码，直接指定")
                    }
                }
            }
            if (uiState.preflightFailures.isNotEmpty()) {
                item { Text("未加入导入队列", style = MaterialTheme.typography.titleMedium) }
                items(uiState.preflightFailures, key = ImportPreflightItem::uriString) { failed ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(failed.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                failed.errorMessage ?: "文件检查失败",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
            uiState.batch?.let { batch ->
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("本批次 ${batch.completedCount}/${batch.items.size}")
                        LinearProgressIndicator(
                            progress = { if (batch.items.isEmpty()) 0f else batch.completedCount.toFloat() / batch.items.size },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
                items(batch.items, key = ImportItemState::workId) { item ->
                    ImportItemCard(item, onResolveDuplicate, onRetry, onEditImportedBook)
                }
            }
            item { Column(Modifier.padding(bottom = 24.dp)) {} }
        }
    }
}

@Composable
private fun ImportItemCard(
    item: ImportItemState,
    onResolveDuplicate: (ImportItemState, DuplicateResolution) -> Unit,
    onRetry: (ImportItemState) -> Unit,
    onEditImportedBook: (ImportItemState) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(item.displayName, style = MaterialTheme.typography.titleMedium)
            Text(statusLabel(item), modifier = Modifier.padding(top = 6.dp))
            if (item.status == ImportItemStatus.RUNNING || item.status == ImportItemStatus.QUEUED) {
                LinearProgressIndicator(
                    progress = { item.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
            item.errorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            when (item.status) {
                ImportItemStatus.SUCCEEDED -> TextButton(
                    onClick = { onEditImportedBook(item) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("编辑书名和作者") }
                ImportItemStatus.NEEDS_DECISION -> Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("已存在：${item.existingBookTitle.orEmpty()}")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onResolveDuplicate(item, DuplicateResolution.SKIP) }) { Text("跳过") }
                        TextButton(onClick = { onResolveDuplicate(item, DuplicateResolution.REPLACE) }) { Text("替换") }
                        TextButton(onClick = { onResolveDuplicate(item, DuplicateResolution.COPY) }) { Text("作为副本") }
                    }
                }
                ImportItemStatus.FAILED,
                ImportItemStatus.CANCELLED,
                -> TextButton(onClick = { onRetry(item) }, modifier = Modifier.align(Alignment.End)) {
                    Text("重试此项")
                }
                else -> Unit
            }
        }
    }
}

private fun statusLabel(item: ImportItemState): String = when (item.status) {
    ImportItemStatus.QUEUED -> "等待导入"
    ImportItemStatus.RUNNING -> "正在导入 ${item.progressPercent}%"
    ImportItemStatus.SUCCEEDED -> "导入成功"
    ImportItemStatus.SKIPPED -> "已跳过重复内容"
    ImportItemStatus.NEEDS_DECISION -> "需要处理重复内容"
    ImportItemStatus.FAILED -> "导入失败"
    ImportItemStatus.CANCELLED -> "任务已取消"
}

@Composable
private fun CharsetDialog(onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        "UTF-8" to "UTF-8",
        "简体中文 GB18030" to "GB18030",
        "繁体中文 Big5" to "Big5",
        "UTF-16 小端" to "UTF-16LE",
        "UTF-16 大端" to "UTF-16BE",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文本编码") },
        text = {
            Column {
                Text("仅在你确定原文件编码时使用。通常直接选择文件，让新阅自动检查。")
                options.forEach { (label, charset) ->
                    TextButton(onClick = { onSelect(charset) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EncodingPreviewDialog(
    item: ImportPreflightItem,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val analysis = requireNotNull(item.encoding)
    var selectedCharset by remember(item.uriString) {
        mutableStateOf(analysis.recommendedCharsetName)
    }
    val selectedCandidate = analysis.candidates.first { it.charsetName == selectedCharset }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认《${item.displayName}》的编码") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(analysis.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                analysis.candidates.forEach { candidate ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = candidate.charsetName == selectedCharset,
                            onClick = { selectedCharset = candidate.charsetName },
                        )
                        Text(charsetLabel(candidate.charsetName))
                    }
                }
                PreviewSection("开头", selectedCandidate.preview.beginning)
                PreviewSection("中段", selectedCandidate.preview.middle)
                PreviewSection("结尾", selectedCandidate.preview.end)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedCharset) }) { Text("按此编码导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消本次导入") } },
    )
}

@Composable
private fun PreviewSection(label: String, text: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            text.ifBlank { "（此段没有可显示文字）" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun charsetLabel(charsetName: String): String = when (charsetName) {
    "UTF-8" -> "UTF-8"
    "GB18030" -> "简体中文 GB18030"
    "Big5" -> "繁体中文 Big5"
    "UTF-16LE" -> "UTF-16 小端"
    "UTF-16BE" -> "UTF-16 大端"
    else -> charsetName
}

@Composable
private fun ImportedBookMetadataDialog(
    book: Book,
    isSaving: Boolean,
    onConfirm: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author.orEmpty()) }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("编辑导入结果") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("书名") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("作者（可选）") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, author.takeIf(String::isNotBlank)) },
                enabled = title.isNotBlank() && !isSaving,
            ) { Text(if (isSaving) "保存中…" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("取消") }
        },
    )
}
