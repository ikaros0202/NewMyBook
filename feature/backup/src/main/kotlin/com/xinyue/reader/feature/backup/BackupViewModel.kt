package com.xinyue.reader.feature.backup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xinyue.reader.core.data.BackupTaskScheduler
import com.xinyue.reader.core.data.BackupUriPermissionManager
import com.xinyue.reader.core.data.BackupWorkState
import com.xinyue.reader.core.data.BackupWorkStatus
import com.xinyue.reader.core.data.BackupWorkType
import com.xinyue.reader.core.data.HandoffTaskScheduler
import com.xinyue.reader.core.data.HandoffWorkState
import com.xinyue.reader.core.data.HandoffWorkType
import com.xinyue.reader.core.domain.model.BackupErrorCode
import com.xinyue.reader.core.domain.model.AnnotationExportFormat
import com.xinyue.reader.core.domain.model.AnnotationExportRequest
import com.xinyue.reader.core.domain.model.AnnotationExportResult
import com.xinyue.reader.core.domain.model.BackupInspectResult
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.BackupPhase
import com.xinyue.reader.core.domain.model.BackupProgress
import com.xinyue.reader.core.domain.model.HandoffBookSummary
import com.xinyue.reader.core.domain.model.HandoffExportRequest
import com.xinyue.reader.core.domain.model.HandoffImportRequest
import com.xinyue.reader.core.domain.model.HandoffInspectResult
import com.xinyue.reader.core.domain.model.HandoffPreview
import com.xinyue.reader.core.domain.model.RestoreConflictChoice
import com.xinyue.reader.core.domain.model.RestoreMode
import com.xinyue.reader.core.domain.model.RestorePreview
import com.xinyue.reader.core.domain.model.RestoreRequest
import com.xinyue.reader.core.domain.repository.BackupService
import com.xinyue.reader.core.domain.repository.AnnotationExportService
import com.xinyue.reader.core.domain.repository.ReadingHandoffService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

enum class BackupUiStage {
    HOME,
    INSPECTING,
    PREVIEW,
    WORKING,
    RESULT,
    HANDOFF_INSPECTING,
    HANDOFF_PREVIEW,
    HANDOFF_WORKING,
    HANDOFF_RESULT,
}

data class BackupUiState(
    val stage: BackupUiStage = BackupUiStage.HOME,
    val showPrivacyDialog: Boolean = false,
    val showOverwriteConfirmation: Boolean = false,
    val options: BackupOptions = BackupOptions(),
    val preview: RestorePreview? = null,
    val conflictSelections: Map<String, RestoreConflictSelection> = emptyMap(),
    val restoreMode: RestoreMode = RestoreMode.MERGE,
    val inspectProgress: BackupProgress? = null,
    val activeWork: BackupWorkState? = null,
    val errorMessage: String? = null,
    val resultMessage: String? = null,
    val showAnnotationExportDialog: Boolean = false,
    val annotationExportInProgress: Boolean = false,
    val annotationExportMessage: String? = null,
    val handoffBooks: List<HandoffBookSummary> = emptyList(),
    val showHandoffExportDialog: Boolean = false,
    val selectedHandoffBookId: String? = null,
    val handoffIncludeBookText: Boolean = false,
    val handoffPreview: HandoffPreview? = null,
    val handoffConflictSelections: Map<String, RestoreConflictSelection> = emptyMap(),
    val handoffProgress: BackupProgress? = null,
    val activeHandoffWork: HandoffWorkState? = null,
    val handoffMessage: String? = null,
) {
    val canStartRestore: Boolean
        get() = preview?.let { conflictSelectionsComplete(it.conflicts, conflictSelections) } == true
    val canCancel: Boolean
        get() = when {
            stage in setOf(BackupUiStage.INSPECTING, BackupUiStage.HANDOFF_INSPECTING) -> true
            stage == BackupUiStage.WORKING ->
                activeWork?.phase !in setOf(BackupPhase.RESTORING, BackupPhase.VERIFYING)
            stage == BackupUiStage.HANDOFF_WORKING ->
                activeHandoffWork?.phase !in setOf(BackupPhase.RESTORING, BackupPhase.VERIFYING)
            else -> false
        }
    val canStartHandoffImport: Boolean
        get() = handoffPreview?.let {
            conflictSelectionsComplete(it.conflicts, handoffConflictSelections)
        } == true
}

