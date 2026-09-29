package com.xinyue.reader.core.data

import com.xinyue.reader.core.text.TextByteSegments
import java.io.InputStream

data class SampledTxtSegments(
    val actualSizeBytes: Long,
    val segments: TextByteSegments,
)

/** Reads only three bounded windows when provider size metadata is available. */
class TextSegmentSampler(
    private val segmentBytes: Int = DEFAULT_SEGMENT_BYTES,
    private val maxBytes: Long = ImportBookUseCase.MAX_TXT_BYTES,
) {
    init {
        require(segmentBytes > 0) { "segmentBytes must be positive" }
        require(maxBytes > 0) { "maxBytes must be positive" }
    }

    fun sample(
        openStream: () -> InputStream,
        declaredSizeBytes: Long,
    ): SampledTxtSegments {
        val actualSize = if (declaredSizeBytes == ImportSource.UNKNOWN_SIZE_BYTES) {
            countAndValidate(openStream)
        } else {
            require(declaredSizeBytes in 1..maxBytes) { "TXT 文件必须介于 1 字节和 50 MB 之间" }
            declaredSizeBytes
        }
        require(actualSize > 0) { "TXT 文件不能为空" }
        val window = minOf(segmentBytes.toLong(), actualSize).toInt()
        val middleOffset = ((actualSize - window) / 2L).coerceAtLeast(0L)
        val endOffset = (actualSize - window).coerceAtLeast(0L)
        return SampledTxtSegments(
            actualSizeBytes = actualSize,
            segments = TextByteSegments(
                beginning = readRange(openStream, 0L, window),
                middle = readRange(openStream, middleOffset, window),
                end = readRange(openStream, endOffset, window),
            ),
        )
    }

    private fun countAndValidate(openStream: () -> InputStream): Long = openStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            require(total <= maxBytes) { "TXT 文件不能超过 50 MB" }
        }
        total
    }

    private fun readRange(openStream: () -> InputStream, offset: Long, count: Int): ByteArray =
        openStream().buffered().use { input ->
            input.skipFully(offset)
            input.readAtMost(count)
        }

    private fun InputStream.skipFully(requested: Long) {
        var remaining = requested
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (read() >= 0) {
                remaining -= 1
            } else {
                break
            }
        }
    }

    private fun InputStream.readAtMost(max: Int): ByteArray {
        val result = ByteArray(max)
        var offset = 0
        while (offset < max) {
            val count = read(result, offset, max - offset)
            if (count < 0) break
            if (count == 0) continue
            offset += count
        }
        return result.copyOf(offset)
    }

    private companion object {
        const val DEFAULT_SEGMENT_BYTES = 24 * 1024
    }
}
