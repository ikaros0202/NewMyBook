package com.xinyue.reader.core.data

import androidx.work.Data
import androidx.work.WorkInfo
import com.xinyue.reader.core.domain.model.BackupErrorCode
import com.xinyue.reader.core.domain.model.BackupPhase
import com.xinyue.reader.core.domain.model.BackupProgress

enum class HandoffWorkType { EXPORT, IMPORT }

data class HandoffWorkState(
    val operationId: String,
    val type: HandoffWorkType,
    val status: BackupWorkStatus,
    val phase: BackupPhase? = null,
    val completed: Long = 0,
    val total: Long = 0,
    val errorCode: BackupErrorCode? = null,
    val importedNewBook: Boolean = false,
    val appliedCount: Int = 0,
    val skippedCount: Int = 0,
    val conflictCopyCount: Int = 0,
)

internal object HandoffWork {
    const val KEY_OPERATION_ID = "handoff_operation_id"
    const val KEY_DESTINATION_URI = "handoff_destination_uri"
    const val KEY_BOOK_ID = "handoff_book_id"
    const val KEY_INCLUDE_BOOK_TEXT = "handoff_include_book_text"
    const val KEY_STAGED_PLAN_TOKEN = "handoff_staged_plan_token"
    const val KEY_IMPORTED_NEW_BOOK = "handoff_imported_new_book"
    const val KEY_APPLIED_COUNT = "handoff_applied_count"
    const val TAG_EXPORT = "xinyue_handoff_export"
    const val TAG_IMPORT = "xinyue_handoff_import"
    const val UNIQUE_EXPORT_PREFIX = "xinyue_handoff_export_"
    const val UNIQUE_IMPORT_PREFIX = "xinyue_handoff_import_"

    fun progress(value: BackupProgress, previousCompleted: Long = 0): Data =
        BackupWork.progress(value, previousCompleted)

    fun state(operationId: String, type: HandoffWorkType, info: WorkInfo): HandoffWorkState {
        val progress = info.progress
        val output = info.outputData
        val phaseOrdinal = progress.getInt(BackupWork.KEY_PHASE, -1)
        return HandoffWorkState(
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
            completed = progress.getLong(BackupWork.KEY_COMPLETED, 0),
            total = progress.getLong(BackupWork.KEY_TOTAL, 0),
            errorCode = output.getString(BackupWork.KEY_RESULT_CODE)
                ?.let { runCatching { BackupErrorCode.valueOf(it) }.getOrNull() },
            importedNewBook = output.getBoolean(KEY_IMPORTED_NEW_BOOK, false),
            appliedCount = output.getInt(KEY_APPLIED_COUNT, 0),
            skippedCount = output.getInt(BackupWork.KEY_SKIPPED_COUNT, 0),
            conflictCopyCount = output.getInt(BackupWork.KEY_CONFLICT_COPY_COUNT, 0),
        )
    }
}
