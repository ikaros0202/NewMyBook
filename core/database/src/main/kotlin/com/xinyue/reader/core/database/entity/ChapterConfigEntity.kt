package com.xinyue.reader.core.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "chapter_configs",
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
data class ChapterConfigEntity(
    val bookId: String,
    val ruleSet: String,
    val manuallyEdited: Boolean,
    val updatedAtEpochMillis: Long,
)
