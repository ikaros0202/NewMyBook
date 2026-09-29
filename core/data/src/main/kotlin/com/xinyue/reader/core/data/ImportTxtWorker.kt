package com.xinyue.reader.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.xinyue.reader.core.database.dao.ImportTaskDao
import com.xinyue.reader.core.database.entity.ImportTaskEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException

@HiltWorker
class ImportTxtWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val taskDao: ImportTaskDao,
    private val sourceFactory: ImportSourceFactory,
    private val importer: ImportBookUseCase,
    private val executionGate: ImportExecutionGate,
    private val permissionManager: ImportUriPermissionManager,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString(ImportWorkContract.KEY_TASK_ID)
            ?: return Result.failure(errorData("导入任务参数缺失"))
        val initial = taskDao.get(taskId)
            ?: return Result.failure(errorData("找不到导入任务"))
        var task = initial.copy(
            status = ImportItemStatus.RUNNING.name,
            progressPercent = 5,
            errorMessage = null,
        )
        update(task)

        return try {
            val source = sourceFactory.create(initial.uriString)
            require(source.displayName.endsWith(".txt", ignoreCase = true)) { "请选择 TXT 文件" }
            task = task.copy(
                displayName = source.displayName,
                status = ImportItemStatus.RUNNING.name,
                progressPercent = 20,
                errorMessage = null,
            )
            update(task)
            val resolution = runCatching { DuplicateResolution.valueOf(initial.duplicateResolution) }
                .getOrDefault(DuplicateResolution.ASK)
            val outcome = executionGate.run {
                importer.importReliably(
                    source = source,
                    preferredCharsetName = initial.preferredCharsetName,
                    duplicateResolution = resolution,
                )
            }
            task = when (outcome) {
                is ReliableImportOutcome.Imported -> task.copy(
                    status = ImportItemStatus.SUCCEEDED.name,
                    progressPercent = 100,
                    bookId = outcome.book.id,
                )
                is ReliableImportOutcome.Skipped -> task.copy(
                    status = ImportItemStatus.SKIPPED.name,
                    progressPercent = 100,
                    bookId = outcome.existingBook.id,
                    existingBookId = outcome.existingBook.id,
                    existingBookTitle = outcome.existingBook.title,
                )
            }
            update(task)
            permissionManager.releaseRead(task.uriString)
            Result.success()
        } catch (duplicate: DuplicateBookException) {
            update(
                task.copy(
                    status = ImportItemStatus.NEEDS_DECISION.name,
                    progressPercent = 100,
                    existingBookId = duplicate.existingBook.id,
                    existingBookTitle = duplicate.existingBook.title,
                    errorMessage = "《${duplicate.existingBook.title}》内容相同，请选择处理方式",
                ),
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            update(task.copy(status = ImportItemStatus.QUEUED.name, errorMessage = null))
            throw cancelled
        } catch (error: Throwable) {
            if (error is IOException && runAttemptCount < MAX_IO_RETRIES) {
                update(
                    task.copy(
                        status = ImportItemStatus.QUEUED.name,
                        errorMessage = "暂时无法读取所选文件，稍后将自动重试",
                    ),
                )
                Result.retry()
            } else {
                update(
                    task.copy(
                        status = ImportItemStatus.FAILED.name,
                        errorMessage = "导入失败，请检查文件后重试",
                    ),
                )
                permissionManager.releaseRead(task.uriString)
                Result.failure(errorData("导入失败，请检查文件后重试"))
            }
        }
    }

    private suspend fun update(task: ImportTaskEntity) {
        val updated = task.copy(updatedAtEpochMillis = System.currentTimeMillis())
        taskDao.update(updated)
        setProgress(Data.Builder().putInt(KEY_PROGRESS, updated.progressPercent).build())
    }

    private fun errorData(message: String): Data = Data.Builder().putString(KEY_ERROR, message).build()

    private companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val MAX_IO_RETRIES = 2
    }
}