sealed interface BackupUiEvent {
    data class CreateBackupDocument(val suggestedName: String) : BackupUiEvent
    data class CreateAnnotationExportDocument(
        val suggestedName: String,
        val request: AnnotationExportRequest,
    ) : BackupUiEvent
    data object OpenBackupDocument : BackupUiEvent
    data class CreateHandoffDocument(
        val suggestedName: String,
        val request: HandoffExportRequest,
    ) : BackupUiEvent
    data object OpenHandoffDocument : BackupUiEvent
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val service: BackupService,
    private val scheduler: BackupTaskScheduler,
    private val permissions: BackupUriPermissionManager,
    private val savedStateHandle: SavedStateHandle,
    private val annotationExportService: AnnotationExportService = UnavailableAnnotationExportService,
    private val handoffService: ReadingHandoffService = UnavailableReadingHandoffService,
    private val handoffScheduler: HandoffTaskScheduler = UnavailableHandoffTaskScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()
    private val eventChannel = Channel<BackupUiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var inspectJob: Job? = null
    private var workObservation: Job? = null
    private var handoffObservation: Job? = null

    init {
        val operationId = savedStateHandle.get<String>(KEY_OPERATION_ID)
        val type = savedStateHandle.get<String>(KEY_OPERATION_TYPE)
            ?.let { runCatching { BackupWorkType.valueOf(it) }.getOrNull() }
        if (operationId != null && type != null) observeWork(operationId, type)
        val handoffOperationId = savedStateHandle.get<String>(KEY_HANDOFF_OPERATION_ID)
        val handoffType = savedStateHandle.get<String>(KEY_HANDOFF_OPERATION_TYPE)
            ?.let { runCatching { HandoffWorkType.valueOf(it) }.getOrNull() }
        if (handoffOperationId != null && handoffType != null) {
            observeHandoffWork(handoffOperationId, handoffType)
        }
        viewModelScope.launch {
            runCatching { handoffService.listBooks() }
                .onSuccess { books ->
                    _uiState.update { state ->
                        state.copy(
                            handoffBooks = books,
                            selectedHandoffBookId = state.selectedHandoffBookId ?: books.firstOrNull()?.id,
                        )
                    }
                }
        }
    }

    fun requestExport() {
        _uiState.update { it.copy(showPrivacyDialog = true, errorMessage = null) }
    }

    fun dismissPrivacyDialog() {
        _uiState.update { it.copy(showPrivacyDialog = false) }
    }

    fun setIncludeBookText(value: Boolean) {
        _uiState.update { it.copy(options = it.options.copy(includeBookText = value)) }
    }

    fun setIncludeFonts(value: Boolean) {
        _uiState.update { it.copy(options = it.options.copy(includeFonts = value)) }
    }

    fun confirmExportOptions() {
        _uiState.update { it.copy(showPrivacyDialog = false, errorMessage = null) }
        eventChannel.trySend(BackupUiEvent.CreateBackupDocument(suggestedFileName()))
    }

    fun onExportDocumentChosen(uriString: String?) {
        if (uriString == null) return
        val operationId = UUID.randomUUID().toString()
        runCatching { scheduler.enqueueExport(operationId, uriString, _uiState.value.options) }
            .onSuccess { observeWork(operationId, BackupWorkType.EXPORT) }
            .onFailure { showError("无法开始备份，请重新选择保存位置") }
    }

    fun requestAnnotationExport() {
        _uiState.update {
            it.copy(showAnnotationExportDialog = true, annotationExportMessage = null)
        }
    }

    fun dismissAnnotationExport() {
        _uiState.update { it.copy(showAnnotationExportDialog = false) }
    }

    fun chooseAnnotationExport(format: AnnotationExportFormat, includeBookmarks: Boolean) {
        val request = AnnotationExportRequest(
            format = format,
            bookIds = null,
            includeBookmarks = includeBookmarks,
        )
        _uiState.update { it.copy(showAnnotationExportDialog = false) }
        eventChannel.trySend(
            BackupUiEvent.CreateAnnotationExportDocument(
                suggestedName = when (format) {
                    AnnotationExportFormat.MARKDOWN -> "xinyue-all-annotations.md"
                    AnnotationExportFormat.JSON -> "xinyue-all-annotations.json"
                },
                request = request,
            ),
        )
    }

