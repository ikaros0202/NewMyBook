package com.xinyue.reader.core.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "reader_global_settings")
data class ReaderGlobalSettingsEntity(
    @PrimaryKey val id: Int = GLOBAL_ROW_ID,
    val settingsJson: String,
    val scheduleJson: String,
    val updatedAtEpochMillis: Long,
) {
    companion object {
        const val GLOBAL_ROW_ID = 0
    }
}
