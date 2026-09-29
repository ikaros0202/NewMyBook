package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.BackupExportResult
import com.xinyue.reader.core.domain.model.BackupInspectResult
import com.xinyue.reader.core.domain.model.BackupOptions
import com.xinyue.reader.core.domain.model.BackupProgress
import com.xinyue.reader.core.domain.model.RestoreRequest
import com.xinyue.reader.core.domain.model.RestoreResult

/**
 * Safe local backup boundary.
 *
 * [inspect] validates and stages an archive without mutating the current library. [restore] accepts
 * only a request derived from that preview. Implementations return modeled operational failures and
 * must coordinate staging, snapshots, durable journals, publication, verification, and cleanup.
 */
interface BackupService {
    /**
     * Exports a stable application snapshot to [destinationUri].
     * A cancelled or failed destination attempt must be invalidated when possible, and temporary files
     * must not survive the operation. Progress labels are diagnostic and must not expose private content.
     */
    suspend fun export(
        destinationUri: String,
        options: BackupOptions,
        onProgress: (BackupProgress) -> Unit = {},
    ): BackupExportResult

    /**
     * Copies, validates, and stages [sourceUri], returning a preview with a one-plan staging token.
     * Success does not change the database or published private files; invalid or cancelled staging is cleaned.
     */
    suspend fun inspect(
        sourceUri: String,
        onProgress: (BackupProgress) -> Unit = {},
    ): BackupInspectResult

    /**
     * Publishes the staged plan named by [request], applying its mode and explicit conflict resolutions.
     * Missing or stale tokens return a conflict failure instead of re-reading or trusting the source archive.
     */
    suspend fun restore(
        request: RestoreRequest,
        onProgress: (BackupProgress) -> Unit = {},
    ): RestoreResult

    /**
     * Releases a preview's staged private data without publishing it.
     * The production implementation is idempotent and treats a missing token as already discarded;
     * the default `false` result marks older or test implementations that do not support explicit cleanup.
     */
    suspend fun discardRestorePlan(stagedPlanToken: String): Boolean = false
}