    fun onAnnotationExportDocumentChosen(
        uriString: String?,
        request: AnnotationExportRequest,
    ) {
        if (uriString == null || _uiState.value.annotationExportInProgress) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(annotationExportInProgress = true, annotationExportMessage = "正在导出批注…")
            }
            val result = annotationExportService.export(uriString, request)
            _uiState.update { state ->
                when (result) {
                    is AnnotationExportResult.Success -> state.copy(
                        annotationExportInProgress = false,
                        annotationExportMessage = "已导出 ${result.annotationCount} 条批注",
                    )
                    is AnnotationExportResult.Failure -> state.copy(
                        annotationExportInProgress = false,
                        annotationExportMessage = result.safeMessage,
                    )
                }
            }
        }
    }

    fun requestHandoffExport() {
        if (_uiState.value.handoffBooks.isEmpty()) {
            showError("书架中没有可导出接力包的书籍")
            return
        }
        _uiState.update {
            it.copy(
                showHandoffExportDialog = true,
                selectedHandoffBookId = it.selectedHandoffBookId ?: it.handoffBooks.first().id,
                handoffMessage = null,
            )
        }
    }

    fun dismissHandoffExport() {
        _uiState.update { it.copy(showHandoffExportDialog = false) }
    }

    fun selectHandoffBook(bookId: String) {
        if (_uiState.value.handoffBooks.none { it.id == bookId }) return
        _uiState.update { it.copy(selectedHandoffBookId = bookId) }
    }

    fun setHandoffIncludeBookText(include: Boolean) {
        _uiState.update { it.copy(handoffIncludeBookText = include) }
    }

    fun confirmHandoffExport() {
        val state = _uiState.value
        val bookId = state.selectedHandoffBookId ?: return
        val request = HandoffExportRequest(bookId, state.handoffIncludeBookText)
        _uiState.update { it.copy(showHandoffExportDialog = false) }
        eventChannel.trySend(
            BackupUiEvent.CreateHandoffDocument(
                suggestedName = handoffFileName(bookId),
                request = request,
            ),
        )
    }

    fun onHandoffExportDocumentChosen(uriString: String?, request: HandoffExportRequest) {
        if (uriString == null) return
        val operationId = UUID.randomUUID().toString()
        runCatching { handoffScheduler.enqueueExport(operationId, uriString, request) }
            .onSuccess { observeHandoffWork(operationId, HandoffWorkType.EXPORT) }
            .onFailure { showHandoffError("无法开始导出接力包，请重新选择保存位置") }
    }

    fun requestHandoffImport() {
        _uiState.update { it.copy(errorMessage = null, handoffMessage = null) }
        eventChannel.trySend(BackupUiEvent.OpenHandoffDocument)
    }

    fun onHandoffDocumentChosen(uriString: String?) {
        if (uriString == null) return
        inspectJob?.cancel()
        inspectJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    stage = BackupUiStage.HANDOFF_INSPECTING,
                    handoffProgress = null,
                    errorMessage = null,
                    handoffMessage = null,
                )
            }
            var permissionHeld = false
            try {
                permissions.persistRead(uriString)
                permissionHeld = true
                when (val result = handoffService.inspect(uriString) { progress ->
                    _uiState.update { state ->
                        state.copy(handoffProgress = progress.copy(label = genericProgressLabel(progress.phase)))
                    }
                }) {
                    is HandoffInspectResult.Success -> _uiState.update {
                        it.copy(
                            stage = BackupUiStage.HANDOFF_PREVIEW,
                            handoffPreview = result.preview,
                            handoffConflictSelections = defaultConflictSelections(result.preview.conflicts),
                            handoffProgress = null,
                        )
                    }
                    is HandoffInspectResult.Failure -> showHandoffError(result.error.safeMessage())
                    HandoffInspectResult.Cancelled -> _uiState.update {
                        it.copy(stage = BackupUiStage.HOME, handoffProgress = null)
                    }
                }
            } catch (_: Throwable) {
                showHandoffError("无法读取所选接力包")
            } finally {
                if (permissionHeld) permissions.releaseRead(uriString)
            }
        }
    }

    fun updateHandoffConflictSelection(conflictId: String, selection: RestoreConflictSelection) {
        val preview = _uiState.value.handoffPreview ?: return
        val conflict = preview.conflicts.singleOrNull { it.id == conflictId } ?: return
        if (selection.choice !in conflict.allowedChoices) return
        _uiState.update {
            it.copy(
                handoffConflictSelections = it.handoffConflictSelections +
                    (conflictId to selection),
            )
        }
    }

    fun confirmHandoffImport() {
        val state = _uiState.value
        val preview = state.handoffPreview ?: return
        if (!state.canStartHandoffImport) return
        val request = HandoffImportRequest(
            preview.stagedPlanToken,
            toRestoreResolutions(preview.conflicts, state.handoffConflictSelections),
        )
        val operationId = UUID.randomUUID().toString()
        runCatching { handoffScheduler.enqueueImport(operationId, request) }
            .onSuccess { observeHandoffWork(operationId, HandoffWorkType.IMPORT) }
            .onFailure { showHandoffError("无法开始应用接力包，请重新预览") }
    }

    fun requestRestore() {
        _uiState.update { it.copy(errorMessage = null) }
        eventChannel.trySend(BackupUiEvent.OpenBackupDocument)
    }

    fun onRestoreDocumentChosen(uriString: String?) {
        if (uriString == null) return
        inspectJob?.cancel()
        inspectJob = viewModelScope.launch {
            _uiState.update { it.copy(stage = BackupUiStage.INSPECTING, inspectProgress = null, errorMessage = null) }
            var permissionHeld = false
            try {
                permissions.persistRead(uriString)
                permissionHeld = true
                when (val result = service.inspect(uriString) { progress ->
                    _uiState.update { state -> state.copy(inspectProgress = progress.copy(label = genericProgressLabel(progress.phase))) }
                }) {
                    is BackupInspectResult.Success -> _uiState.update {
                        it.copy(
                            stage = BackupUiStage.PREVIEW,
                            preview = result.preview,
                            conflictSelections = defaultConflictSelections(result.preview.conflicts),
                            restoreMode = RestoreMode.MERGE,
                            inspectProgress = null,
                        )
                    }
                    is BackupInspectResult.Failure -> showError(result.error.safeMessage())
                    BackupInspectResult.Cancelled -> _uiState.update { it.copy(stage = BackupUiStage.HOME, inspectProgress = null) }
                }
            } catch (_: Throwable) {
                showError("无法读取所选备份文件")
            } finally {
                if (permissionHeld) permissions.releaseRead(uriString)
            }
        }
    }

    fun selectConflict(conflictId: String, choice: RestoreConflictChoice, renamedValue: String = "") {
        val preview = _uiState.value.preview ?: return
        val conflict = preview.conflicts.singleOrNull { it.id == conflictId } ?: return
        if (choice !in conflict.allowedChoices) return
        _uiState.update {
            it.copy(conflictSelections = it.conflictSelections + (conflictId to RestoreConflictSelection(choice, renamedValue)))
        }
    }

    fun updateConflictSelection(conflictId: String, selection: RestoreConflictSelection) {
        selectConflict(conflictId, selection.choice, selection.renamedValue)
    }

    fun selectRestoreMode(mode: RestoreMode) {
        if (mode == RestoreMode.OVERWRITE && _uiState.value.preview?.options?.includeBookText != true) {
            _uiState.update { it.copy(restoreMode = RestoreMode.MERGE, errorMessage = "未包含小说正文的备份不能覆盖整个书架") }
            return
        }
        _uiState.update { it.copy(restoreMode = mode) }
    }

    fun requestConfirmedRestore() {
        val state = _uiState.value
        if (!state.canStartRestore) return
        if (state.restoreMode == RestoreMode.OVERWRITE) {
            _uiState.update { it.copy(showOverwriteConfirmation = true) }
        } else {
            enqueueRestore()
        }
    }

    fun dismissOverwriteConfirmation() {
        _uiState.update { it.copy(showOverwriteConfirmation = false) }
    }

    fun confirmOverwriteRestore() {
        _uiState.update { it.copy(showOverwriteConfirmation = false) }
        enqueueRestore()
    }

    fun cancelActiveOperation() {
        val state = _uiState.value
        if (!state.canCancel) return
        if (state.stage in setOf(BackupUiStage.INSPECTING, BackupUiStage.HANDOFF_INSPECTING)) {
            inspectJob?.cancel()
            _uiState.update {
                it.copy(stage = BackupUiStage.HOME, inspectProgress = null, handoffProgress = null)
            }
            return
        }
        val handoffWork = state.activeHandoffWork
        if (state.stage == BackupUiStage.HANDOFF_WORKING && handoffWork != null) {
            when (handoffWork.type) {
                HandoffWorkType.EXPORT -> handoffScheduler.cancelExport(handoffWork.operationId)
                HandoffWorkType.IMPORT -> handoffScheduler.cancelImport(handoffWork.operationId)
            }
            return
        }
        val work = state.activeWork ?: return
        when (work.type) {
            BackupWorkType.EXPORT -> scheduler.cancelExport(work.operationId)
            BackupWorkType.RESTORE -> scheduler.cancelRestore(work.operationId)
        }
    }

    fun reset() {
        _uiState.value.preview?.stagedPlanToken?.let { token ->
            viewModelScope.launch { service.discardRestorePlan(token) }
        }
        _uiState.value.handoffPreview?.stagedPlanToken?.let { token ->
            viewModelScope.launch { handoffService.discard(token) }
        }
        clearPersistedWork()
        clearPersistedHandoffWork()
        val state = _uiState.value
        _uiState.value = BackupUiState(
            options = state.options,
            handoffBooks = state.handoffBooks,
            selectedHandoffBookId = state.selectedHandoffBookId,
            handoffIncludeBookText = state.handoffIncludeBookText,
        )
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun enqueueRestore() {
        val state = _uiState.value
        val preview = state.preview ?: return
        if (!state.canStartRestore) return
        val request = RestoreRequest(
            preview.stagedPlanToken,
            state.restoreMode,
            toRestoreResolutions(preview.conflicts, state.conflictSelections),
        )
        val operationId = UUID.randomUUID().toString()
        runCatching { scheduler.enqueueRestore(operationId, request) }
            .onSuccess { observeWork(operationId, BackupWorkType.RESTORE) }
            .onFailure { showError("无法开始恢复，请重新预览备份") }
    }

    private fun observeWork(operationId: String, type: BackupWorkType) {
        savedStateHandle[KEY_OPERATION_ID] = operationId
        savedStateHandle[KEY_OPERATION_TYPE] = type.name
        workObservation?.cancel()
        workObservation = viewModelScope.launch {
            val flow = when (type) {
                BackupWorkType.EXPORT -> scheduler.observeExport(operationId)
                BackupWorkType.RESTORE -> scheduler.observeRestore(operationId)
            }
            flow.collect { work ->
                if (work == null) return@collect
                val terminal = work.status in TERMINAL_STATUSES
                _uiState.update { state ->
                    val failureMessage = if (work.status == BackupWorkStatus.FAILED) work.errorCode.safeMessage() else null
                    state.copy(
                        stage = if (terminal) BackupUiStage.RESULT else BackupUiStage.WORKING,
                        activeWork = work,
                        errorMessage = failureMessage,
                        resultMessage = failureMessage,
                    )
                }
                if (terminal) clearPersistedWork()
            }
        }
    }

    private fun observeHandoffWork(operationId: String, type: HandoffWorkType) {
        savedStateHandle[KEY_HANDOFF_OPERATION_ID] = operationId
        savedStateHandle[KEY_HANDOFF_OPERATION_TYPE] = type.name
        handoffObservation?.cancel()
        handoffObservation = viewModelScope.launch {
            val flow = when (type) {
                HandoffWorkType.EXPORT -> handoffScheduler.observeExport(operationId)
                HandoffWorkType.IMPORT -> handoffScheduler.observeImport(operationId)
            }
            flow.collect { work ->
                if (work == null) return@collect
                val terminal = work.status in TERMINAL_STATUSES
                _uiState.update { state ->
                    val failureMessage = if (work.status == BackupWorkStatus.FAILED) {
                        work.errorCode.safeMessage()
                    } else {
                        null
                    }
                    state.copy(
                        stage = if (terminal) BackupUiStage.HANDOFF_RESULT
                        else BackupUiStage.HANDOFF_WORKING,
                        activeHandoffWork = work,
                        errorMessage = failureMessage,
                        handoffMessage = failureMessage,
                    )
                }
                if (terminal) clearPersistedHandoffWork()
            }
        }
    }

    private fun clearPersistedWork() {
        savedStateHandle.remove<String>(KEY_OPERATION_ID)
        savedStateHandle.remove<String>(KEY_OPERATION_TYPE)
    }

    private fun clearPersistedHandoffWork() {
        savedStateHandle.remove<String>(KEY_HANDOFF_OPERATION_ID)
        savedStateHandle.remove<String>(KEY_HANDOFF_OPERATION_TYPE)
    }

    private fun showError(message: String) {
        _uiState.update {
            it.copy(stage = BackupUiStage.RESULT, errorMessage = message, resultMessage = message, inspectProgress = null)
        }
    }

    private fun showHandoffError(message: String) {
        _uiState.update {
            it.copy(
                stage = BackupUiStage.HANDOFF_RESULT,
                errorMessage = message,
                handoffMessage = message,
                handoffProgress = null,
            )
        }
    }

    private fun suggestedFileName(): String {
        val timestamp = FILE_TIME_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault()))
        return "xinyue-$timestamp.xinyuebackup"
    }

    private fun handoffFileName(bookId: String): String {
        val timestamp = FILE_TIME_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault()))
        return "xinyue-${bookId.take(24)}-$timestamp.xinyuehandoff"
    }

    private fun BackupErrorCode?.safeMessage(): String = when (this) {
        BackupErrorCode.PERMISSION -> "文件权限已失效，请重新选择"
        BackupErrorCode.SPACE -> "可用空间不足，未更改现有数据"
        BackupErrorCode.SOURCE_CHANGED -> "备份期间数据发生变化，请重试"
        BackupErrorCode.PROVIDER -> "文件提供方暂时不可用，请重试"
        BackupErrorCode.INVALID_FORMAT -> "备份格式无效"
        BackupErrorCode.SECURITY -> "备份文件未通过安全校验"
        BackupErrorCode.CONFLICT -> "恢复预览已失效，请重新检查"
        BackupErrorCode.CANCELLED -> "操作已取消"
        BackupErrorCode.UNKNOWN, null -> "操作失败，现有数据未受影响"
    }

    private fun com.xinyue.reader.core.domain.model.BackupError.safeMessage(): String = code.safeMessage()

    private fun genericProgressLabel(phase: BackupPhase): String = when (phase) {
        BackupPhase.PREPARING -> "正在准备"
        BackupPhase.HASHING -> "正在校验"
        BackupPhase.WRITING -> "正在写入"
        BackupPhase.VALIDATING -> "正在验证"
        BackupPhase.PLANNING -> "正在生成预览"
        BackupPhase.SNAPSHOTTING -> "正在创建安全快照"
        BackupPhase.RESTORING -> "正在安全恢复"
        BackupPhase.VERIFYING -> "正在核对结果"
    }

    private companion object {
        const val KEY_OPERATION_ID = "backup_operation_id"
        const val KEY_OPERATION_TYPE = "backup_operation_type"
        const val KEY_HANDOFF_OPERATION_ID = "handoff_operation_id"
        const val KEY_HANDOFF_OPERATION_TYPE = "handoff_operation_type"
        val FILE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
        val TERMINAL_STATUSES = setOf(
            BackupWorkStatus.SUCCEEDED,
            BackupWorkStatus.FAILED,
            BackupWorkStatus.CANCELLED,
        )
    }
}

