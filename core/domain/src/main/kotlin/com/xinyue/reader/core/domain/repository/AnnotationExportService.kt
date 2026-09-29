package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.AnnotationExportRequest
import com.xinyue.reader.core.domain.model.AnnotationExportResult

/** Builds and publishes a bounded, local-only annotation document through a caller-selected URI. */
interface AnnotationExportService {
    suspend fun export(
        destinationUri: String,
        request: AnnotationExportRequest,
    ): AnnotationExportResult
}
