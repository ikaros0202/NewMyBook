package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.ImportSource

/** Owns validated custom-cover files and their database reference for a book. */
interface BookCoverRepository {
    /**
     * Imports [source] into the book's private directory and returns its private relative path.
     * Invalid images, unsafe paths, missing books, and failed publication are reported as failures.
     */
    suspend fun importCover(bookId: String, source: ImportSource): String

    /** Clears the database reference before removing the previous private cover file. */
    suspend fun clearCover(bookId: String)
}
