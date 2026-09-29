package com.xinyue.reader.core.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "search_index_states",
    primaryKeys = ["bookId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class SearchIndexStateEntity(
    val bookId: String,
    val activeGenerationId: String?,
    val buildingGenerationId: String?,
    val status: String,
    val contentSha256: String,
    val indexedUtf16Length: Long,
    val updatedAtEpochMillis: Long,
    val errorMessage: String?,
)
