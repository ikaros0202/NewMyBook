package com.xinyue.reader.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TextAnchor(
    val offset: Long,
    val contextHash: String,
    val prefix: String = "",
    val suffix: String = "",
) {
    fun clampTo(contentLength: Long): TextAnchor =
        copy(offset = offset.coerceIn(0, contentLength.coerceAtLeast(0)))
}

@Serializable
data class ReadingProgress(
    val bookId: String,
    val anchor: TextAnchor,
    val contentLength: Long,
    val updatedAtEpochMillis: Long,
) {
    val fraction: Double
        get() = if (contentLength <= 0) 0.0 else (anchor.offset.toDouble() / contentLength).coerceIn(0.0, 1.0)
}
