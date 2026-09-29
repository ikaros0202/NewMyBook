package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.ReaderAnnotation
import kotlinx.coroutines.flow.Flow

/**
 * Persists the unified bookmark, highlight, and note records for a book.
 *
 * Text positions are UTF-16 offsets in the normalized book content. Range queries use
 * `[startOffset, endOffset)` semantics; zero-width records match when their point lies in that range.
 */
interface AnnotationRepository {
    /** Observes every annotation for [bookId], ordered by text position and creation time. */
    fun observe(bookId: String): Flow<List<ReaderAnnotation>>

    /** Observes only annotations of [kind] for [bookId], using the same stable ordering. */
    fun observe(bookId: String, kind: AnnotationKind): Flow<List<ReaderAnnotation>>

    /** Returns the current annotation snapshot for backup or bounded orchestration work. */
    suspend fun getForBook(bookId: String): List<ReaderAnnotation>

    /** Returns the record with [annotationId], or `null` when it no longer exists. */
    suspend fun get(annotationId: String): ReaderAnnotation?

    /** Finds annotations whose range overlaps `[startOffset, endOffset)`. */
    suspend fun findOverlapping(bookId: String, startOffset: Long, endOffset: Long): List<ReaderAnnotation>

    /** Inserts or replaces one annotation by its stable ID. */
    suspend fun upsert(annotation: ReaderAnnotation)

    /** Deletes the record if present; deleting a missing ID is a no-op. */
    suspend fun delete(annotationId: String)
}
