package com.xinyue.reader.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@HiltWorker
class BookFileCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val cleanup: PendingBookFileCleanup,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = try {
        if (cleanup.cleanAll()) Result.success() else Result.retry()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        Result.retry()
    }
}

interface BookFileCleanupScheduler {
    fun enqueue()
}

@Singleton
class WorkManagerBookFileCleanupScheduler @Inject constructor(
    private val workManager: WorkManager,
) : BookFileCleanupScheduler {
    override fun enqueue() = BookFileCleanupWork.enqueue(workManager)
}

object BookFileCleanupWork {
    private const val UNIQUE_WORK_NAME = "xinyue_pending_book_file_cleanup"

    fun enqueue(workManager: WorkManager) {
        val request = OneTimeWorkRequest.Builder(BookFileCleanupWorker::class.java).build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
