package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.BookCollection
import com.xinyue.reader.core.domain.model.BookCollectionMembership
import kotlinx.coroutines.flow.Flow

/** Maintains user-defined collections and their many-to-many book memberships. */
interface BookCollectionRepository {
    /** Observes collections in their persisted display order. */
    fun observeAll(): Flow<List<BookCollection>>

    /** Observes every persisted membership ordered by book and collection identity. */
    fun observeMemberships(): Flow<List<BookCollectionMembership>>

    /** Creates a collection after normalizing and validating its user-visible name. */
    suspend fun create(name: String): BookCollection

    /** Renames an existing collection; invalid, missing, or duplicate names fail validation. */
    suspend fun rename(collectionId: String, name: String)

    /** Deletes a collection and only its memberships; books are never deleted. */
    suspend fun delete(collectionId: String)

    /** Adds the Cartesian product of [bookIds] and [collectionIds], preserving existing memberships. */
    suspend fun addBooksToCollections(bookIds: Set<String>, collectionIds: Set<String>)

    /** Removes only matching memberships, leaving all other collection membership intact. */
    suspend fun removeBooksFromCollections(bookIds: Set<String>, collectionIds: Set<String>)

    /** Replaces all collection memberships for every selected book in one transaction. */
    suspend fun replaceCollectionsForBooks(bookIds: Set<String>, collectionIds: Set<String>)

    /**
     * V1.4 compatibility operation. It now replaces memberships with zero or one collection rather
     * than writing a single-value column.
     */
    suspend fun moveBooks(bookIds: Set<String>, groupId: String?) {
        replaceCollectionsForBooks(bookIds, groupId?.let(::setOf).orEmpty())
    }
}

/** Compatibility name for source callers while the V1.5 UI adopts [BookCollectionRepository]. */
typealias BookGroupRepository = BookCollectionRepository
