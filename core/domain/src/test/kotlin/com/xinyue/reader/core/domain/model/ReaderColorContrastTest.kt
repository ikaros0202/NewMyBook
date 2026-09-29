package com.xinyue.reader.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderColorContrastTest {
    @Test
    fun `black on white has maximum contrast`() {
        val result = ReaderColorContrast.evaluate(0xFF000000, 0xFFFFFFFF)

        assertThat(result.ratio).isWithin(0.001).of(21.0)
        assertThat(result.isLowContrast).isFalse()
    }

    @Test
    fun `identical colors have one to one contrast`() {
        val result = ReaderColorContrast.evaluate(0xFF336699, 0xFF336699)

        assertThat(result.ratio).isWithin(0.001).of(1.0)
        assertThat(result.isLowContrast).isTrue()
    }

    @Test
    fun `translucent foreground is composited over an opaque background`() {
        val result = ReaderColorContrast.evaluate(0x80000000, 0x80FFFFFF)

        assertThat(result.opaqueBackgroundArgb).isEqualTo(0xFFFFFFFF)
        assertThat(result.opaqueForegroundArgb).isEqualTo(0xFF7F7F7F)
        assertThat(result.ratio).isWithin(0.02).of(4.00)
    }

    @Test
    fun `all five built in reading themes clear the warning threshold`() {
        val builtIns = listOf(
            0xFF2B2926 to 0xFFF6F1E7,
            0xFF40362B to 0xFFF4E8CE,
            0xFF25352B to 0xFFDDEBD9,
            0xFFE5E1DC to 0xFF252423,
            0xFFE6E6E6 to 0xFF000000,
        )

        builtIns.forEach { (foreground, background) ->
            assertThat(ReaderColorContrast.evaluate(foreground, background).ratio)
                .isAtLeast(ReaderColorContrast.WARNING_THRESHOLD)
        }
    }

    @Test
    fun `normal and large text minimums include their exact boundary`() {
        assertThat(ReaderColorContrast.meetsMinimum(4.5, largeText = false)).isTrue()
        assertThat(ReaderColorContrast.meetsMinimum(4.499, largeText = false)).isFalse()
        assertThat(ReaderColorContrast.meetsMinimum(3.0, largeText = true)).isTrue()
        assertThat(ReaderColorContrast.meetsMinimum(2.999, largeText = true)).isFalse()
    }

    @Test
    fun `warm overlay produces an opaque final background`() {
        val finalBackground = ReaderColorContrast.composite(
            foregroundArgb = 0xFFFF8000,
            backgroundArgb = 0xFF000000,
            opacity = 0.25,
        )

        assertThat(finalBackground).isEqualTo(0xFF402000)
        assertThat(ReaderColorContrast.isLight(finalBackground)).isFalse()
    }
}
