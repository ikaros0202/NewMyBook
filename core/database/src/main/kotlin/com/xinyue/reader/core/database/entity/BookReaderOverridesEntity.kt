package com.xinyue.reader.core.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "book_reader_overrides",
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
data class BookReaderOverridesEntity(
    val bookId: String,
    val overridesJson: String,
    val updatedAtEpochMillis: Long,
)
