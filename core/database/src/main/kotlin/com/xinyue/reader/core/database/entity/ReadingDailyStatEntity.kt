package com.xinyue.reader.core.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "reading_daily_stats",
    primaryKeys = ["bookId", "localEpochDay"],
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
        Index("localEpochDay"),
    ],
)
data class ReadingDailyStatEntity(
    val bookId: String,
    val localEpochDay: Long,
    val activeMillis: Long,
    val sessionCount: Long,
)
