package com.xinyue.reader.core.data

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.xinyue.reader.core.domain.model.BackupErrorCode
import com.xinyue.reader.core.domain.model.HandoffExportRequest
import com.xinyue.reader.core.domain.model.HandoffExportResult
import com.xinyue.reader.core.domain.repository.ReadingHandoffService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException

@HiltWorker
class HandoffExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val service: ReadingHandoffService,
    private val permissions: BackupUriPermissionManager,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val destination = inputData.getString(HandoffWork.KEY_DESTINATION_URI)
            ?.takeIf(::validContentUri)
            ?: return Result.failure(BackupWork.error(BackupErrorCode.INVALID_FORMAT))
        val bookId = inputData.getString(HandoffWork.KEY_BOOK_ID)
            ?.takeIf { it.matches(BackupWork.ID_PATTERN) }
            ?: return Result.failure(BackupWork.error(BackupErrorCode.INVALID_FORMAT))
        if (HandoffWork.KEY_INCLUDE_BOOK_TEXT !in inputData.keyValueMap) {
            return Result.failure(BackupWork.error(BackupErrorCode.INVALID_FORMAT))
        }
        var retry = false
        var completed = 0L
        return try {
            when (val result = service.export(
                destination,
                HandoffExportRequest(
                    bookId,
                    inputData.getBoolean(HandoffWork.KEY_INCLUDE_BOOK_TEXT, false),
                ),
            ) { progress ->
                val mapped = HandoffWork.progress(progress, completed)
                completed = mapped.getLong(BackupWork.KEY_COMPLETED, completed)
                setProgressAsync(mapped)
            }) {
                is HandoffExportResult.Success -> Result.success(
                    Data.Builder()
                        .putString(BackupWork.KEY_RESULT_CODE, "SUCCESS")
                        .putString(BackupWork.KEY_ARCHIVE_SHA256, result.archiveSha256)
                        .putLong(BackupWork.KEY_BYTES_WRITTEN, result.bytesWritten)
                        .build(),
                )
                is HandoffExportResult.Failure -> {
                    retry = result.error.code == BackupErrorCode.PROVIDER &&
                        runAttemptCount < BackupWork.MAX_RETRIES
                    if (retry) Result.retry() else Result.failure(BackupWork.error(result.error.code))
                }
                HandoffExportResult.Cancelled ->
                    Result.failure(BackupWork.error(BackupErrorCode.CANCELLED))
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
