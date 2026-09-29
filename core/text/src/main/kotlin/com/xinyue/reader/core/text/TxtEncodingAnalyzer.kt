package com.xinyue.reader.core.text

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class TextByteSegments(
    val beginning: ByteArray,
    val middle: ByteArray,
    val end: ByteArray,
) {
    companion object {
        fun single(bytes: ByteArray) = TextByteSegments(bytes, bytes, bytes)
    }
}

data class ThreeSegmentTextPreview(
    val beginning: String,
    val middle: String,
    val end: String,
)

data class EncodingCandidate(
    val charsetName: String,
    val preview: ThreeSegmentTextPreview,
    val qualityScore: Int,
)

data class TxtEncodingAnalysis(
    val recommendedCharsetName: String,
    val isCertain: Boolean,
    val candidates: List<EncodingCandidate>,
    val explanation: String,
)

/**
 * Conservative TXT encoding analysis for the import preflight screen.
 *
 * A BOM, a strong UTF-16 byte pattern, or valid non-ASCII UTF-8 is considered
 * certain. ASCII and overlapping Chinese legacy encodings deliberately stop
 * for user confirmation because silently choosing one can corrupt a whole
 * novel while still producing technically valid characters.
 */
object TxtEncodingAnalyzer {
    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val utf16LeBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val utf16BeBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    private val legacyNames = listOf("GB18030", "Big5")

    fun analyze(segments: TextByteSegments): TxtEncodingAnalysis {
        require(segments.beginning.isNotEmpty()) { "TXT 文件不能为空" }

        bomCharset(segments.beginning)?.let { charsetName ->
            return certain(segments, charsetName, "检测到编码标记")
        }
        detectUtf16BytePattern(segments.beginning)?.let { charsetName ->
            return certain(segments, charsetName, "检测到稳定的 UTF-16 字节排列")
        }

        val utf8Text = strictDecode(segments.beginning, "UTF-8")
        val containsNonAscii = segments.beginning.any { byte -> byte.toInt() and 0x80 != 0 }
        if (utf8Text != null && containsNonAscii) {
            return certain(segments, "UTF-8", "样本是有效的非 ASCII UTF-8 文本")
        }

        val candidateNames = buildList {
            if (utf8Text != null) add("UTF-8")
            legacyNames.filterTo(this) { name -> strictDecode(segments.beginning, name) != null }
            if (isEmpty()) addAll(listOf("UTF-8", "GB18030", "Big5"))
        }.distinct()
        val candidates = candidateNames
            .map { name -> candidate(segments, name) }
            .sortedByDescending(EncodingCandidate::qualityScore)
        val recommended = when {
            isAsciiText(segments.beginning) -> "UTF-8"
            else -> candidates.first().charsetName
        }
        return TxtEncodingAnalysis(
            recommendedCharsetName = recommended,
            isCertain = false,
            candidates = candidates.sortedWith(compareByDescending<EncodingCandidate> { it.charsetName == recommended }
                .thenByDescending(EncodingCandidate::qualityScore)),
            explanation = if (isAsciiText(segments.beginning)) {
                "内容仅含 ASCII，无法仅凭字节区分 UTF-8 与中文旧编码"
            } else {
                "多个编码都能解码该文件，请核对开头、中段和结尾"
            },
        )
    }

    private fun certain(
        segments: TextByteSegments,
        charsetName: String,
        explanation: String,
    ) = TxtEncodingAnalysis(
        recommendedCharsetName = charsetName,
        isCertain = true,
        candidates = listOf(candidate(segments, charsetName)),
        explanation = explanation,
    )

    private fun candidate(segments: TextByteSegments, charsetName: String): EncodingCandidate {
        val preview = ThreeSegmentTextPreview(
            beginning = decodePreview(segments.beginning, charsetName, stripBom = true),
            middle = decodePreview(segments.middle, charsetName, stripBom = false),
            end = decodePreview(segments.end, charsetName, stripBom = false),
        )
        val combined = preview.beginning + preview.middle + preview.end
        return EncodingCandidate(
            charsetName = charsetName,
            preview = preview,
            qualityScore = qualityScore(combined),
        )
    }

    private fun decodePreview(bytes: ByteArray, charsetName: String, stripBom: Boolean): String {
        val payload = if (stripBom) stripKnownBom(bytes) else bytes
        val decoder = Charset.forName(charsetName).newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return TextNormalizer.normalize(decoder.decode(ByteBuffer.wrap(payload)).toString())
            .asSequence()
            .map { character ->
                if (character.code < 0x20 && character != '\n' && character != '\t') '�' else character
            }
            .joinToString(separator = "")
            .trim()
            .take(MAX_PREVIEW_UNITS)
    }

    private fun strictDecode(bytes: ByteArray, charsetName: String): String? = try {
        Charset.forName(charsetName).newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun qualityScore(text: String): Int = text.sumOf { character ->
        when {
            character == '\uFFFD' || character == '\u0000' -> -30
            character.code in 0xE000..0xF8FF -> -12
            character.code < 0x20 && character != '\n' && character != '\t' -> -20
            character.code in 0x3400..0x9FFF -> 4
            character.isLetterOrDigit() -> 2
            character.isWhitespace() -> 1
            else -> 0
        }
    }

    private fun isAsciiText(bytes: ByteArray): Boolean = bytes.all { byte ->
        val value = byte.toInt() and 0xFF
        value == 0x09 || value == 0x0A || value == 0x0D || value in 0x20..0x7E
    }

    private fun bomCharset(bytes: ByteArray): String? = when {
        bytes.startsWith(utf8Bom) -> "UTF-8"
        bytes.startsWith(utf16LeBom) -> "UTF-16LE"
        bytes.startsWith(utf16BeBom) -> "UTF-16BE"
        else -> null
    }

    private fun detectUtf16BytePattern(bytes: ByteArray): String? {
        val pairs = (bytes.size.coerceAtMost(8 * 1024) / 2)
        if (pairs < 4) return null
        var evenNuls = 0
        var oddNuls = 0
        repeat(pairs) { index ->
            if (bytes[index * 2] == 0.toByte()) evenNuls += 1
            if (bytes[index * 2 + 1] == 0.toByte()) oddNuls += 1
        }
        val evenRatio = evenNuls.toDouble() / pairs
        val oddRatio = oddNuls.toDouble() / pairs
        return when {
            oddRatio >= UTF16_NUL_RATIO && evenRatio <= UTF16_OTHER_SIDE_MAX_RATIO -> "UTF-16LE"
            evenRatio >= UTF16_NUL_RATIO && oddRatio <= UTF16_OTHER_SIDE_MAX_RATIO -> "UTF-16BE"
            else -> null
        }
    }

    private fun stripKnownBom(bytes: ByteArray): ByteArray = when {
        bytes.startsWith(utf8Bom) -> bytes.copyOfRange(utf8Bom.size, bytes.size)
        bytes.startsWith(utf16LeBom) -> bytes.copyOfRange(utf16LeBom.size, bytes.size)
        bytes.startsWith(utf16BeBom) -> bytes.copyOfRange(utf16BeBom.size, bytes.size)
        else -> bytes
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

    private const val MAX_PREVIEW_UNITS = 500
    private const val UTF16_NUL_RATIO = 0.25
    // Some CJK code points (for example U+4E00) legitimately have a zero low
    // byte, so the opposite side needs a little tolerance.
    private const val UTF16_OTHER_SIDE_MAX_RATIO = 0.15
}
