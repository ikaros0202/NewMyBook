package com.xinyue.reader.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xinyue.reader.core.data.BackupWorkStatus
import com.xinyue.reader.core.data.BackupWorkType
import com.xinyue.reader.core.domain.model.AnnotationExportFormat
import com.xinyue.reader.core.domain.model.AnnotationExportRequest
import com.xinyue.reader.core.domain.model.HandoffExportRequest
import com.xinyue.reader.core.ui.XinYueIcons
import com.xinyue.reader.core.ui.AnnotationExportRequestSaver
import com.xinyue.reader.core.ui.HandoffExportRequestSaver
import kotlinx.coroutines.flow.collectLatest

@Composable
fun BackupRoute(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        viewModel.onExportDocumentChosen(uri?.toString())
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onRestoreDocumentChosen(uri?.toString())
    }
    val openHandoffDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onHandoffDocumentChosen(uri?.toString())
    }
    var pendingHandoffRequest by rememberSaveable(stateSaver = HandoffExportRequestSaver) { mutableStateOf<HandoffExportRequest?>(null) }
    val createHandoffDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val request = pendingHandoffRequest
        pendingHandoffRequest = null
        if (request != null) viewModel.onHandoffExportDocumentChosen(uri?.toString(), request)
    }
    var pendingAnnotationRequest by rememberSaveable(stateSaver = AnnotationExportRequestSaver) { mutableStateOf<AnnotationExportRequest?>(null) }
    val createMarkdown = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        val request = pendingAnnotationRequest
        pendingAnnotationRequest = null
        if (request != null) viewModel.onAnnotationExportDocumentChosen(uri?.toString(), request)
    }
    val createJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val request = pendingAnnotationRequest
        pendingAnnotationRequest = null
        if (request != null) viewModel.onAnnotationExportDocumentChosen(uri?.toString(), request)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is BackupUiEvent.CreateBackupDocument -> createDocument.launch(event.suggestedName)
                is BackupUiEvent.CreateAnnotationExportDocument -> {
                    pendingAnnotationRequest = event.request
                    when (event.request.format) {
                        AnnotationExportFormat.MARKDOWN -> createMarkdown.launch(event.suggestedName)
                        AnnotationExportFormat.JSON -> createJson.launch(event.suggestedName)
                    }
                }
                BackupUiEvent.OpenBackupDocument -> openDocument.launch(
                    arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"),
                )
                is BackupUiEvent.CreateHandoffDocument -> {
                    pendingHandoffRequest = event.request
                    createHandoffDocument.launch(event.suggestedName)
                }
                BackupUiEvent.OpenHandoffDocument -> openHandoffDocument.launch(
                    arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"),
                )
            }
        }
    }
    BackupScreen(
        state = state,
        onBack = onBack,
        onRequestExport = viewModel::requestExport,
        onRequestRestore = viewModel::requestRestore,
        onRequestAnnotationExport = viewModel::requestAnnotationExport,
        onRequestHandoffExport = viewModel::requestHandoffExport,
        onRequestHandoffImport = viewModel::requestHandoffImport,
        onCancel = viewModel::cancelActiveOperation,
        onReset = viewModel::reset,
        onDismissError = viewModel::dismissError,
        onConflictChanged = viewModel::updateConflictSelection,
        onModeChanged = viewModel::selectRestoreMode,
        onConfirmRestore = viewModel::requestConfirmedRestore,
        onHandoffConflictChanged = viewModel::updateHandoffConflictSelection,
        onConfirmHandoffImport = viewModel::confirmHandoffImport,
    )
    if (state.showPrivacyDialog) {
        BackupPrivacyDialog(
            options = state.options,
            onIncludeBookTextChanged = viewModel::setIncludeBookText,
            onIncludeFontsChanged = viewModel::setIncludeFonts,
            onConfirm = viewModel::confirmExportOptions,
            onDismiss = viewModel::dismissPrivacyDialog,
        )
    }
    if (state.showOverwriteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissOverwriteConfirmation,
            title = { Text("确认覆盖整个本机书架？") },
            text = { Text("当前书架、进度、笔记和设置将被备份替换。应用会临时保存当前数据；恢复失败时自动还原。") },
            confirmButton = { Button(shape = MaterialTheme.shapes.medium, onClick = viewModel::confirmOverwriteRestore) { Text("确认覆盖") } },
            dismissButton = { TextButton(onClick = viewModel::dismissOverwriteConfirmation) { Text("返回") } },
        )
    }
    if (state.showAnnotationExportDialog) {
        AnnotationExportDialog(
            title = "导出全部书籍批注",
            onChoose = viewModel::chooseAnnotationExport,
            onDismiss = viewModel::dismissAnnotationExport,
        )
    }
    if (state.showHandoffExportDialog) {
        HandoffExportDialog(
            books = state.handoffBooks,
            selectedBookId = state.selectedHandoffBookId,
            includeBookText = state.handoffIncludeBookText,
            onBookSelected = viewModel::selectHandoffBook,
            onIncludeBookTextChanged = viewModel::setHandoffIncludeBookText,
            onConfirm = viewModel::confirmHandoffExport,
            onDismiss = viewModel::dismissHandoffExport,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    state: BackupUiState,
    onBack: () -> Unit,
    onRequestExport: () -> Unit,
    onRequestRestore: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onDismissError: () -> Unit,
    onConflictChanged: (String, RestoreConflictSelection) -> Unit,
    onModeChanged: (com.xinyue.reader.core.domain.model.RestoreMode) -> Unit,
    onConfirmRestore: () -> Unit,
    onRequestAnnotationExport: () -> Unit = {},
    onRequestHandoffExport: () -> Unit = {},
    onRequestHandoffImport: () -> Unit = {},
    onHandoffConflictChanged: (String, RestoreConflictSelection) -> Unit = { _, _ -> },
    onConfirmHandoffImport: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        onDismissError()
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("backup_root"),
        topBar = {
            TopAppBar(
                title = { Text("备份与恢复") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(XinYueIcons.ArrowBack), contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (state.stage) {
            BackupUiStage.HOME -> BackupHome(
                padding = padding,
                onRequestExport = onRequestExport,
                onRequestRestore = onRequestRestore,
                onRequestAnnotationExport = onRequestAnnotationExport,
                onRequestHandoffExport = onRequestHandoffExport,
                onRequestHandoffImport = onRequestHandoffImport,
                annotationExportInProgress = state.annotationExportInProgress,
                annotationExportMessage = state.annotationExportMessage,
            )
            BackupUiStage.INSPECTING -> ProgressContent(
                padding,
                state.inspectProgress?.label ?: "正在安全检查备份",
                state.inspectProgress?.completed ?: 0,
                state.inspectProgress?.total ?: 0,
                true,
                onCancel,
            )
            BackupUiStage.PREVIEW -> state.preview?.let { preview ->
                RestorePreviewScreen(
                    preview = preview,
                    selections = state.conflictSelections,
                    mode = state.restoreMode,
                    canRestore = state.canStartRestore,
                    onSelectionChanged = onConflictChanged,
                    onModeChanged = onModeChanged,
                    onRestore = onConfirmRestore,
                    onCancel = onReset,
                    modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
                )
            }
            BackupUiStage.WORKING -> {
                val work = state.activeWork
                val safeFinishing = work?.phase in setOf(
                    com.xinyue.reader.core.domain.model.BackupPhase.RESTORING,
                    com.xinyue.reader.core.domain.model.BackupPhase.VERIFYING,
                )
                ProgressContent(
                    padding,
                    if (safeFinishing) "正在安全完成或回滚" else work?.phase?.displayLabel().orEmpty().ifBlank { "后台任务已排队" },
                    work?.completed ?: 0,
                    work?.total ?: 0,
                    state.canCancel,
                    onCancel,
                )
            }
            BackupUiStage.RESULT -> ResultContent(padding, state, onReset)
            BackupUiStage.HANDOFF_INSPECTING -> ProgressContent(
                padding,
                state.handoffProgress?.label ?: "正在安全检查接力包",
                state.handoffProgress?.completed ?: 0,
                state.handoffProgress?.total ?: 0,
                true,
                onCancel,
            )
            BackupUiStage.HANDOFF_PREVIEW -> state.handoffPreview?.let { preview ->
                HandoffPreviewScreen(
                    preview = preview,
                    selections = state.handoffConflictSelections,
                    canImport = state.canStartHandoffImport,
                    onSelectionChanged = onHandoffConflictChanged,
                    onImport = onConfirmHandoffImport,
                    onCancel = onReset,
                    modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
                )
            }
            BackupUiStage.HANDOFF_WORKING -> {
                val work = state.activeHandoffWork
                val safeFinishing = work?.phase in setOf(
                    com.xinyue.reader.core.domain.model.BackupPhase.RESTORING,
                    com.xinyue.reader.core.domain.model.BackupPhase.VERIFYING,
                )
                ProgressContent(
                    padding,
                    if (safeFinishing) "正在安全完成或回滚"
                    else work?.phase?.displayLabel().orEmpty().ifBlank { "接力任务已排队" },
                    work?.completed ?: 0,
                    work?.total ?: 0,
                    state.canCancel,
                    onCancel,
                )
            }
            BackupUiStage.HANDOFF_RESULT -> HandoffResultContent(
                state = state,
                onReset = onReset,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun BackupHome(
    padding: PaddingValues,
    onRequestExport: () -> Unit,
    onRequestRestore: () -> Unit,
    onRequestAnnotationExport: () -> Unit,
    annotationExportInProgress: Boolean,
    annotationExportMessage: String?,
    onRequestHandoffExport: () -> Unit,
    onRequestHandoffImport: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("backup_home_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                "备份保存在你选择的位置，恢复前可先查看内容。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp),
            )
            Text("整个书架", style = MaterialTheme.typography.titleMedium)
        }
        item {
            BackupActionRow("导出备份", "保存书籍、阅读进度和设置", "开始导出", onRequestExport)
        }
        item {
            BackupActionRow("从备份恢复", "选择备份，预览后再恢复", "选择备份文件", onRequestRestore)
        }
        item {
            Text("笔记与单书", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            BackupActionRow(
                title = "导出全部批注",
                body = "保存为 Markdown 或 JSON，可选包含书签",
                action = if (annotationExportInProgress) "正在导出…" else "选择导出格式",
                onClick = onRequestAnnotationExport,
                enabled = !annotationExportInProgress,
            )
            annotationExportMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item {
            BackupActionRow(
                title = "导出单书接力包",
                body = "将一本书的进度、笔记和设置带到另一台设备",
                action = "选择书籍",
                onClick = onRequestHandoffExport,
            )
        }
        item {
            BackupActionRow(
                title = "应用阅读接力包",
                body = "接着另一台设备上的位置阅读",
                action = "选择 .xinyuehandoff",
                onClick = onRequestHandoffImport,
            )
        }
    }
}

@Composable
internal fun AnnotationExportDialog(
    title: String,
    onChoose: (AnnotationExportFormat, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var includeBookmarks by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(AnnotationExportFormat.MARKDOWN) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(AnnotationExportFormat.MARKDOWN to "Markdown", AnnotationExportFormat.JSON to "JSON").forEach { (option, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = format == option, role = Role.RadioButton, onClick = { format = option }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = format == option, onClick = null)
                        Column {
                            Text(label)
                            Text(
                                if (option == AnnotationExportFormat.MARKDOWN) "方便直接阅读和整理" else "方便其他工具处理",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = includeBookmarks, role = Role.Checkbox, onValueChange = { includeBookmarks = it }),
                ) {
                    Checkbox(checked = includeBookmarks, onCheckedChange = null)
                    Text("包含书签", modifier = Modifier.padding(top = 12.dp))
                }
                Text("内容可能包含私人摘录，请保存到可信位置。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(format, includeBookmarks) }) {
                Text("导出")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun BackupActionRow(title: String, body: String, action: String, onClick: () -> Unit, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(if (enabled) body else action) },
        trailingContent = {
            Icon(painterResource(XinYueIcons.ChevronRight), contentDescription = null, modifier = Modifier.size(20.dp))
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun ProgressContent(
    padding: PaddingValues,
    label: String,
    completed: Long,
    total: Long,
    canCancel: Boolean,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp)
            .testTag("backup_progress")
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
        if (total > 0) {
            LinearProgressIndicator(
                progress = { (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("$completed / $total")
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (canCancel) TextButton(onClick = onCancel) { Text("取消") }
    }
}

@Composable
private fun ResultContent(padding: PaddingValues, state: BackupUiState, onReset: () -> Unit) {
    val work = state.activeWork
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val succeeded = work?.status == BackupWorkStatus.SUCCEEDED
        Text(if (succeeded) "操作完成" else "操作未完成", style = MaterialTheme.typography.headlineSmall)
        if (succeeded && work.type == BackupWorkType.RESTORE) {
            Text("已恢复 ${work.restoredCount} 项，跳过 ${work.skippedCount} 项，冲突副本 ${work.conflictCopyCount} 项。")
            if (work.metadataOnlyBookCount > 0) Text("有 ${work.metadataOnlyBookCount} 本仅含元数据，未创建缺少正文的书籍。")
        } else if (succeeded) {
            Text("备份文件已完整写入并校验。")
        } else {
            Text(state.resultMessage ?: if (work?.status == BackupWorkStatus.CANCELLED) "操作已取消" else "现有数据未受影响，可返回重试。")
        }
        Button(shape = MaterialTheme.shapes.medium, onClick = onReset) { Text("返回备份与恢复") }
    }
}

private fun com.xinyue.reader.core.domain.model.BackupPhase.displayLabel(): String = when (this) {
    com.xinyue.reader.core.domain.model.BackupPhase.PREPARING -> "正在准备"
    com.xinyue.reader.core.domain.model.BackupPhase.HASHING -> "正在校验"
    com.xinyue.reader.core.domain.model.BackupPhase.WRITING -> "正在写入"
    com.xinyue.reader.core.domain.model.BackupPhase.VALIDATING -> "正在验证"
    com.xinyue.reader.core.domain.model.BackupPhase.PLANNING -> "正在生成预览"
    com.xinyue.reader.core.domain.model.BackupPhase.SNAPSHOTTING -> "正在创建安全快照"
    com.xinyue.reader.core.domain.model.BackupPhase.RESTORING -> "正在恢复"
    com.xinyue.reader.core.domain.model.BackupPhase.VERIFYING -> "正在核对结果"
}
