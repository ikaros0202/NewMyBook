package com.xinyue.reader.core.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("bookId"),
        Index(value = ["bookId", "kind"]),
        Index(value = ["bookId", "startOffset"]),
    ],
)
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val kind: String,
    val startOffset: Long,
    val endOffset: Long,
    val prefix: String,
    val suffix: String,
    val selectedSha256: String?,
    val color: String?,
    val note: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
