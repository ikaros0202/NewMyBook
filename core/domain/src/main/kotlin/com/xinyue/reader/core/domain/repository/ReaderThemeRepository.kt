package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.ReaderThemePreset
import kotlinx.coroutines.flow.Flow

/** Persists named reader appearance presets while preserving protected built-in themes. */
interface ReaderThemeRepository {
    /** Observes all available presets; malformed persisted custom entries may be omitted by implementations. */
    fun observeAll(): Flow<List<ReaderThemePreset>>

    /** Creates or replaces a preset after validating stable ID and unique, bounded display name. */
    suspend fun save(preset: ReaderThemePreset)

    /** Deletes a custom preset if present; built-in or missing presets are left intact. */
    suspend fun delete(presetId: String)
}
