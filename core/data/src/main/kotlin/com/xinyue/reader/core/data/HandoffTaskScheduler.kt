package com.xinyue.reader.core.data

import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.xinyue.reader.core.domain.model.HandoffExportRequest
import com.xinyue.reader.core.domain.model.HandoffImportRequest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface HandoffTaskScheduler {
    fun enqueueExport(operationId: String, destinationUri: String, request: HandoffExportRequest)
    fun enqueueImport(operationId: String, request: HandoffImportRequest)
    fun observeExport(operationId: String): Flow<HandoffWorkState?>
    fun observeImport(operationId: String): Flow<HandoffWorkState?>
    fun cancelExport(operationId: String)
    fun cancelImport(operationId: String)
}

@Singleton
class WorkManagerHandoffTaskScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val permissions: BackupUriPermissionManager,
    private val importRequests: HandoffImportRequestStore,
) : HandoffTaskScheduler {
    override fun enqueueExport(
        operationId: String,
        destinationUri: String,
        request: HandoffExportRequest,
    ) {
        requireId(operationId)
        require(request.bookId.matches(BackupWork.ID_PATTERN)) { "接力书籍 ID 无效" }
        permissions.persistWrite(destinationUri)
        try {
            val work = OneTimeWorkRequest.Builder(HandoffExportWorker::class.java)
                .setId(stableWorkId(HandoffWorkType.EXPORT, operationId))
                .setInputData(
                    Data.Builder()
                        .putString(HandoffWork.KEY_OPERATION_ID, operationId)
                        .putString(HandoffWork.KEY_DESTINATION_URI, destinationUri)
                        .putString(HandoffWork.KEY_BOOK_ID, request.bookId)
                        .putBoolean(HandoffWork.KEY_INCLUDE_BOOK_TEXT, request.includeBookText)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag(HandoffWork.TAG_EXPORT)
                .build()
            workManager.enqueueUniqueWork(exportName(operationId), ExistingWorkPolicy.KEEP, work)
        } catch (error: Throwable) {
            permissions.releaseWrite(destinationUri)
            throw error
        }
    }

    override fun enqueueImport(operationId: String, request: HandoffImportRequest) {
        requireId(operationId)
        importRequests.save(request)
        try {
            val work = OneTimeWorkRequest.Builder(HandoffImportWorker::class.java)
                .setId(stableWorkId(HandoffWorkType.IMPORT, operationId))
                .setInputData(
                    Data.Builder()
                        .putString(HandoffWork.KEY_OPERATION_ID, operationId)
                        .putString(HandoffWork.KEY_STAGED_PLAN_TOKEN, request.stagedPlanToken)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag(HandoffWork.TAG_IMPORT)
                .build()
            workManager.enqueueUniqueWork(importName(operationId), ExistingWorkPolicy.KEEP, work)
        } catch (error: Throwable) {
            importRequests.delete(request.stagedPlanToken)
            throw error
        }
    }

    override fun observeExport(operationId: String): Flow<HandoffWorkState?> =
        observe(operationId, HandoffWorkType.EXPORT, exportName(operationId))

    override fun observeImport(operationId: String): Flow<HandoffWorkState?> =
        observe(operationId, HandoffWorkType.IMPORT, importName(operationId))

    override fun cancelExport(operationId: String) {
        requireId(operationId)
        workManager.cancelUniqueWork(exportName(operationId))
    }

    override fun cancelImport(operationId: String) {
        requireId(operationId)
        workManager.cancelUniqueWork(importName(operationId))
    }

    private fun observe(
        operationId: String,
        type: HandoffWorkType,
        uniqueName: String,
    ): Flow<HandoffWorkState?> {
        requireId(operationId)
        return workManager.getWorkInfosForUniqueWorkFlow(uniqueName).map { infos ->
            infos.maxByOrNull { it.runAttemptCount }?.let { HandoffWork.state(operationId, type, it) }
        }
    }

    private fun requireId(value: String) =
        require(value.matches(BackupWork.ID_PATTERN)) { "接力任务 ID 无效" }

    private fun exportName(id: String) = HandoffWork.UNIQUE_EXPORT_PREFIX + id
    private fun importName(id: String) = HandoffWork.UNIQUE_IMPORT_PREFIX + id
    private fun stableWorkId(type: HandoffWorkType, id: String): UUID =
        UUID.nameUUIDFromBytes("${type.name}:$id".encodeToByteArray())
}
