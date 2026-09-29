package com.xinyue.reader.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AnnotationExportFormat {
    MARKDOWN,
    JSON,
}

/**
 * Requests a local annotation export. A `null` [bookIds] value means every current book.
 *
 * Bookmarks are excluded by default because they often carry no user-authored content.
 */
@Serializable
data class AnnotationExportRequest(
    val format: AnnotationExportFormat,
    val bookIds: Set<String>? = null,
    val includeBookmarks: Boolean = false,
)

sealed interface AnnotationExportResult {
    data class Success(
        val bookCount: Int,
        val annotationCount: Int,
        val byteCount: Long,
    ) : AnnotationExportResult

    data class Failure(val safeMessage: String) : AnnotationExportResult
}
