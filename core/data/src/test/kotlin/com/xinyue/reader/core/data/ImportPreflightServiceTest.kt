package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ImportPreflightServiceTest {
    @Test
    fun `keeps valid and invalid files as separate preflight results`() = runTest {
        val sources = mapOf(
            "content://valid" to "第一章\n正文".toByteArray(),
            "content://blank" to " \r\n\t　\n".toByteArray(),
        )
        val service = AndroidImportPreflightService(
            sourceFactory = object : ImportSourceFactory {
                override suspend fun create(uriString: String): ImportSource {
                    val bytes = sources.getValue(uriString)
                    return ImportSource(
                        displayName = uriString.substringAfterLast('/') + ".txt",
                        sizeBytes = bytes.size.toLong(),
                        openStream = { ByteArrayInputStream(bytes) },
                    )
                }
            },
        )

        val results = service.analyze(sources.keys.toList())

        assertThat(results).hasSize(2)
        assertThat(results[0].isValid).isTrue()
        assertThat(results[0].encoding?.recommendedCharsetName).isEqualTo("UTF-8")
        assertThat(results[1].isValid).isFalse()
        assertThat(results[1].errorMessage).contains("有效正文")
    }

    @Test
    fun `does not expose provider exception text in a preflight result`() = runTest {
        val service = AndroidImportPreflightService(
            sourceFactory = object : ImportSourceFactory {
                override suspend fun create(uriString: String): ImportSource {
                    throw IllegalStateException("permission denied for content://private/path")
                }
            },
        )

        val result = service.analyze(listOf("content://private/path")).single()

        assertThat(result.errorMessage).isEqualTo("无法检查该 TXT 文件")
    }
}
