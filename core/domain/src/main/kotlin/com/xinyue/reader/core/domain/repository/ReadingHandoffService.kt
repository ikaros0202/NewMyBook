package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.BackupProgress
import com.xinyue.reader.core.domain.model.HandoffExportRequest
import com.xinyue.reader.core.domain.model.HandoffExportResult
import com.xinyue.reader.core.domain.model.HandoffBookSummary
import com.xinyue.reader.core.domain.model.HandoffImportRequest
import com.xinyue.reader.core.domain.model.HandoffImportResult
import com.xinyue.reader.core.domain.model.HandoffInspectResult

/**
 * Purely local one-book reading-state transfer boundary.
 *
 * Implementations must validate and stage an unencrypted archive before publishing it. A handoff
 * without book text may bind only to an existing book with an exact normalized-content SHA-256.
 */
interface ReadingHandoffService {
    suspend fun listBooks(): List<HandoffBookSummary> = emptyList()

    suspend fun export(
        destinationUri: String,
        request: HandoffExportRequest,
        onProgress: (BackupProgress) -> Unit = {},
    ): HandoffExportResult

    suspend fun inspect(
        sourceUri: String,
        onProgress: (BackupProgress) -> Unit = {},
    ): HandoffInspectResult

    suspend fun import(
        request: HandoffImportRequest,
        onProgress: (BackupProgress) -> Unit = {},
    ): HandoffImportResult

    suspend fun discard(stagedPlanToken: String): Boolean = false
}
