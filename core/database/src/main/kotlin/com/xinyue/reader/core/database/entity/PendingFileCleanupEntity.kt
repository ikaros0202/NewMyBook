package com.xinyue.reader.core.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "pending_file_cleanup")
data class PendingFileCleanupEntity(
    @PrimaryKey val bookId: String,
    val originalPath: String,
    val normalizedPath: String,
    val queuedAtEpochMillis: Long,
)
