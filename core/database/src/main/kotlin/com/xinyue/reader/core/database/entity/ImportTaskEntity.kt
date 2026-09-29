package com.xinyue.reader.core.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "import_tasks",
    indices = [
        Index(value = ["batchId"]),
        Index(value = ["batchId", "itemIndex", "attempt"], unique = true),
    ],
)
data class ImportTaskEntity(
    @PrimaryKey val id: String,
    val batchId: String,
    val itemIndex: Int,
    val totalItems: Int,
    val attempt: Int,
    val uriString: String,
    val displayName: String,
    val preferredCharsetName: String?,
    val duplicateResolution: String,
    val status: String,
    val progressPercent: Int,
    val bookId: String?,
    val existingBookId: String?,
    val existingBookTitle: String?,
    val errorMessage: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
