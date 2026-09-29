package com.xinyue.reader.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ImportedFont(
    val id: String,
    val displayName: String,
    val contentSha256: String,
    val sizeBytes: Long,
    val createdAtEpochMillis: Long,
)

sealed interface FontRemovalResult {
    data object Removed : FontRemovalResult
    data object NotFound : FontRemovalResult
    data class InUse(val referenceCount: Int) : FontRemovalResult
}
