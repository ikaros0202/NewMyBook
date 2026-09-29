package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import org.junit.Test

class CountingLimitedInputStreamTest {
    @Test
    fun `counts bounded reads and rejects the first byte beyond limit`() {
        val input = CountingLimitedInputStream(ByteArrayInputStream(ByteArray(5)), limit = 4)
        val buffer = ByteArray(3)

        assertThat(input.read(buffer)).isEqualTo(3)
        val error = runCatching { input.read(buffer) }.exceptionOrNull()

        assertThat(error).isInstanceOf(BackupLimitExceededException::class.java)
        assertThat(input.count).isEqualTo(5)
    }
}
