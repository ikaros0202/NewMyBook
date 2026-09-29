package com.xinyue.reader.core.data

import androidx.work.Data
import androidx.work.WorkInfo
import com.xinyue.reader.core.domain.model.BackupErrorCode
import com.xinyue.reader.core.domain.model.BackupPhase
import com.xinyue.reader.core.domain.model.BackupProgress

enum class BackupWorkType { EXPORT, RESTORE }
enum class BackupWorkStatus { ENQUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, BLOCKED }

data class BackupWorkState(
    val operationId: String,
    val type: BackupWorkType,
    val status: BackupWorkStatus,
    val phase: BackupPhase? = null,
    val completed: Long = 0,
    val total: Long = 0,
    val errorCode: BackupErrorCode? = null,
    val restoredCount: Int = 0,
    val skippedCount: Int = 0,
    val conflictCopyCount: Int = 0,
    val metadataOnlyBookCount: Int = 0,
)

internal object BackupWork {
    const val KEY_OPERATION_ID = "operation_id"
    const val KEY_DESTINATION_URI = "destination_uri"
    const val KEY_INCLUDE_BOOK_TEXT = "include_book_text"
    const val KEY_INCLUDE_FONTS = "include_fonts"
    const val KEY_STAGED_PLAN_TOKEN = "staged_plan_token"
    const val KEY_PHASE = "phase"
    const val KEY_COMPLETED = "completed"
    const val KEY_TOTAL = "total"
    const val KEY_LABEL = "label"
    const val KEY_RESULT_CODE = "result_code"
    const val KEY_ARCHIVE_SHA256 = "archive_sha256"
    const val KEY_BYTES_WRITTEN = "bytes_written"
    const val KEY_RESTORED_COUNT = "restored_count"
    const val KEY_SKIPPED_COUNT = "skipped_count"
    const val KEY_CONFLICT_COPY_COUNT = "conflict_copy_count"
    const val KEY_METADATA_ONLY_BOOK_COUNT = "metadata_only_book_count"
    const val TAG_EXPORT = "xinyue_backup_export"
    const val TAG_RESTORE = "xinyue_backup_restore"
    const val UNIQUE_EXPORT_PREFIX = "xinyue_backup_export_"
    const val UNIQUE_RESTORE_PREFIX = "xinyue_backup_restore_"
    const val MAX_RETRIES = 2
    val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")

    fun progress(value: BackupProgress, previousCompleted: Long = 0): Data {
        val completed = maxOf(previousCompleted, value.completed.coerceAtLeast(0))
        val total = maxOf(completed, value.total.coerceAtLeast(0))
        return Data.Builder()
            .putInt(KEY_PHASE, value.phase.ordinal)
            .putLong(KEY_COMPLETED, completed)
            .putLong(KEY_TOTAL, total)
            .putString(KEY_LABEL, genericLabel(value.phase))
            .build()
    }

    fun error(code: BackupErrorCode): Data = Data.Builder()
        .putString(KEY_RESULT_CODE, code.name)
        .build()

    fun genericLabel(phase: BackupPhase): String = when (phase) {
        BackupPhase.PREPARING -> "preparing"
        BackupPhase.HASHING -> "hashing"
        BackupPhase.WRITING -> "writing"
        BackupPhase.VALIDATING -> "validating"
        BackupPhase.PLANNING -> "planning"
        BackupPhase.SNAPSHOTTING -> "snapshotting"
        BackupPhase.RESTORING -> "restoring"
        BackupPhase.VERIFYING -> "verifying"
    }

    fun state(operationId: String, type: BackupWorkType, info: WorkInfo): BackupWorkState {
        val progress = info.progress
        val output = info.outputData
        val phaseOrdinal = progress.getInt(KEY_PHASE, -1)
        return BackupWorkState(
            operationId = operationId,
            type = type,
            status = when (info.state) {
                WorkInfo.State.ENQUEUED -> BackupWorkStatus.ENQUEUED
                WorkInfo.State.RUNNING -> BackupWorkStatus.RUNNING
                WorkInfo.State.SUCCEEDED -> BackupWorkStatus.SUCCEEDED
                WorkInfo.State.FAILED -> BackupWorkStatus.FAILED
                WorkInfo.State.CANCELLED -> BackupWorkStatus.CANCELLED
                WorkInfo.State.BLOCKED -> BackupWorkStatus.BLOCKED
            },
            phase = BackupPhase.entries.getOrNull(phaseOrdinal),
            completed = progress.getLong(KEY_COMPLETED, 0),
            total = progress.getLong(KEY_TOTAL, 0),
            errorCode = output.getString(KEY_RESULT_CODE)?.let { runCatching { BackupErrorCode.valueOf(it) }.getOrNull() },
            restoredCount = output.getInt(KEY_RESTORED_COUNT, 0),
            skippedCount = output.getInt(KEY_SKIPPED_COUNT, 0),
            conflictCopyCount = output.getInt(KEY_CONFLICT_COPY_COUNT, 0),
            metadataOnlyBookCount = output.getInt(KEY_METADATA_ONLY_BOOK_COUNT, 0),
        )
    }
}
