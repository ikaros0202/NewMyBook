package com.xinyue.reader.core.domain.model

import java.io.InputStream

data class ImportSource(
    val displayName: String,
    val sizeBytes: Long,
    val openStream: () -> InputStream,
) {
    companion object {
        const val UNKNOWN_SIZE_BYTES = -1L
    }
}
