package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.BookSearchIndexState
import com.xinyue.reader.core.domain.model.BookSearchResult
import kotlinx.coroutines.flow.Flow

/**
 * Full-book search boundary backed by a rebuildable index.
 *
 * Result positions are UTF-16 offsets into normalized content. Index construction is asynchronous;
 * implementations must not replace the last usable generation until a new generation is complete.
 */
interface BookSearchRepository {
    /** Ensures indexing is scheduled when the active generation is missing or stale for the book hash. */
    suspend fun ensureIndexed(bookId: String)

    /** Observes index status and indexed length for [bookId]. */
    fun observeIndexState(bookId: String): Flow<BookSearchIndexState>

    /**
     * Searches normalized content and returns at most [limit] results in text order.
     * The current contract requires at least two Unicode code points and a limit in `1..500`.
     */
    suspend fun search(bookId: String, query: String, limit: Int = 100): List<BookSearchResult>

    /** Replaces any queued build and starts a fresh index generation. */
    suspend fun rebuild(bookId: String)

    /** Cancels the queued or running build without invalidating the last active generation. */
    suspend fun cancelIndex(bookId: String)
}
