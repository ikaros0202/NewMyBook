package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.LibraryLayoutPreference
import kotlinx.coroutines.flow.Flow

/** Stores device-local library presentation state separately from books and backup data. */
interface LibraryLayoutPreferences {
    /** Observes the current library mode and grid density. */
    fun observe(): Flow<LibraryLayoutPreference>

    /** Persists a new presentation preference. Implementations may report storage failures. */
    suspend fun set(preference: LibraryLayoutPreference)
}
