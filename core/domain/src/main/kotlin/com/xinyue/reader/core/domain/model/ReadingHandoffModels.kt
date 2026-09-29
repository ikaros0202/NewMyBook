package com.xinyue.reader.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class HandoffExportRequest(
    val bookId: String,
    val includeBookText: Boolean = false,
)

data class HandoffBookSummary(
    val id: String,
    val title: String,
    val author: String?,
    val seriesName: String?,
)

data class HandoffPreview(
    val stagedPlanToken: String,
    val formatVersion: Int,
    val createdAtEpochMillis: Long,
    val sourceBookId: String,
    val targetBookId: String?,
    val title: String,
    val includesBookText: Boolean,
    val importsNewBook: Boolean,
    val incomingProgressIsNewer: Boolean,
    val annotationInsertCount: Int,
    val annotationConflictCopyCount: Int,
    val collectionCount: Int,
    val conflicts: List<RestoreConflict>,
)

sealed interface HandoffExportResult {
    data class Success(
        val operationId: String,
        val archiveSha256: String,
        val bytesWritten: Long,
    ) : HandoffExportResult

    data object Cancelled : HandoffExportResult
    data class Failure(val error: BackupError) : HandoffExportResult
}

sealed interface HandoffInspectResult {
    data class Success(val preview: HandoffPreview) : HandoffInspectResult
    data object Cancelled : HandoffInspectResult
    data class Failure(val error: BackupError) : HandoffInspectResult
}

@Serializable
data class HandoffImportRequest(
    val stagedPlanToken: String,
    val resolutions: List<RestoreConflictResolution>,
)

sealed interface HandoffImportResult {
    data class Success(
        val importedNewBook: Boolean,
        val appliedCount: Int,
        val skippedCount: Int,
        val conflictCopyCount: Int,
    ) : HandoffImportResult

    data object Cancelled : HandoffImportResult
    data class Failure(val error: BackupError) : HandoffImportResult
}
