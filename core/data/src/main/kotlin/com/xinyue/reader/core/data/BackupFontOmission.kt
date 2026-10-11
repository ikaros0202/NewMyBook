package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import kotlinx.serialization.encodeToString

/** Omitted font files must not leave settings pointing at an unavailable imported font. */
internal fun BackupCatalogSnapshot.withoutImportedFonts(): BackupCatalogSnapshot {
    fun settings(encoded: String): String {
        val decoded = readerDataJson.decodeFromString<ReaderSettings>(encoded)
        return if (decoded.font is ReaderFontRef.Imported) {
            readerDataJson.encodeToString(decoded.copy(font = ReaderFontRef.System))
        } else encoded
    }
    return copy(
        fonts = emptyList(),
        fontSources = emptyList(),
        globalSettings = globalSettings?.let { it.copy(settingsJson = settings(it.settingsJson)) },
        themes = themes.map { it.copy(settingsJson = settings(it.settingsJson)) },
        bookSettings = bookSettings.map {
            val decoded = readerDataJson.decodeFromString<ReaderSettingsOverrides>(it.overridesJson)
            if (decoded.font is ReaderFontRef.Imported) {
                it.copy(overridesJson = readerDataJson.encodeToString(decoded.copy(font = ReaderFontRef.System)))
            } else it
        },
    )
}
