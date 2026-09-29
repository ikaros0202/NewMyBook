package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.ReadingStatistics
import kotlinx.coroutines.flow.Flow

/**
 * Records effective reading sessions independently from reading progress.
 *
 * Callers should isolate failures from opening, paging, and saving progress. Time ranges use epoch
 * milliseconds and `[rangeStartEpochMillis, rangeEndEpochMillis)` semantics.
 */
interface ReadingSessionRepository {
    /** Starts one open session for [bookId] and returns its stable ID. */
    suspend fun begin(bookId: String, nowEpochMillis: Long): String

    /** Accounts eligible active time up to [nowEpochMillis] and keeps the session open. */
    suspend fun recordInteraction(sessionId: String, nowEpochMillis: Long)

    /** Accounts final eligible time and closes the session; repeated or missing sessions are harmless. */
    suspend fun end(sessionId: String, nowEpochMillis: Long)

    /** Closes sessions left open by process death or an interrupted reader lifecycle. */
    suspend fun closeStaleSessions(nowEpochMillis: Long)

    /** Observes all retained statistics for one book. */
    fun observeBookStatistics(bookId: String): Flow<ReadingStatistics>

    /** Observes one book's daily aggregates intersecting the requested half-open range. */
    fun observeBookStatistics(
        bookId: String,
        rangeStartEpochMillis: Long,
        rangeEndEpochMillis: Long,
    ): Flow<ReadingStatistics> = observeBookStatistics(bookId)

    /** Observes library-wide daily aggregates for the requested half-open range. */
    fun observeStatistics(rangeStartEpochMillis: Long, rangeEndEpochMillis: Long): Flow<ReadingStatistics>

    /** Deletes session and aggregate statistics without deleting books or reading progress. */
    suspend fun clearStatistics()
}
