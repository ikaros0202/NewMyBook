package com.xinyue.reader.core.ui

import androidx.compose.runtime.saveable.SaverScope
import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.AnnotationExportFormat
import com.xinyue.reader.core.domain.model.AnnotationExportRequest
import com.xinyue.reader.core.domain.model.HandoffExportRequest
import org.junit.Test

class ExportRequestSaversTest {
    private val scope = SaverScope { it is String || it is Boolean }

    @Test
    fun `annotation selection and options survive saved state restoration`() {
        for (ids in listOf(null, emptySet(), setOf("public-1", "public-2"))) {
            for (format in AnnotationExportFormat.entries) {
                val request = AnnotationExportRequest(format, ids, includeBookmarks = true)
                val saved = with(AnnotationExportRequestSaver) { scope.save(request) }
                assertThat(AnnotationExportRequestSaver.restore(requireNotNull(saved))).isEqualTo(request)
            }
        }
    }

    @Test
    fun `handoff source and text option survive saved state restoration`() {
        for (includeText in listOf(false, true)) {
            val request = HandoffExportRequest("public-1", includeText)
            val saved = with(HandoffExportRequestSaver) { scope.save(request) }
            assertThat(HandoffExportRequestSaver.restore(requireNotNull(saved))).isEqualTo(request)
        }
    }
}
