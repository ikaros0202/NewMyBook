package com.xinyue.reader.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.xinyue.reader.core.domain.model.BackupErrorCode
import com.xinyue.reader.core.domain.model.RestoreResult
import com.xinyue.reader.core.domain.repository.BackupService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException

@HiltWorker
class BackupRestoreWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val backupService: BackupService,
    private val recovery: RestoreRecovery,
    private val requests: RestoreRequestStore,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val operationId = inputData.getString(BackupWork.KEY_OPERATION_ID)
            ?.takeIf { it.matches(BackupWork.ID_PATTERN) }
            ?: return Result.failure(BackupWork.error(BackupErrorCode.INVALID_FORMAT))
        val token = inputData.getString(BackupWork.KEY_STAGED_PLAN_TOKEN)
            ?.takeIf { it.matches(BackupWork.ID_PATTERN) }
            ?: return Result.failure(BackupWork.error(BackupErrorCode.INVALID_FORMAT))
        val request = requests.load(token)
            ?: return Result.failure(BackupWork.error(BackupErrorCode.CONFLICT))
        var retry = false
        var completed = 0L
        return try {
            recovery.reconcile()
            when (val result = backupService.restore(request) { progress ->
                val mapped = BackupWork.progress(progress, completed)
                completed = mapped.getLong(BackupWork.KEY_COMPLETED, completed)
                setProgressAsync(mapped)
            }) {
                is RestoreResult.Success -> Result.success(
                    Data.Builder()
                        .putString(BackupWork.KEY_RESULT_CODE, "SUCCESS")
                        .putInt(BackupWork.KEY_RESTORED_COUNT, result.restoredCount)
                        .putInt(BackupWork.KEY_SKIPPED_COUNT, result.skippedCount)
                        .putInt(BackupWork.KEY_CONFLICT_COPY_COUNT, result.conflictCopyCount)
                        .putInt(BackupWork.KEY_METADATA_ONLY_BOOK_COUNT, result.metadataOnlyBookCount)
                        .build(),
                )
                is RestoreResult.Failure -> {
                    retry = result.error.code == BackupErrorCode.PROVIDER && runAttemptCount < BackupWork.MAX_RETRIES
                    if (retry) Result.retry() else Result.failure(BackupWork.error(result.error.code))
                }
                RestoreResult.Cancelled -> Result.failure(BackupWork.error(BackupErrorCode.CANCELLED))
            }
        } catch (cancelled: CancellationException) {
            backupService.discardRestorePlan(token)
            throw cancelled
        } catch (_: IOException) {
            retry = runAttemptCount < BackupWork.MAX_RETRIES
            if (retry) Result.retry() else Result.failure(BackupWork.error(BackupErrorCode.PROVIDER))
        } catch (_: SecurityException) {
            Result.failure(BackupWork.error(BackupErrorCode.PERMISSION))
        } catch (_: Throwable) {
            Result.failure(BackupWork.error(BackupErrorCode.UNKNOWN))
        } finally {
            if (!retry) {
                requests.delete(token)
                backupService.discardRestorePlan(token)
            }
        }
    }
}
