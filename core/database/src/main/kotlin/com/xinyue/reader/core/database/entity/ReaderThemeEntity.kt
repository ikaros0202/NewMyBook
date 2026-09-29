package com.xinyue.reader.core.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "reader_themes",
    indices = [Index(value = ["name"], unique = true)],
)
data class ReaderThemeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val settingsJson: String,
    val builtIn: Boolean,
    val updatedAtEpochMillis: Long,
)