private object UnavailableAnnotationExportService : AnnotationExportService {
    override suspend fun export(
        destinationUri: String,
        request: AnnotationExportRequest,
    ): AnnotationExportResult = AnnotationExportResult.Failure("批注导出服务不可用")
}

private object UnavailableReadingHandoffService : ReadingHandoffService {
    override suspend fun export(
        destinationUri: String,
        request: HandoffExportRequest,
        onProgress: (BackupProgress) -> Unit,
    ) = com.xinyue.reader.core.domain.model.HandoffExportResult.Failure(
        com.xinyue.reader.core.domain.model.BackupError(
            BackupErrorCode.UNKNOWN,
            "接力服务不可用",
        ),
    )

    override suspend fun inspect(
        sourceUri: String,
        onProgress: (BackupProgress) -> Unit,
    ) = HandoffInspectResult.Failure(
        com.xinyue.reader.core.domain.model.BackupError(
            BackupErrorCode.UNKNOWN,
            "接力服务不可用",
        ),
    )

    override suspend fun import(
        request: HandoffImportRequest,
        onProgress: (BackupProgress) -> Unit,
    ) = com.xinyue.reader.core.domain.model.HandoffImportResult.Failure(
        com.xinyue.reader.core.domain.model.BackupError(
            BackupErrorCode.UNKNOWN,
            "接力服务不可用",
        ),
    )
}

private object UnavailableHandoffTaskScheduler : HandoffTaskScheduler {
    override fun enqueueExport(
        operationId: String,
        destinationUri: String,
        request: HandoffExportRequest,
    ) = error("接力任务调度器不可用")

    override fun enqueueImport(operationId: String, request: HandoffImportRequest) =
        error("接力任务调度器不可用")

    override fun observeExport(operationId: String) = flowOf<HandoffWorkState?>(null)
    override fun observeImport(operationId: String) = flowOf<HandoffWorkState?>(null)
    override fun cancelExport(operationId: String) = Unit
    override fun cancelImport(operationId: String) = Unit
}
