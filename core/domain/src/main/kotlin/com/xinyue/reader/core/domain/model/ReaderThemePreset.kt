package com.xinyue.reader.core.domain.model

import kotlinx.serialization.Serializable

const val BUILT_IN_PAPER_ID = "built-in-paper"
const val BUILT_IN_SEPIA_ID = "built-in-sepia"
const val BUILT_IN_GREEN_ID = "built-in-green"
const val BUILT_IN_DARK_ID = "built-in-dark"
const val BUILT_IN_OLED_ID = "built-in-oled"

@Serializable
data class ReaderThemePreset(
    val id: String,
    val name: String,
    val settings: ReaderSettings,
    val builtIn: Boolean,
    val updatedAtEpochMillis: Long,
)
