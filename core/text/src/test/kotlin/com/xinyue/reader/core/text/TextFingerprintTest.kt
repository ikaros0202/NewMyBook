package com.xinyue.reader.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextFingerprintTest {
    @Test
    fun `captures bounded context and finds the nearest matching selection`() {
        val text = "前文".repeat(30) + "星河璀璨" + "后文".repeat(30)
        val start = text.indexOf("星河璀璨")
        val fingerprint = TextFingerprint.capture(text, start, start + 4)
        val shifted = "新增开头" + text

        assertThat(fingerprint.prefix.codePointCount(0, fingerprint.prefix.length)).isAtMost(24)
        assertThat(fingerprint.suffix.codePointCount(0, fingerprint.suffix.length)).isAtMost(24)
        assertThat(
            TextFingerprint.findNearest(
                text = shifted,
                expectedStart = start,
                selectedUtf16Length = 4,
                fingerprint = fingerprint,
            ),
        ).isEqualTo(start + 4)
    }

    @Test
    fun `rejects changed selected text and snaps emoji boundaries`() {
        val text = "开头😀选中文本结尾"
        val emojiLowSurrogate = text.indexOf("😀") + 1
        val fingerprint = TextFingerprint.capture(text, emojiLowSurrogate, text.indexOf("结尾"))

        assertThat(fingerprint.selectedSha256).isNotEmpty()
        assertThat(
            TextFingerprint.findNearest(
                text = text.replace("选中文本", "已经改变"),
                expectedStart = emojiLowSurrogate,
                selectedUtf16Length = text.indexOf("结尾") - (emojiLowSurrogate - 1),
                fingerprint = fingerprint,
            ),
        ).isNull()
    }

    @Test
    fun `supports an empty bookmark range`() {
        val text = "第一章 正文"
        val fingerprint = TextFingerprint.capture(text, 3, 3)

        assertThat(fingerprint.selectedSha256).isNull()
        assertThat(TextFingerprint.findNearest(text, 3, 0, fingerprint)).isEqualTo(3)
    }

    @Test
    fun `context hash is stable sha256 with an unambiguous separator`() {
        val fingerprint = TextFingerprint(prefix = "甲", suffix = "乙", selectedSha256 = null)

        assertThat(TextFingerprint.contextSha256(fingerprint)).isEqualTo(
            "d90d5cb4c73e725fa4c51d57a44d5065bdc05aa05ae557944dcc4828c29bb060",
        )
    }
}
