package com.xinyue.reader.core.text

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class DecodedText(
    val text: String,
    val charsetName: String,
)

object TxtDecoder {
    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val utf16LeBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val utf16BeBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    fun decode(
        bytes: ByteArray,
        preferredCharsetName: String? = null,
    ): DecodedText {
        preferredCharsetName?.let { name ->
            return decodeWith(bytes, Charset.forName(name))
        }

        return when {
            bytes.startsWith(utf8Bom) -> decodeWith(bytes.copyOfRange(utf8Bom.size, bytes.size), Charsets.UTF_8)
            bytes.startsWith(utf16LeBom) -> decodeWith(bytes.copyOfRange(utf16LeBom.size, bytes.size), Charsets.UTF_16LE)
            bytes.startsWith(utf16BeBom) -> decodeWith(bytes.copyOfRange(utf16BeBom.size, bytes.size), Charsets.UTF_16BE)
            else -> listOf(Charsets.UTF_8, Charset.forName("GB18030"), Charset.forName("Big5"))
                .firstNotNullOfOrNull { charset -> strictDecode(bytes, charset) }
                ?: decodeWith(bytes, Charsets.UTF_8)
        }
    }

    /**
     * Detects a charset from a bounded prefix without treating an unfinished
     * character at the end of that prefix as malformed input.
     */
    fun detectCharsetPrefix(
        bytes: ByteArray,
        preferredCharsetName: String? = null,
    ): String {
        val candidates = preferredCharsetName?.let { listOf(Charset.forName(it)) } ?: when {
            bytes.startsWith(utf8Bom) -> listOf(Charsets.UTF_8)
            bytes.startsWith(utf16LeBom) -> listOf(Charsets.UTF_16LE)
            bytes.startsWith(utf16BeBom) -> listOf(Charsets.UTF_16BE)
            else -> listOf(Charsets.UTF_8, Charset.forName("GB18030"), Charset.forName("Big5"))
        }
        return candidates.firstOrNull { charset -> canDecodePrefix(bytes, charset) }?.name()
            ?: throw CharacterCodingException()
    }

    private fun canDecodePrefix(bytes: ByteArray, charset: Charset): Boolean {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val outputSize = (bytes.size * decoder.maxCharsPerByte())
            .toInt()
            .coerceAtLeast(1) + 1
        val result = decoder.decode(
            ByteBuffer.wrap(bytes),
            CharBuffer.allocate(outputSize),
            false,
        )
        return !result.isError
    }

    private fun strictDecode(bytes: ByteArray, charset: Charset): DecodedText? =
        try {
            decodeWith(bytes, charset, reportMalformedInput = true)
        } catch (_: CharacterCodingException) {
            null
        }

    private fun decodeWith(
        bytes: ByteArray,
        charset: Charset,
        reportMalformedInput: Boolean = false,
    ): DecodedText {
        val decoder = charset.newDecoder()
        if (reportMalformedInput) {
            decoder.onMalformedInput(CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT)
        }
        val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()
        return DecodedText(
            text = TextNormalizer.normalize(decoded),
            charsetName = charset.name(),
        )
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }
}
