package com.xinyue.reader.core.data

typealias ImportSource = com.xinyue.reader.core.domain.model.ImportSource

data class StagedBookFiles(
    val bookId: String,
    val originalFileName: String,
    val charsetName: String,
    val contentSha256: String,
    val contentLength: Long,
    val suggestedTitle: String? = null,
    val suggestedAuthor: String? = null,
    val suggestedSeriesName: String? = null,
    val suggestedSeriesOrder: Int? = null,
)

data class StoredBookFiles(
    val originalPath: String,
    val normalizedPath: String,
)

interface BookFileStore {
    suspend fun stage(
        bookId: String,
        source: ImportSource,
        preferredCharsetName: String? = null,
    ): StagedBookFiles

    suspend fun commit(staged: StagedBookFiles): StoredBookFiles

    suspend fun discard(staged: StagedBookFiles)

    suspend fun remove(stored: StoredBookFiles)
}
