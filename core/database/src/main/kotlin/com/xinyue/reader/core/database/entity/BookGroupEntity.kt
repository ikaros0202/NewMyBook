package com.xinyue.reader.core.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "book_groups",
    indices = [Index(value = ["name"], unique = true)],
)
data class BookGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
