package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderColorControlsTest {
    @Test
    fun `hex parser accepts rgb and argb while rejecting malformed values`() {
        assertThat(parseReaderArgb("#112233")).isEqualTo(0xFF112233)
        assertThat(parseReaderArgb("80112233")).isEqualTo(0x80112233)
        assertThat(parseReaderArgb("#xyz")).isNull()
        assertThat(parseReaderArgb("11223")).isNull()
    }

    @Test
    fun `formatted persisted color is fixed width uppercase argb`() {
        assertThat(formatReaderArgb(0x112233)).isEqualTo("#00112233")
        assertThat(formatReaderArgb(0xFFAABBCC)).isEqualTo("#FFAABBCC")
    }

    @Test
    fun `custom reading colors normalize background and composited text to opaque`() {
        val colors = normalizeReaderColors(0x80000000, 0x80FFFFFF)

        assertThat(colors.foregroundArgb).isEqualTo(0xFF7F7F7F)
        assertThat(colors.backgroundArgb).isEqualTo(0xFFFFFFFF)
        assertThat(colors.contrastRatio).isWithin(0.02).of(4.00)
    }

    @Test
    fun `low contrast warns but valid colors do not`() {
        assertThat(readerContrastWarning(0xFF777777, 0xFF777777)).isNotNull()
        assertThat(readerContrastWarning(0xFF000000, 0xFFFFFFFF)).isNull()
    }
}
