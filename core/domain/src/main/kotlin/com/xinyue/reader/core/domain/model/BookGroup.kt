package com.xinyue.reader.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BookCollection(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class BookCollectionMembership(
    val bookId: String,
    val collectionId: String,
)

/** Compatibility name for source callers while the V1.5 UI adopts “集合”. */
typealias BookGroup = BookCollection
