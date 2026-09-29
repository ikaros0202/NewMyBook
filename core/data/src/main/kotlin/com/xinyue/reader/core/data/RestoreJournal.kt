package com.xinyue.reader.core.data

import kotlinx.serialization.Serializable

@Serializable
enum class RestorePhase {
    PREPARED,
    FILES_PUBLISHED,
    DATABASE_COMMITTED,
    VERIFIED,
    CLEANUP_PENDING,
    COMPLETED,
}

@Serializable
data class PublishedMove(
    val targetRelativePath: String,
    val replacedExisting: Boolean,
)

@Serializable
data class RestoreJournal(
    val operationId: String,
    val phase: RestorePhase,
    val stagedRoot: String,
    val snapshotRoot: String,
    val intendedTargets: List<String>,
    val publishedMoves: List<PublishedMove>,
    val expectedCatalogSha256: String,
    val request: com.xinyue.reader.core.domain.model.RestoreRequest? = null,
)
