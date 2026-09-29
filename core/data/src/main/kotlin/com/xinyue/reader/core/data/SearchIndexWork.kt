package com.xinyue.reader.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@HiltWorker
class SearchIndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val coordinator: SearchIndexCoordinator,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val bookId = inputData.getString(SearchIndexWorkContract.KEY_BOOK_ID)
            ?: return Result.failure()
        return try {
            coordinator.build(bookId)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            if (runAttemptCount < MAX_IO_RETRIES) Result.retry() else Result.failure()
        } catch (_: Throwable) {
            Result.failure()
        }
    }

    private companion object {
        const val MAX_IO_RETRIES = 2
    }
}

interface SearchIndexScheduler {
    fun ensure(bookId: String)
    fun rebuild(bookId: String)
    fun cancel(bookId: String)
}

@Singleton
class WorkManagerSearchIndexScheduler @Inject constructor(
    private val workManager: WorkManager,
) : SearchIndexScheduler {
    override fun ensure(bookId: String) = enqueue(bookId, ExistingWorkPolicy.KEEP)
    override fun rebuild(bookId: String) = enqueue(bookId, ExistingWorkPolicy.REPLACE)
    override fun cancel(bookId: String) {
        workManager.cancelUniqueWork("${SearchIndexWorkContract.UNIQUE_PREFIX}$bookId")
    }

    private fun enqueue(bookId: String, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequest.Builder(SearchIndexWorker::class.java)
            .setInputData(Data.Builder().putString(SearchIndexWorkContract.KEY_BOOK_ID, bookId).build())
            .addTag(SearchIndexWorkContract.TAG)
            .build()
        workManager.enqueueUniqueWork(
            "${SearchIndexWorkContract.UNIQUE_PREFIX}$bookId",
            policy,
            request,
        )
    }
}

internal object SearchIndexWorkContract {
    const val KEY_BOOK_ID = "book_id"
    const val TAG = "xinyue_search_index"
    const val UNIQUE_PREFIX = "xinyue_search_index:"
}
