package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Persists global reader settings and sparse per-book appearance overrides.
 *
 * A resolved per-book view is derived from the global row plus that book's overrides; callers must not
 * persist the resolved view as a second source of truth.
 */
interface ReaderSettingsRepository {
    /** Observes global settings when [bookId] is null, otherwise the resolved global-plus-book view. */
    fun observe(bookId: String?): Flow<ReaderSettings>

    fun observeBookOverrides(bookId: String): Flow<ReaderSettingsOverrides>

    /** Replaces the global settings row without changing any book-specific overrides. */
    suspend fun updateGlobal(settings: ReaderSettings)

    /**
     * Reads the latest global value and writes its transformed value as one logical update.
     * Implementations backed by shared storage should override this to make the read-modify-write atomic.
     */
    suspend fun updateGlobalFromLatest(transform: (ReaderSettings) -> ReaderSettings) {
        updateGlobal(transform(observe(null).first()))
    }

    /** Replaces the sparse appearance override for exactly one book. */
    suspend fun updateBookOverrides(bookId: String, overrides: ReaderSettingsOverrides)

    /** Removes the book override so the book immediately inherits global appearance settings. */
    suspend fun clearBookOverrides(bookId: String)
}
