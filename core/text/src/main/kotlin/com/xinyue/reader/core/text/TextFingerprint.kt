package com.xinyue.reader.core.text

import java.security.MessageDigest

data class TextFingerprint(
    val prefix: String,
    val suffix: String,
    val selectedSha256: String?,
) {
    companion object {
        private const val CONTEXT_CODE_POINTS = 24

        fun capture(text: String, start: Int, end: Int): TextFingerprint {
            val range = normalizedRange(text, start, end)
            val prefixStart = text.offsetByCodePoints(
                range.first,
                -minOf(CONTEXT_CODE_POINTS, text.codePointCount(0, range.first)),
            )
            val suffixEnd = text.offsetByCodePoints(
                range.second,
                minOf(CONTEXT_CODE_POINTS, text.codePointCount(range.second, text.length)),
            )
            val selected = text.substring(range.first, range.second)
            return TextFingerprint(
                prefix = text.substring(prefixStart, range.first),
                suffix = text.substring(range.second, suffixEnd),
                selectedSha256 = selected.takeIf(String::isNotEmpty)?.sha256(),
            )
        }

        fun findNearest(
            text: String,
            expectedStart: Int,
            selectedUtf16Length: Int,
            fingerprint: TextFingerprint,
        ): Int? {
            if (selectedUtf16Length < 0 || selectedUtf16Length > text.length) return null
            val lastStart = text.length - selectedUtf16Length
            val expected = expectedStart.coerceIn(0, lastStart)
            for (distance in 0..maxOf(expected, lastStart - expected)) {
                val left = expected - distance
                if (left >= 0 && matchesAt(text, left, selectedUtf16Length, fingerprint)) return left
                val right = expected + distance
                if (distance != 0 && right <= lastStart && matchesAt(text, right, selectedUtf16Length, fingerprint)) {
                    return right
                }
            }
            return null
        }

        fun contextSha256(fingerprint: TextFingerprint): String =
            (fingerprint.prefix + "\u0000" + fingerprint.suffix).sha256()

        internal fun matchesAt(
            text: String,
            start: Int,
            selectedUtf16Length: Int,
            fingerprint: TextFingerprint,
        ): Boolean {
            val end = start + selectedUtf16Length
            if (start > 0 && start < text.length && text[start].isLowSurrogate() && text[start - 1].isHighSurrogate()) {
                return false
            }
            if (end > 0 && end < text.length && text[end].isLowSurrogate() && text[end - 1].isHighSurrogate()) {
                return false
            }
            if (fingerprint.prefix.isNotEmpty()) {
                val prefixStart = start - fingerprint.prefix.length
                if (prefixStart < 0 || !text.regionMatches(prefixStart, fingerprint.prefix, 0, fingerprint.prefix.length)) {
                    return false
                }
            }
            if (fingerprint.suffix.isNotEmpty()) {
                if (end + fingerprint.suffix.length > text.length ||
                    !text.regionMatches(end, fingerprint.suffix, 0, fingerprint.suffix.length)
                ) return false
            }
            if (fingerprint.selectedSha256 != null &&
                text.substring(start, end).sha256() != fingerprint.selectedSha256
            ) return false
            if (fingerprint.selectedSha256 == null && selectedUtf16Length != 0) return false
            return true
        }

        private fun normalizedRange(text: String, start: Int, end: Int): Pair<Int, Int> {
            var safeStart = minOf(start, end).coerceIn(0, text.length)
            var safeEnd = maxOf(start, end).coerceIn(0, text.length)
            if (safeStart > 0 && safeStart < text.length &&
                text[safeStart].isLowSurrogate() && text[safeStart - 1].isHighSurrogate()
            ) safeStart -= 1
            if (safeEnd > 0 && safeEnd < text.length &&
                text[safeEnd].isLowSurrogate() && text[safeEnd - 1].isHighSurrogate()
            ) safeEnd += 1
            return safeStart to safeEnd
        }
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
