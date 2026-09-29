package com.xinyue.reader.core.data

import kotlinx.coroutines.flow.Flow

enum class ImportItemStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    SKIPPED,
    NEEDS_DECISION,
    FAILED,
    CANCELLED,
}

data class ImportItemState(
    val workId: String,
    val batchId: String,
    val index: Int,
    val total: Int,
    val attempt: Int,
    val uriString: String,
    val displayName: String,
    val preferredCharsetName: String?,
    val duplicateResolution: DuplicateResolution,
    val status: ImportItemStatus,
    val progressPercent: Int,
    val bookId: String?,
    val existingBookId: String?,
    val existingBookTitle: String?,
    val errorMessage: String?,
)

data class ImportBatchState(
    val batchId: String,
    val items: List<ImportItemState>,
) {
    val completedCount: Int
        get() = items.count { item ->
            item.status in setOf(
                ImportItemStatus.SUCCEEDED,
                ImportItemStatus.SKIPPED,
                ImportItemStatus.NEEDS_DECISION,
                ImportItemStatus.FAILED,
                ImportItemStatus.CANCELLED,
            )
        }

    val isFinished: Boolean
        get() = items.isNotEmpty() && completedCount == items.size
}

data class ImportRequest(
    val uriString: String,
    val preferredCharsetName: String? = null,
)

interface ImportTaskScheduler {
    val latestBatch: Flow<ImportBatchState?>

    suspend fun enqueue(
        uriStrings: List<String>,
        preferredCharsetName: String? = null,
    ): String = enqueueRequests(
        uriStrings.map { uriString -> ImportRequest(uriString, preferredCharsetName) },
    )

    suspend fun enqueueRequests(requests: List<ImportRequest>): String

    suspend fun resolveDuplicate(item: ImportItemState, resolution: DuplicateResolution)

    suspend fun retry(item: ImportItemState)
}

data class ImportWorkSnapshot(
    val workId: String,
    val batchId: String,
    val index: Int,
    val total: Int,
    val attempt: Int,
    val uriString: String,
    val displayName: String,
    val preferredCharsetName: String?,
    val duplicateResolution: DuplicateResolution,
    val status: ImportItemStatus,
    val progressPercent: Int,
    val bookId: String?,
    val existingBookId: String?,
    val existingBookTitle: String?,
    val errorMessage: String?,
)

fun reduceImportWorkSnapshots(snapshots: List<ImportWorkSnapshot>): ImportBatchState? {
    val latestBatchId = snapshots.maxOfOrNull(ImportWorkSnapshot::batchId) ?: return null
    val items = snapshots.asSequence()
        .filter { it.batchId == latestBatchId }
        .groupBy(ImportWorkSnapshot::index)
        .values
        .mapNotNull { attempts -> attempts.maxByOrNull(ImportWorkSnapshot::attempt) }
        .sortedBy(ImportWorkSnapshot::index)
        .map { snapshot ->
            ImportItemState(
                workId = snapshot.workId,
                batchId = snapshot.batchId,
                index = snapshot.index,
                total = snapshot.total,
                attempt = snapshot.attempt,
                uriString = snapshot.uriString,
                displayName = snapshot.displayName,
                preferredCharsetName = snapshot.preferredCharsetName,
                duplicateResolution = snapshot.duplicateResolution,
                status = snapshot.status,
                progressPercent = snapshot.progressPercent.coerceIn(0, 100),
                bookId = snapshot.bookId,
                existingBookId = snapshot.existingBookId,
                existingBookTitle = snapshot.existingBookTitle,
                errorMessage = snapshot.errorMessage,
            )
        }
    return ImportBatchState(batchId = latestBatchId, items = items)
}
