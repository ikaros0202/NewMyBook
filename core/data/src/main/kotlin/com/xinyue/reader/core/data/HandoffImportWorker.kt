package com.xinyue.reader.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.xinyue.reader.core.domain.model.BackupErrorCode
import com.xinyue.reader.core.domain.model.HandoffImportResult
import com.xinyue.reader.core.domain.repository.ReadingHandoffService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException

@HiltWorker
class HandoffImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val service: ReadingHandoffService,
    private val requests: HandoffImportRequestStore,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val token = inputData.getString(HandoffWork.KEY_STAGED_PLAN_TOKEN)
            ?.takeIf { it.matches(BackupWork.ID_PATTERN) }
            ?: return Result.failure(BackupWork.error(BackupErrorCode.INVALID_FORMAT))
        val request = requests.load(token)
            ?: return Result.failure(BackupWork.error(BackupErrorCode.CONFLICT))
        var retry = false
        var completed = 0L
        return try {
            when (val result = service.import(request) { progress ->
                val mapped = HandoffWork.progress(progress, completed)
                completed = mapped.getLong(BackupWork.KEY_COMPLETED, completed)
                setProgressAsync(mapped)
            }) {
                is HandoffImportResult.Success -> Result.success(
                    Data.Builder()
                        .putString(BackupWork.KEY_RESULT_CODE, "SUCCESS")
                        .putBoolean(HandoffWork.KEY_IMPORTED_NEW_BOOK, result.importedNewBook)
                        .putInt(HandoffWork.KEY_APPLIED_COUNT, result.appliedCount)
                        .putInt(BackupWork.KEY_SKIPPED_COUNT, result.skippedCount)
                        .putInt(BackupWork.KEY_CONFLICT_COPY_COUNT, result.conflictCopyCount)
                        .build(),
                )
                is HandoffImportResult.Failure -> {
                    retry = result.error.code == BackupErrorCode.PROVIDER &&
                        runAttemptCount < BackupWork.MAX_RETRIES
                    if (retry) Result.retry() else Result.failure(BackupWork.error(result.error.code))
                }
                HandoffImportResult.Cancelled ->
                    Result.failure(BackupWork.error(BackupErrorCode.CANCELLED))
            }
        } catch (cancelled: CancellationException) {
            service.discard(token)
            throw cancelled
        } catch (_: IOException) {
            retry = runAttemptCount < BackupWork.MAX_RETRIES
            if (retry) Result.retry() else Result.failure(BackupWork.error(BackupErrorCode.PROVIDER))
        } catch (_: Throwable) {
            Result.failure(BackupWork.error(BackupErrorCode.UNKNOWN))
        } finally {
            if (!retry) {
                requests.delete(token)
                service.discard(token)
            }
        }
    }
}
