package com.xinyue.reader.core.database.entity

import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val originalFileName: String,
    val originalPath: String,
    val normalizedPath: String,
    val charsetName: String,
    val contentSha256: String,
    val contentLength: Long,
    val createdAtEpochMillis: Long,
    val lastOpenedAtEpochMillis: Long?,
    val seriesName: String? = null,
    val seriesOrder: Int? = null,
    @Ignore
    val groupId: String? = null,
    val customCoverPath: String? = null,
    val finished: Boolean = false,
)
