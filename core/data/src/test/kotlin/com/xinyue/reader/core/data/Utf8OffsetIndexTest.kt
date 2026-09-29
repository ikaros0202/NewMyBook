package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Utf8OffsetIndexTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `records valid utf16 to utf8 byte checkpoints across a split surrogate pair`() {
        val text = "A😀B"
        val characters = text.toCharArray()
        val output = ByteArrayOutputStream()
        val writer = CheckpointingUtf8Writer(output, checkpointIntervalUtf16Units = 2)

        writer.write(characters, 0, 2)
        writer.write(characters, 2, characters.size - 2)
        writer.close()

        assertThat(output.toByteArray().toString(Charsets.UTF_8)).isEqualTo(text)
        assertThat(writer.checkpoints.first()).isEqualTo(Utf8OffsetCheckpoint(0, 0))
        assertThat(writer.checkpoints.last())
            .isEqualTo(Utf8OffsetCheckpoint(text.length.toLong(), text.toByteArray().size.toLong()))
    }

    @Test
    fun `round trips the compact checkpoint sidecar`() {
        val file = temporaryFolder.newFile("offsets.xidx")
        val checkpoints = listOf(
            Utf8OffsetCheckpoint(0, 0),
            Utf8OffsetCheckpoint(4096, 7210),
            Utf8OffsetCheckpoint(8123, 14002),
        )

        Utf8OffsetIndexFile.write(file, checkpoints)

        assertThat(Utf8OffsetIndexFile.read(file)).containsExactlyElementsIn(checkpoints).inOrder()
    }
}
