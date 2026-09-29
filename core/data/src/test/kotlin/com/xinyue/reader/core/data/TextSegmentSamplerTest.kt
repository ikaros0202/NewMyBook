package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import org.junit.Test

class TextSegmentSamplerTest {
    @Test
    fun `samples beginning middle and end without retaining the whole known file`() {
        val bytes = ByteArray(100) { it.toByte() }
        val result = TextSegmentSampler(segmentBytes = 10).sample(
            openStream = { ByteArrayInputStream(bytes) },
            declaredSizeBytes = bytes.size.toLong(),
        )

        assertThat(result.actualSizeBytes).isEqualTo(100)
        assertThat(result.segments.beginning.toList()).containsExactlyElementsIn(bytes.sliceArray(0..9).toList()).inOrder()
        assertThat(result.segments.middle.toList()).containsExactlyElementsIn(bytes.sliceArray(45..54).toList()).inOrder()
        assertThat(result.segments.end.toList()).containsExactlyElementsIn(bytes.sliceArray(90..99).toList()).inOrder()
    }

    @Test
    fun `counts an unknown provider stream before selecting exact segments`() {
        val bytes = ByteArray(73) { (it + 1).toByte() }
        val result = TextSegmentSampler(segmentBytes = 9).sample(
            openStream = { ByteArrayInputStream(bytes) },
            declaredSizeBytes = ImportSource.UNKNOWN_SIZE_BYTES,
        )

        assertThat(result.actualSizeBytes).isEqualTo(73)
        assertThat(result.segments.beginning).hasLength(9)
        assertThat(result.segments.middle).hasLength(9)
        assertThat(result.segments.end.toList()).containsExactlyElementsIn(bytes.takeLast(9)).inOrder()
    }
}
