package com.xinyue.reader.core.data

import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.RestoreRequest
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface BackupTaskScheduler {
    fun enqueueExport(operationId: String, destinationUri: String, options: BackupOptions)
    fun enqueueRestore(operationId: String, request: RestoreRequest)
    fun observeExport(operationId: String): Flow<BackupWorkState?>
    fun observeRestore(operationId: String): Flow<BackupWorkState?>
    fun cancelExport(operationId: String)
    fun cancelRestore(operationId: String)
}

@Singleton
class WorkManagerBackupTaskScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val permissions: BackupUriPermissionManager,
    private val restoreRequests: RestoreRequestStore,
) : BackupTaskScheduler {
    override fun enqueueExport(operationId: String, destinationUri: String, options: BackupOptions) {
        requireId(operationId)
        permissions.persistWrite(destinationUri)
        try {
            val request = OneTimeWorkRequest.Builder(BackupExportWorker::class.java)
                .setId(stableWorkId(BackupWorkType.EXPORT, operationId))
                .setInputData(
                    Data.Builder()
                        .putString(BackupWork.KEY_OPERATION_ID, operationId)
                        .putString(BackupWork.KEY_DESTINATION_URI, destinationUri)
                        .putBoolean(BackupWork.KEY_INCLUDE_BOOK_TEXT, options.includeBookText)
                        .putBoolean(BackupWork.KEY_INCLUDE_FONTS, options.includeFonts)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag(BackupWork.TAG_EXPORT)
                .build()
            workManager.enqueueUniqueWork(exportName(operationId), ExistingWorkPolicy.KEEP, request)
        } catch (error: Throwable) {
            permissions.releaseWrite(destinationUri)
            throw error
        }
    }

    override fun enqueueRestore(operationId: String, request: RestoreRequest) {
        requireId(operationId)
        restoreRequests.save(request)
        try {
            val work = OneTimeWorkRequest.Builder(BackupRestoreWorker::class.java)
                .setId(stableWorkId(BackupWorkType.RESTORE, operationId))
                .setInputData(
                    Data.Builder()
                        .putString(BackupWork.KEY_OPERATION_ID, operationId)
                        .putString(BackupWork.KEY_STAGED_PLAN_TOKEN, request.stagedPlanToken)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag(BackupWork.TAG_RESTORE)
                .build()
            workManager.enqueueUniqueWork(restoreName(operationId), ExistingWorkPolicy.KEEP, work)
        } catch (error: Throwable) {
            restoreRequests.delete(request.stagedPlanToken)
            throw error
        }
    }

    override fun observeExport(operationId: String): Flow<BackupWorkState?> = observe(
        operationId, BackupWorkType.EXPORT, exportName(operationId),
    )

    override fun observeRestore(operationId: String): Flow<BackupWorkState?> = observe(
        operationId, BackupWorkType.RESTORE, restoreName(operationId),
    )

    override fun cancelExport(operationId: String) {
        requireId(operationId)
        workManager.cancelUniqueWork(exportName(operationId))
    }

    override fun cancelRestore(operationId: String) {
        requireId(operationId)
        workManager.cancelUniqueWork(restoreName(operationId))
    }

    private fun observe(
        operationId: String,
        type: BackupWorkType,
        uniqueName: String,
    ): Flow<BackupWorkState?> {
        requireId(operationId)
        return workManager.getWorkInfosForUniqueWorkFlow(uniqueName).map { infos ->
            infos.maxByOrNull { it.runAttemptCount }?.let { BackupWork.state(operationId, type, it) }
        }
    }

    private fun requireId(value: String) = require(value.matches(BackupWork.ID_PATTERN)) { "备份任务 ID 无效" }
    private fun exportName(id: String) = BackupWork.UNIQUE_EXPORT_PREFIX + id
    private fun restoreName(id: String) = BackupWork.UNIQUE_RESTORE_PREFIX + id
    private fun stableWorkId(type: BackupWorkType, operationId: String): UUID =
        UUID.nameUUIDFromBytes("${type.name}:$operationId".encodeToByteArray())
}
