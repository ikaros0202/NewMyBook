package com.xinyue.reader.core.data

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.xinyue.reader.core.database.dao.ImportTaskDao
import com.xinyue.reader.core.database.entity.ImportTaskEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class WorkManagerImportTaskScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val taskDao: ImportTaskDao,
    private val permissionManager: ImportUriPermissionManager,
) : ImportTaskScheduler {
    override val latestBatch: Flow<ImportBatchState?> = combine(
        taskDao.observeLatestBatch(),
        workManager.getWorkInfosByTagFlow(ImportWorkContract.TAG_IMPORT),
    ) { tasks, workInfos ->
        val workById = workInfos.associateBy { it.id.toString() }
        reduceImportWorkSnapshots(
            tasks.map { task -> task.toSnapshot().withExecutorState(workById[task.id]) },
        )
    }

    override suspend fun enqueueRequests(requests: List<ImportRequest>): String {
        require(requests.isNotEmpty()) { "请至少选择一个 TXT 文件" }
        val batchId = newBatchId()
        val now = System.currentTimeMillis()
        val tasks = requests.mapIndexed { index, request ->
            newTask(
                batchId = batchId,
                index = index,
                total = requests.size,
                attempt = 0,
                uriString = request.uriString,
                preferredCharsetName = request.preferredCharsetName,
                duplicateResolution = DuplicateResolution.ASK,
                now = now,
            )
        }
        taskDao.insertAll(tasks)
        for (task in tasks) {
            persistAndEnqueue(task)
        }
        return batchId
    }

    override suspend fun resolveDuplicate(item: ImportItemState, resolution: DuplicateResolution) {
        require(resolution != DuplicateResolution.ASK) { "请选择重复文件处理方式" }
        enqueueAttempt(item, resolution)
    }

    override suspend fun retry(item: ImportItemState) {
        enqueueAttempt(item, item.duplicateResolution)
    }

    private suspend fun enqueueAttempt(item: ImportItemState, resolution: DuplicateResolution) {
        val now = System.currentTimeMillis()
        val task = newTask(
            batchId = item.batchId,
            index = item.index,
            total = item.total,
            attempt = item.attempt + 1,
            uriString = item.uriString,
            preferredCharsetName = item.preferredCharsetName,
            duplicateResolution = resolution,
            now = now,
        ).copy(displayName = item.displayName)
        taskDao.insertAll(listOf(task))
        persistAndEnqueue(task)
    }

    private suspend fun persistAndEnqueue(task: ImportTaskEntity) {
        try {
            permissionManager.persistRead(task.uriString)
            val request = OneTimeWorkRequest.Builder(ImportTxtWorker::class.java)
                .setId(UUID.fromString(task.id))
                .setInputData(Data.Builder().putString(ImportWorkContract.KEY_TASK_ID, task.id).build())
                .addTag(ImportWorkContract.TAG_IMPORT)
                .addTag("${ImportWorkContract.TAG_BATCH_PREFIX}${task.batchId}")
                .build()
            workManager.enqueueUniqueWork(
                "${ImportWorkContract.UNIQUE_ITEM_PREFIX}${task.id}",
                ExistingWorkPolicy.KEEP,
                request,
            )
        } catch (error: Throwable) {
            taskDao.update(
                task.copy(
                    status = ImportItemStatus.FAILED.name,
                    errorMessage = "无法保留所选文件的读取权限",
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun newTask(
        batchId: String,
        index: Int,
        total: Int,
        attempt: Int,
        uriString: String,
        preferredCharsetName: String?,
        duplicateResolution: DuplicateResolution,
        now: Long,
    ): ImportTaskEntity = ImportTaskEntity(
        id = UUID.randomUUID().toString(),
        batchId = batchId,
        itemIndex = index,
        totalItems = total,
        attempt = attempt,
        uriString = uriString,
        displayName = uriString.substringAfterLast('/').ifBlank { "未命名小说.txt" },
        preferredCharsetName = preferredCharsetName,
        duplicateResolution = duplicateResolution.name,
        status = ImportItemStatus.QUEUED.name,
        progressPercent = 0,
        bookId = null,
        existingBookId = null,
        existingBookTitle = null,
        errorMessage = null,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
    )

    private fun newBatchId(): String = "%013d-%s".format(System.currentTimeMillis(), UUID.randomUUID())
}

private fun ImportTaskEntity.toSnapshot() = ImportWorkSnapshot(
    workId = id,
    batchId = batchId,
    index = itemIndex,
    total = totalItems,
    attempt = attempt,
    uriString = uriString,
    displayName = displayName,
    preferredCharsetName = preferredCharsetName,
    duplicateResolution = runCatching { DuplicateResolution.valueOf(duplicateResolution) }
        .getOrDefault(DuplicateResolution.ASK),
    status = runCatching { ImportItemStatus.valueOf(status) }.getOrDefault(ImportItemStatus.FAILED),
    progressPercent = progressPercent,
    bookId = bookId,
    existingBookId = existingBookId,
    existingBookTitle = existingBookTitle,
    errorMessage = errorMessage,
)

private fun ImportWorkSnapshot.withExecutorState(workInfo: WorkInfo?): ImportWorkSnapshot {
    if (workInfo == null || status !in setOf(ImportItemStatus.QUEUED, ImportItemStatus.RUNNING)) return this
    return when (workInfo.state) {
        WorkInfo.State.FAILED -> copy(
            status = ImportItemStatus.FAILED,
            errorMessage = errorMessage ?: "后台导入任务未能启动",
        )
        WorkInfo.State.CANCELLED -> copy(
            status = ImportItemStatus.CANCELLED,
            errorMessage = errorMessage ?: "后台导入任务已取消",
        )
        else -> this
    }
}

internal object ImportWorkContract {
    const val KEY_TASK_ID = "import_task_id"
    const val TAG_IMPORT = "xinyue_import"
    const val TAG_BATCH_PREFIX = "xinyue_import_batch:"
    const val UNIQUE_ITEM_PREFIX = "xinyue_import_item:"
}
