package com.xinyue.reader.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AnnotationKind { BOOKMARK, HIGHLIGHT, NOTE }

@Serializable
enum class HighlightColor { YELLOW, GREEN, BLUE, PINK }

data class TextRangeAnchor(
    val startOffset: Long,
    val endOffset: Long,
    val prefix: String,
    val suffix: String,
    val selectedSha256: String?,
) {
    init {
        require(startOffset >= 0) { "Start offset cannot be negative" }
        require(endOffset >= startOffset) { "End offset cannot precede start offset" }
    }
}

data class ReaderAnnotation(
    val id: String,
    val bookId: String,
    val kind: AnnotationKind,
    val range: TextRangeAnchor,
    val color: HighlightColor?,
    val note: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
