package com.xinyue.reader.core.data

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.xinyue.reader.core.domain.model.BackupErrorCode
import com.xinyue.reader.core.domain.model.BackupExportResult
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.repository.BackupService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException

@HiltWorker
class BackupExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val backupService: BackupService,
    private val permissions: BackupUriPermissionManager,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val operationId = inputData.getString(BackupWork.KEY_OPERATION_ID)
            ?.takeIf { it.matches(BackupWork.ID_PATTERN) }
            ?: return Result.failure(BackupWork.error(BackupErrorCode.INVALID_FORMAT))
        val destination = inputData.getString(BackupWork.KEY_DESTINATION_URI)
            ?.takeIf(::validContentUri)
            ?: return Result.failure(BackupWork.error(BackupErrorCode.INVALID_FORMAT))
        if (
            BackupWork.KEY_INCLUDE_BOOK_TEXT !in inputData.keyValueMap ||
            BackupWork.KEY_INCLUDE_FONTS !in inputData.keyValueMap
        ) return Result.failure(BackupWork.error(BackupErrorCode.INVALID_FORMAT))
        val options = BackupOptions(
            includeBookText = inputData.getBoolean(BackupWork.KEY_INCLUDE_BOOK_TEXT, true),
            includeFonts = inputData.getBoolean(BackupWork.KEY_INCLUDE_FONTS, true),
        )
        var retry = false
        var completed = 0L
        return try {
            when (val result = backupService.export(destination, options) { progress ->
                val mapped = BackupWork.progress(progress, completed)
                completed = mapped.getLong(BackupWork.KEY_COMPLETED, completed)
                setProgressAsync(mapped)
            }) {
                is BackupExportResult.Success -> Result.success(
                    Data.Builder()
                        .putString(BackupWork.KEY_RESULT_CODE, "SUCCESS")
                        .putString(BackupWork.KEY_ARCHIVE_SHA256, result.archiveSha256)
                        .putLong(BackupWork.KEY_BYTES_WRITTEN, result.bytesWritten)
                        .build(),
                )
                is BackupExportResult.Failure -> {
                    retry = result.error.code == BackupErrorCode.PROVIDER && runAttemptCount < BackupWork.MAX_RETRIES
                    if (retry) Result.retry() else Result.failure(BackupWork.error(result.error.code))
                }
                BackupExportResult.Cancelled -> Result.failure(BackupWork.error(BackupErrorCode.CANCELLED))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            Result.failure(BackupWork.error(BackupErrorCode.PERMISSION))
        } catch (_: IOException) {
            retry = runAttemptCount < BackupWork.MAX_RETRIES
            if (retry) Result.retry() else Result.failure(BackupWork.error(BackupErrorCode.PROVIDER))
        } catch (_: Throwable) {
            Result.failure(BackupWork.error(BackupErrorCode.UNKNOWN))
        } finally {
            if (!retry) permissions.releaseWrite(destination)
        }
    }

    private fun validContentUri(value: String): Boolean = runCatching {
        val uri = Uri.parse(value)
        uri.scheme == "content" && uri.authority?.isNotBlank() == true
    }.getOrDefault(false)
}
