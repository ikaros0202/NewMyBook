package com.xinyue.reader.core.text

import com.google.common.truth.Truth.assertThat
import java.nio.charset.Charset
import org.junit.Test

class TxtEncodingAnalyzerTest {
    @Test
    fun `recognizes non ascii UTF-8 without interrupting the user`() {
        val analysis = TxtEncodingAnalyzer.analyze(
            TextByteSegments.single("第一章\n这是正文".toByteArray()),
        )

        assertThat(analysis.recommendedCharsetName).isEqualTo("UTF-8")
        assertThat(analysis.isCertain).isTrue()
    }

    @Test
    fun `treats ascii only content as uncertain and offers legacy candidates`() {
        val analysis = TxtEncodingAnalyzer.analyze(
            TextByteSegments.single("Chapter 1\nHello world".toByteArray()),
        )

        assertThat(analysis.isCertain).isFalse()
        assertThat(analysis.candidates.map(EncodingCandidate::charsetName))
            .containsAtLeast("UTF-8", "GB18030", "Big5")
    }

    @Test
    fun `provides readable beginning middle and ending previews for Big5`() {
        val charset = Charset.forName("Big5")
        val analysis = TxtEncodingAnalyzer.analyze(
            TextByteSegments(
                beginning = "開頭：繁體小說".toByteArray(charset),
                middle = "中段：列車抵達".toByteArray(charset),
                end = "結尾：故事完結".toByteArray(charset),
            ),
        )
        val candidate = analysis.candidates.first { it.charsetName == "Big5" }

        assertThat(candidate.preview.beginning).contains("開頭")
        assertThat(candidate.preview.middle).contains("中段")
        assertThat(candidate.preview.end).contains("結尾")
    }

    @Test
    fun `recognizes UTF-16LE without a byte order marker from nul distribution`() {
        val analysis = TxtEncodingAnalyzer.analyze(
            TextByteSegments.single("第一章 UTF16 正文".toByteArray(Charsets.UTF_16LE)),
        )

        assertThat(analysis.recommendedCharsetName).isEqualTo("UTF-16LE")
        assertThat(analysis.isCertain).isTrue()
    }

    @Test
    fun `keeps strictly valid non ascii UTF-8 even when the sample is only unicode whitespace`() {
        val analysis = TxtEncodingAnalyzer.analyze(
            TextByteSegments.single(" \n\t　\n".toByteArray()),
        )

        assertThat(analysis.recommendedCharsetName).isEqualTo("UTF-8")
        assertThat(analysis.isCertain).isTrue()
    }
}
