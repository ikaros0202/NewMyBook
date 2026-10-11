package com.xinyue.reader.core.ui

import androidx.compose.runtime.saveable.listSaver
import com.xinyue.reader.core.domain.model.AnnotationExportFormat
import com.xinyue.reader.core.domain.model.AnnotationExportRequest
import com.xinyue.reader.core.domain.model.HandoffExportRequest

val AnnotationExportRequestSaver = listSaver<AnnotationExportRequest?, Any>(
    save = { request ->
        if (request == null) emptyList() else
            listOf(request.format.name, request.includeBookmarks, request.bookIds == null) + request.bookIds.orEmpty().toList()
    },
    restore = { values ->
        if (values.isEmpty()) null else AnnotationExportRequest(
            format = AnnotationExportFormat.valueOf(values[0] as String),
            includeBookmarks = values[1] as Boolean,
            bookIds = if (values[2] as Boolean) null else values.drop(3).map { it as String }.toSet(),
        )
    },
)

val HandoffExportRequestSaver = listSaver<HandoffExportRequest?, Any>(
    save = { request -> request?.let { listOf(it.bookId, it.includeBookText) }.orEmpty() },
    restore = { values -> if (values.isEmpty()) null else HandoffExportRequest(values[0] as String, values[1] as Boolean) },
)
