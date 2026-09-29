package com.xinyue.reader.core.text

import java.io.Reader
import java.io.Writer

object TextStreamNormalizer {
    fun copy(
        reader: Reader,
        writer: Writer,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
        onNormalizedChunk: ((CharArray, Int, Int) -> Unit)? = null,
    ): Long {
        require(bufferSize > 0) { "bufferSize must be positive" }

        val buffer = CharArray(bufferSize)
        val normalizedBuffer = CharArray(bufferSize)
        var normalizedCount = 0
        var isFirstCharacter = true
        var pendingCarriageReturn = false
        var writtenUnits = 0L

        fun flushNormalizedBuffer() {
            if (normalizedCount == 0) return
            writer.write(normalizedBuffer, 0, normalizedCount)
            onNormalizedChunk?.invoke(normalizedBuffer, 0, normalizedCount)
            normalizedCount = 0
        }

        fun emit(character: Char) {
            normalizedBuffer[normalizedCount++] = character
            writtenUnits += 1
            if (normalizedCount == normalizedBuffer.size) flushNormalizedBuffer()
        }

        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            for (index in 0 until count) {
                val character = buffer[index]
                if (isFirstCharacter) {
                    isFirstCharacter = false
                    if (character == '\uFEFF') continue
                }

                if (pendingCarriageReturn) {
                    emit('\n')
                    pendingCarriageReturn = false
                    if (character == '\n') continue
                }

                if (character == '\r') {
                    pendingCarriageReturn = true
                } else {
                    emit(character)
                }
            }
        }

        if (pendingCarriageReturn) {
            emit('\n')
        }
        flushNormalizedBuffer()
        writer.flush()
        return writtenUnits
    }
}
