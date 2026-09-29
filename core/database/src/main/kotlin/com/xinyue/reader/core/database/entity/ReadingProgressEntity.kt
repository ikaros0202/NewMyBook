package com.xinyue.reader.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "reading_progress",
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
data class ReadingProgressEntity(
    val bookId: String,
    val offset: Long,
    val contextHash: String,
    @ColumnInfo(defaultValue = "''")
    val prefix: String = "",
    @ColumnInfo(defaultValue = "''")
    val suffix: String = "",
    val contentLength: Long,
    val updatedAtEpochMillis: Long,
)
