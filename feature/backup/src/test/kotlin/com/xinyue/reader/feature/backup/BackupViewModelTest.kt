package com.xinyue.reader.feature.backup

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.data.BackupTaskScheduler
import com.xinyue.reader.core.data.BackupUriPermissionManager
import com.xinyue.reader.core.data.BackupWorkState
import com.xinyue.reader.core.data.BackupWorkStatus
import com.xinyue.reader.core.data.BackupWorkType
import com.xinyue.reader.core.data.HandoffTaskScheduler
import com.xinyue.reader.core.data.HandoffWorkState
import com.xinyue.reader.core.data.HandoffWorkType
import com.xinyue.reader.core.domain.model.BackupExportResult
import com.xinyue.reader.core.domain.model.AnnotationExportFormat
import com.xinyue.reader.core.domain.model.AnnotationExportRequest
import com.xinyue.reader.core.domain.model.AnnotationExportResult
import com.xinyue.reader.core.domain.model.BackupInspectResult
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.BackupProgress
import com.xinyue.reader.core.domain.model.HandoffBookSummary
import com.xinyue.reader.core.domain.model.HandoffExportRequest
import com.xinyue.reader.core.domain.model.HandoffExportResult
import com.xinyue.reader.core.domain.model.HandoffImportRequest
import com.xinyue.reader.core.domain.model.HandoffImportResult
import com.xinyue.reader.core.domain.model.HandoffInspectResult
import com.xinyue.reader.core.domain.model.HandoffPreview
import com.xinyue.reader.core.domain.model.RestoreConflict
import com.xinyue.reader.core.domain.model.RestoreConflictChoice
import com.xinyue.reader.core.domain.model.RestoreConflictKind
import com.xinyue.reader.core.domain.model.RestoreMode
import com.xinyue.reader.core.domain.model.RestorePreview
import com.xinyue.reader.core.domain.model.RestoreRequest
import com.xinyue.reader.core.domain.model.RestoreResult
import com.xinyue.reader.core.domain.repository.BackupService
import com.xinyue.reader.core.domain.repository.AnnotationExportService
import com.xinyue.reader.core.domain.repository.ReadingHandoffService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `export flows privacy picker observable work and terminal result without uri in state`() = runTest(dispatcher) {
        val scheduler = FakeScheduler()
        val viewModel = viewModel(scheduler = scheduler)
        viewModel.requestExport()
        assertThat(viewModel.uiState.value.showPrivacyDialog).isTrue()
        viewModel.setIncludeFonts(false)
        viewModel.confirmExportOptions()
        val event = viewModel.events.first() as BackupUiEvent.CreateBackupDocument
        assertThat(event.suggestedName).startsWith("xinyue-")
        assertThat(event.suggestedName).endsWith(".xinyuebackup")

        viewModel.onExportDocumentChosen("content://documents/private-id")
        advanceUntilIdle()
        val operationId = requireNotNull(scheduler.exportOperationId)
        assertThat(scheduler.exportOptions?.includeFonts).isFalse()
        scheduler.exportFlow.value = BackupWorkState(operationId, BackupWorkType.EXPORT, BackupWorkStatus.RUNNING)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.stage).isEqualTo(BackupUiStage.WORKING)
        scheduler.exportFlow.value = BackupWorkState(operationId, BackupWorkType.EXPORT, BackupWorkStatus.SUCCEEDED)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.stage).isEqualTo(BackupUiStage.RESULT)
        assertThat(viewModel.uiState.value.toString()).doesNotContain("private-id")
    }

    @Test
    fun `restore inspect defaults conflicts blocks incomplete rename then enqueues merge`() = runTest(dispatcher) {
        val preview = preview()
        val service = FakeService(BackupInspectResult.Success(preview))
        val scheduler = FakeScheduler()
        val permissions = FakePermissions()
        val viewModel = viewModel(service, scheduler, permissions)

        viewModel.onRestoreDocumentChosen("content://documents/private-source")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.stage).isEqualTo(BackupUiStage.PREVIEW)
        assertThat(viewModel.uiState.value.canStartRestore).isTrue()
        assertThat(permissions.persistedRead).isEqualTo(1)
        assertThat(permissions.releasedRead).isEqualTo(1)
        viewModel.selectConflict("group", RestoreConflictChoice.RENAME_BACKUP, " ")
        assertThat(viewModel.uiState.value.canStartRestore).isFalse()
        viewModel.selectConflict("group", RestoreConflictChoice.RENAME_BACKUP, "备份分组")
        viewModel.requestConfirmedRestore()
        advanceUntilIdle()

        assertThat(scheduler.restoreRequest?.mode).isEqualTo(RestoreMode.MERGE)
        assertThat(scheduler.restoreRequest?.resolutions?.single()?.renamedValue).isEqualTo("备份分组")
        assertThat(viewModel.uiState.value.toString()).doesNotContain("private-source")
    }

    @Test
    fun `overwrite requires second confirmation and metadata backup cannot select overwrite`() = runTest(dispatcher) {
        val scheduler = FakeScheduler()
        val viewModel = viewModel(FakeService(BackupInspectResult.Success(preview())), scheduler)
        viewModel.onRestoreDocumentChosen("content://documents/source")
        advanceUntilIdle()
        viewModel.selectRestoreMode(RestoreMode.OVERWRITE)
        viewModel.requestConfirmedRestore()
        assertThat(viewModel.uiState.value.showOverwriteConfirmation).isTrue()
        assertThat(scheduler.restoreRequest).isNull()
        viewModel.confirmOverwriteRestore()
        advanceUntilIdle()
        assertThat(scheduler.restoreRequest?.mode).isEqualTo(RestoreMode.OVERWRITE)

        val metadata = viewModel(FakeService(BackupInspectResult.Success(preview(BackupOptions(false, false)))), FakeScheduler())
        metadata.onRestoreDocumentChosen("content://documents/source")
        advanceUntilIdle()
        metadata.selectRestoreMode(RestoreMode.OVERWRITE)
        assertThat(metadata.uiState.value.restoreMode).isEqualTo(RestoreMode.MERGE)
        assertThat(metadata.uiState.value.errorMessage).contains("正文")
    }

    @Test
    fun `saved operation is observed after view model recreation without enqueue`() = runTest(dispatcher) {
        val scheduler = FakeScheduler()
        val saved = SavedStateHandle(
            mapOf("backup_operation_id" to "restored-operation", "backup_operation_type" to BackupWorkType.RESTORE.name),
        )
        viewModel(scheduler = scheduler, saved = saved)
        val recreated = viewModel(scheduler = scheduler, saved = saved)
        scheduler.restoreFlow.value = BackupWorkState(
            "restored-operation", BackupWorkType.RESTORE, BackupWorkStatus.RUNNING,
        )
        advanceUntilIdle()
        assertThat(recreated.uiState.value.activeWork?.operationId).isEqualTo("restored-operation")
        assertThat(scheduler.restoreEnqueueCount).isEqualTo(0)
    }

    @Test
    fun `all books annotation export uses picker event and reports a safe result`() = runTest(dispatcher) {
        val annotations = FakeAnnotationExportService()
        val viewModel = viewModel(annotationExportService = annotations)

        viewModel.requestAnnotationExport()
        assertThat(viewModel.uiState.value.showAnnotationExportDialog).isTrue()
        viewModel.chooseAnnotationExport(AnnotationExportFormat.JSON, includeBookmarks = true)
        val event = viewModel.events.first() as BackupUiEvent.CreateAnnotationExportDocument
        assertThat(event.request).isEqualTo(
            AnnotationExportRequest(AnnotationExportFormat.JSON, bookIds = null, includeBookmarks = true),
        )
        assertThat(event.suggestedName).endsWith(".json")

        viewModel.onAnnotationExportDocumentChosen("content://documents/private-export-id", event.request)
        advanceUntilIdle()

        assertThat(annotations.requests).containsExactly(event.request)
        assertThat(viewModel.uiState.value.annotationExportMessage).isEqualTo("已导出 3 条批注")
        assertThat(viewModel.uiState.value.toString()).doesNotContain("private-export-id")
    }

    @Test
    fun `handoff export and inspected merge use dedicated picker preview and workers`() = runTest(dispatcher) {
        val conflict = RestoreConflict(
            "annotation:note-1",
            RestoreConflictKind.ANNOTATION,
            "note-1",
            setOf(RestoreConflictChoice.KEEP_BOTH),
            RestoreConflictChoice.KEEP_BOTH,
        )
        val preview = HandoffPreview(
            stagedPlanToken = "handoff-token",
            formatVersion = 1,
            createdAtEpochMillis = 1,
            sourceBookId = "book-1",
            targetBookId = "local-book",
            title = "公开书名",
            includesBookText = false,
            importsNewBook = false,
            incomingProgressIsNewer = true,
            annotationInsertCount = 2,
            annotationConflictCopyCount = 1,
            collectionCount = 1,
            conflicts = listOf(conflict),
        )
        val handoffService = FakeHandoffService(HandoffInspectResult.Success(preview))
        val handoffScheduler = FakeHandoffScheduler()
        val viewModel = viewModel(
            handoffService = handoffService,
            handoffScheduler = handoffScheduler,
        )
        advanceUntilIdle()

        viewModel.requestHandoffExport()
        assertThat(viewModel.uiState.value.showHandoffExportDialog).isTrue()
        viewModel.setHandoffIncludeBookText(true)
        viewModel.confirmHandoffExport()
        val exportEvent = viewModel.events.first() as BackupUiEvent.CreateHandoffDocument
        assertThat(exportEvent.request).isEqualTo(HandoffExportRequest("book-1", true))
        assertThat(exportEvent.suggestedName).endsWith(".xinyuehandoff")
        viewModel.onHandoffExportDocumentChosen("content://documents/private-handoff", exportEvent.request)
        advanceUntilIdle()
        assertThat(handoffScheduler.exportRequest).isEqualTo(exportEvent.request)

        viewModel.onHandoffDocumentChosen("content://documents/private-handoff")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.stage).isEqualTo(BackupUiStage.HANDOFF_PREVIEW)
        assertThat(viewModel.uiState.value.canStartHandoffImport).isTrue()
        viewModel.confirmHandoffImport()
        advanceUntilIdle()
        assertThat(handoffScheduler.importRequest?.stagedPlanToken).isEqualTo("handoff-token")
        assertThat(handoffScheduler.importRequest?.resolutions?.single()?.choice)
            .isEqualTo(RestoreConflictChoice.KEEP_BOTH)
        assertThat(viewModel.uiState.value.toString()).doesNotContain("private-handoff")
    }

    private fun viewModel(
        service: FakeService = FakeService(BackupInspectResult.Cancelled),
        scheduler: FakeScheduler = FakeScheduler(),
        permissions: FakePermissions = FakePermissions(),
        saved: SavedStateHandle = SavedStateHandle(),
        annotationExportService: AnnotationExportService = FakeAnnotationExportService(),
        handoffService: ReadingHandoffService = FakeHandoffService(HandoffInspectResult.Cancelled),
        handoffScheduler: HandoffTaskScheduler = FakeHandoffScheduler(),
    ) = BackupViewModel(
        service,
        scheduler,
        permissions,
        saved,
        annotationExportService,
        handoffService,
        handoffScheduler,
    )

    private fun preview(options: BackupOptions = BackupOptions()) = RestorePreview(
        stagedPlanToken = "opaque-token",
        formatVersion = 1,
        createdAtEpochMillis = 1,
        options = options,
        newBookCount = 2,
        duplicateBookCount = 1,
        conflictCount = 1,
        stagingBytes = 10,
        publishBytes = 20,
        snapshotBytes = 30,
        requiredFreeBytes = 40,
        conflicts = listOf(
            RestoreConflict(
                "group", RestoreConflictKind.GROUP, "公开分组",
                setOf(RestoreConflictChoice.KEEP_LOCAL, RestoreConflictChoice.RENAME_BACKUP),
                RestoreConflictChoice.KEEP_LOCAL,
            ),
        ),
    )

    private class FakeService(private val inspectResult: BackupInspectResult) : BackupService {
        override suspend fun export(destinationUri: String, options: BackupOptions, onProgress: (BackupProgress) -> Unit) =
            BackupExportResult.Cancelled
        override suspend fun inspect(sourceUri: String, onProgress: (BackupProgress) -> Unit): BackupInspectResult = inspectResult
        override suspend fun restore(request: RestoreRequest, onProgress: (BackupProgress) -> Unit) = RestoreResult.Cancelled
    }

    private class FakeAnnotationExportService : AnnotationExportService {
        val requests = mutableListOf<AnnotationExportRequest>()
        override suspend fun export(
            destinationUri: String,
            request: AnnotationExportRequest,
        ): AnnotationExportResult {
            requests += request
            return AnnotationExportResult.Success(bookCount = 2, annotationCount = 3, byteCount = 100)
        }
    }

    private class FakeHandoffService(
        private val inspectResult: HandoffInspectResult,
    ) : ReadingHandoffService {
        override suspend fun listBooks() = listOf(
            HandoffBookSummary("book-1", "公开书名", "公开作者", "公开系列"),
        )
        override suspend fun export(
            destinationUri: String,
            request: HandoffExportRequest,
            onProgress: (BackupProgress) -> Unit,
        ) = HandoffExportResult.Cancelled
        override suspend fun inspect(
            sourceUri: String,
            onProgress: (BackupProgress) -> Unit,
        ) = inspectResult
        override suspend fun import(
            request: HandoffImportRequest,
            onProgress: (BackupProgress) -> Unit,
        ) = HandoffImportResult.Cancelled
    }

    private class FakeHandoffScheduler : HandoffTaskScheduler {
        val exportFlow = MutableStateFlow<HandoffWorkState?>(null)
        val importFlow = MutableStateFlow<HandoffWorkState?>(null)
        var exportRequest: HandoffExportRequest? = null
        var importRequest: HandoffImportRequest? = null
        override fun enqueueExport(
            operationId: String,
            destinationUri: String,
            request: HandoffExportRequest,
        ) {
            exportRequest = request
        }
        override fun enqueueImport(operationId: String, request: HandoffImportRequest) {
            importRequest = request
        }
        override fun observeExport(operationId: String): Flow<HandoffWorkState?> = exportFlow
        override fun observeImport(operationId: String): Flow<HandoffWorkState?> = importFlow
        override fun cancelExport(operationId: String) = Unit
        override fun cancelImport(operationId: String) = Unit
    }

    private class FakeScheduler : BackupTaskScheduler {
        val exportFlow = MutableStateFlow<BackupWorkState?>(null)
        val restoreFlow = MutableStateFlow<BackupWorkState?>(null)
        var exportOperationId: String? = null
        var exportOptions: BackupOptions? = null
        var restoreRequest: RestoreRequest? = null
        var restoreEnqueueCount = 0
        override fun enqueueExport(operationId: String, destinationUri: String, options: BackupOptions) {
            exportOperationId = operationId
            exportOptions = options
        }
        override fun enqueueRestore(operationId: String, request: RestoreRequest) {
            restoreEnqueueCount++
            restoreRequest = request
        }
        override fun observeExport(operationId: String): Flow<BackupWorkState?> = exportFlow
        override fun observeRestore(operationId: String): Flow<BackupWorkState?> = restoreFlow
        override fun cancelExport(operationId: String) = Unit
        override fun cancelRestore(operationId: String) = Unit
    }

    private class FakePermissions : BackupUriPermissionManager {
        var persistedRead = 0
        var releasedRead = 0
        override fun persistWrite(uriString: String) = Unit
        override fun releaseWrite(uriString: String) = Unit
        override fun persistRead(uriString: String) { persistedRead++ }
        override fun releaseRead(uriString: String) { releasedRead++ }
    }
}
