package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

/**
 * Book-specific bookmark view over the unified annotation store.
 *
 * Implementations must not create a second bookmark truth source: bookmark writes and deletes must be
 * visible through [AnnotationRepository] as `BOOKMARK` annotations.
 */
interface BookmarkRepository {
    /** Observes bookmarks for [bookId] in ascending text-position order. */
    fun observe(bookId: String): Flow<List<Bookmark>>

    /** Inserts or replaces a bookmark by its stable ID. */
    suspend fun add(bookmark: Bookmark)

    /** Deletes the bookmark if present; deleting a missing ID is a no-op. */
    suspend fun delete(bookmarkId: String)
}
