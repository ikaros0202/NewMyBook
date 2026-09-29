package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.ImportSource
import com.xinyue.reader.core.domain.model.ImportedFont
import com.xinyue.reader.core.domain.model.FontRemovalResult
import kotlinx.coroutines.flow.Flow

/** Owns validated private font files and their metadata records. */
interface ImportedFontRepository {
    /** Observes imported font metadata; font bytes remain private to the data layer. */
    fun observeAll(): Flow<List<ImportedFont>>

    /**
     * Validates and publishes [source] into private storage, returning the durable metadata record.
     * Failed validation or publication must not leave a visible database record or final font directory.
     */
    suspend fun importFont(source: ImportSource): ImportedFont

    /** Removes an unreferenced font and reports missing or in-use records without destructive fallback. */
    suspend fun remove(fontId: String): FontRemovalResult
}
