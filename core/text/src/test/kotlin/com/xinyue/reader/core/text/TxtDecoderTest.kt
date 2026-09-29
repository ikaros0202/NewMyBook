package com.xinyue.reader.core.text

import com.google.common.truth.Truth.assertThat
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import org.junit.Test
import kotlin.test.assertFailsWith

class TxtDecoderTest {
    @Test
    fun `decodes UTF-8 BOM and removes the marker`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "第一章".toByteArray()

        val result = TxtDecoder.decode(bytes)

        assertThat(result.text).isEqualTo("第一章")
        assertThat(result.charsetName).isEqualTo("UTF-8")
    }

    @Test
    fun `decodes UTF-16LE BOM`() {
        val payload = "章节".toByteArray(Charsets.UTF_16LE)
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + payload

        val result = TxtDecoder.decode(bytes)

        assertThat(result.text).isEqualTo("章节")
        assertThat(result.charsetName).isEqualTo("UTF-16LE")
    }

    @Test
    fun `falls back to GB18030 for common Chinese legacy text`() {
        val bytes = "这是一本中文小说".toByteArray(Charset.forName("GB18030"))

        val result = TxtDecoder.decode(bytes)

        assertThat(result.text).isEqualTo("这是一本中文小说")
        assertThat(result.charsetName).isEqualTo("GB18030")
    }

    @Test
    fun `honors a user selected charset`() {
        val bytes = "繁體小說".toByteArray(Charset.forName("Big5"))

        val result = TxtDecoder.decode(bytes, preferredCharsetName = "Big5")

        assertThat(result.text).isEqualTo("繁體小說")
        assertThat(result.charsetName).isEqualTo("Big5")
    }

    @Test
    fun `prefix detection accepts a UTF-8 character split by the sample boundary`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val complete = bom + ByteArray(65_531) { 'a'.code.toByte() } + "界".toByteArray()
        val truncatedPrefix = complete.copyOf(65_536)

        assertThat(TxtDecoder.detectCharsetPrefix(truncatedPrefix, "UTF-8"))
            .isEqualTo("UTF-8")
    }

    @Test
    fun `prefix detection accepts trailing partial units for supported charsets`() {
        val cases = listOf(
            "UTF-16LE" to "章".toByteArray(Charsets.UTF_16LE),
            "UTF-16BE" to "章".toByteArray(Charsets.UTF_16BE),
            "GB18030" to "𠀀".toByteArray(Charset.forName("GB18030")),
            "Big5" to "繁".toByteArray(Charset.forName("Big5")),
        )

        cases.forEach { (charsetName, encoded) ->
            assertThat(TxtDecoder.detectCharsetPrefix(encoded.copyOf(encoded.size - 1), charsetName))
                .isEqualTo(charsetName)
        }
    }

    @Test
    fun `prefix detection rejects malformed bytes before the trailing boundary`() {
        val malformed = byteArrayOf('a'.code.toByte(), 0xC3.toByte(), 0x28, 'z'.code.toByte())

        assertFailsWith<CharacterCodingException> {
            TxtDecoder.detectCharsetPrefix(malformed, "UTF-8")
        }
    }

    @Test
    fun `prefix detection rejects malformed complete units for every supported legacy charset`() {
        val malformedCases = listOf(
            "UTF-16LE" to byteArrayOf(0x00, 0xD8.toByte(), 0x41, 0x00),
            "UTF-16BE" to byteArrayOf(0xD8.toByte(), 0x00, 0x00, 0x41),
            "GB18030" to byteArrayOf(0x81.toByte(), 0x30, 0x20, 0x30),
            "Big5" to byteArrayOf(0x81.toByte(), 0x20, 0x61),
        )

        malformedCases.forEach { (charsetName, malformed) ->
            assertFailsWith<CharacterCodingException>(charsetName) {
                TxtDecoder.detectCharsetPrefix(malformed, charsetName)
            }
        }
    }
}
