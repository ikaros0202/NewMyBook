package com.xinyue.reader.core.text

import com.google.common.truth.Truth.assertThat
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import org.junit.Test

class TextStreamNormalizerTest {
    @Test
    fun `normalizes line endings even when every character is a separate read`() {
        val output = StringWriter()
        val writtenUnits = TextStreamNormalizer.copy(
            reader = OneCharacterReader("\uFEFF第一行\r\n第二行\r第三😀"),
            writer = output,
        )

        assertThat(output.toString()).isEqualTo("第一行\n第二行\n第三😀")
        assertThat(writtenUnits).isEqualTo(output.toString().length.toLong())
    }

    @Test
    fun `reports normalized output in bounded chunks`() {
        val output = StringWriter()
        val observed = StringBuilder()

        TextStreamNormalizer.copy(
            reader = StringReader("A\r\nB\rC"),
            writer = output,
            bufferSize = 2,
            onNormalizedChunk = { characters, offset, count ->
                observed.append(characters, offset, count)
            },
        )

        assertThat(output.toString()).isEqualTo("A\nB\nC")
        assertThat(observed.toString()).isEqualTo(output.toString())
    }

    private class OneCharacterReader(text: String) : Reader() {
        private val delegate = StringReader(text)

        override fun read(buffer: CharArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, length.coerceAtMost(1))

        override fun close() = delegate.close()
    }
}
