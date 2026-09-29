package com.xinyue.reader.core.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "imported_fonts",
    indices = [Index(value = ["contentSha256"], unique = true)],
)
data class ImportedFontEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val privateRelativePath: String,
    val contentSha256: String,
    val sizeBytes: Long,
    val createdAtEpochMillis: Long,
)
