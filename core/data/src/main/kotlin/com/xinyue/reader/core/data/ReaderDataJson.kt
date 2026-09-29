package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.ReaderThemeManualOverride
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val readerDataJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
internal data class StoredReaderThemeSchedule(
    val schedule: ReaderThemeSchedule = ReaderThemeSchedule(),
    val manualOverride: ReaderThemeManualOverride? = null,
)

internal inline fun <reified T> Json.decodeOrDefault(encoded: String, default: T): T =
    runCatching { decodeFromString<T>(encoded) }.getOrDefault(default)
