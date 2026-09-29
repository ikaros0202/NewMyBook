package com.xinyue.reader.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val formatVersion: Int = 1,
    val archiveType: BackupArchiveType = BackupArchiveType.BACKUP,
    val rootBookId: String? = null,
    val appVersion: String,
    val createdAtEpochMillis: Long,
    val options: BackupOptions,
    val entries: List<BackupManifestEntry>,
)

@Serializable
enum class BackupArchiveType { BACKUP, HANDOFF }

@Serializable
data class BackupOptions(
    val includeBookText: Boolean = true,
    val includeFonts: Boolean = true,
)

@Serializable
data class BackupManifestEntry(
    val path: String,
    val kind: BackupEntryKind,
    val uncompressedSize: Long,
    val sha256: String,
)

@Serializable
enum class BackupEntryKind {
    CATALOG,
    ORIGINAL_TEXT,
    NORMALIZED_TEXT,
    OFFSET_INDEX,
    CUSTOM_COVER,
    FONT,
}

data class BackupProgress(
    val phase: BackupPhase,
    val completed: Long,
    val total: Long,
    val label: String,
)

enum class BackupPhase {
    PREPARING,
    HASHING,
    WRITING,
    VALIDATING,
    PLANNING,
    SNAPSHOTTING,
    RESTORING,
    VERIFYING,
}

enum class BackupErrorCode {
    PERMISSION,
    SPACE,
    SOURCE_CHANGED,
    PROVIDER,
    INVALID_FORMAT,
    SECURITY,
    CONFLICT,
    CANCELLED,
    UNKNOWN,
}

data class BackupError(val code: BackupErrorCode, val message: String)

sealed interface BackupExportResult {
    data class Success(
        val operationId: String,
        val archiveSha256: String,
        val bytesWritten: Long,
    ) : BackupExportResult

    data object Cancelled : BackupExportResult
    data class Failure(val error: BackupError) : BackupExportResult
}

sealed interface BackupInspectResult {
    data class Success(val preview: RestorePreview) : BackupInspectResult
    data object Cancelled : BackupInspectResult
    data class Failure(val error: BackupError) : BackupInspectResult
}

@Serializable
enum class RestoreMode { MERGE, OVERWRITE }

@Serializable
enum class RestoreConflictKind {
    BOOK_ID,
    PROGRESS,
    ANNOTATION,
    GROUP,
    THEME,
    FONT,
    GLOBAL_SETTINGS,
    BOOK_SETTINGS,
    STATISTICS,
}

@Serializable
enum class RestoreConflictChoice {
    KEEP_LOCAL,
    USE_BACKUP,
    RENAME_BACKUP,
    COPY_AS_NEW,
    KEEP_BOTH,
}

@Serializable
data class RestoreConflict(
    val id: String,
    val kind: RestoreConflictKind,
    val label: String,
    val allowedChoices: Set<RestoreConflictChoice>,
    val suggestedChoice: RestoreConflictChoice,
)

@Serializable
data class RestoreConflictResolution(
    val conflictId: String,
    val choice: RestoreConflictChoice,
    val renamedValue: String? = null,
)

@Serializable
data class RestorePreview(
    val stagedPlanToken: String,
    val formatVersion: Int,
    val createdAtEpochMillis: Long,
    val options: BackupOptions,
    val newBookCount: Int,
    val duplicateBookCount: Int,
    val conflictCount: Int,
    val stagingBytes: Long,
    val publishBytes: Long,
    val snapshotBytes: Long,
    val requiredFreeBytes: Long,
    val conflicts: List<RestoreConflict>,
)

@Serializable
data class RestoreRequest(
    val stagedPlanToken: String,
    val mode: RestoreMode,
    val resolutions: List<RestoreConflictResolution>,
)

sealed interface RestoreResult {
    data class Success(
        val restoredCount: Int,
        val skippedCount: Int,
        val conflictCopyCount: Int,
        val metadataOnlyBookCount: Int,
    ) : RestoreResult

    data object Cancelled : RestoreResult
    data class Failure(val error: BackupError) : RestoreResult
}
