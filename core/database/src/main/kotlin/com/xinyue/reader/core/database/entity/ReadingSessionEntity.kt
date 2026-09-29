package com.xinyue.reader.core.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "reading_sessions",
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
        Index("endedAtEpochMillis"),
    ],
)
data class ReadingSessionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val startedAtEpochMillis: Long,
    val lastInteractionAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val activeMillis: Long,
)
