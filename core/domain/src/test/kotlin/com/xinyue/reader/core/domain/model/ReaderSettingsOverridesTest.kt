package com.xinyue.reader.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderSettingsOverridesTest {
    @Test
    fun `diff stores only normalized appearance differences`() {
        val global = ReaderSettings(
            fontSizeSp = 20f,
            foregroundArgb = 0xFF111111,
            pageAnimation = ReaderPageAnimation.COVER,
        )
        val desired = global.copy(
            fontSizeSp = 28f,
            foregroundArgb = 0xFF222222,
            pageAnimation = ReaderPageAnimation.NONE,
        )

        val diff = ReaderSettingsOverrides.diff(global, desired)

        assertThat(diff.fontSizeSp).isEqualTo(28f)
        assertThat(diff.foregroundArgb).isEqualTo(0xFF222222)
        assertThat(diff.backgroundArgb).isNull()
        assertThat(diff.font).isNull()
        assertThat(global.resolve(diff)).isEqualTo(
            global.copy(fontSizeSp = 28f, foregroundArgb = 0xFF222222),
        )
        assertThat(ReaderSettingsOverrides.diff(global, global))
            .isEqualTo(ReaderSettingsOverrides())
    }

    @Test
    fun `resolves every appearance field independently and preserves global interaction settings`() {
        val global = ReaderSettings(
            font = ReaderFontRef.Imported("font-global"),
            fontWeight = 700,
            fontSizeSp = 22f,
            letterSpacingEm = .03f,
            lineHeightMultiplier = 1.8f,
            paragraphSpacingEm = .7f,
            firstLineIndentEm = 3f,
            alignment = ReaderTextAlignment.JUSTIFY,
            horizontalPaddingDp = 30,
            verticalPaddingDp = 20,
            foregroundArgb = 0xFF102030,
            backgroundArgb = 0xFFF0E0D0,
            warmOverlayArgb = 0xFFCC8844,
            warmOverlayOpacity = .25f,
            focusBand = ReaderFocusBandSettings(true, 4, 0x22334455, .3f),
            colorTheme = ReaderColorTheme.SEPIA,
            pageAnimation = ReaderPageAnimation.COVER,
            keepScreenOn = false,
            volumeKeyPageTurn = false,
            showClock = true,
        )

        val resolved = global.resolve(
            ReaderSettingsOverrides(
                fontSizeSp = 28f,
                backgroundArgb = 0xFF010203,
            ),
        )

        assertThat(resolved).isEqualTo(
            global.copy(fontSizeSp = 28f, backgroundArgb = 0xFF010203),
        )
        assertThat(global.resolve(ReaderSettingsOverrides())).isEqualTo(global)
        assertThat(resolved.resolve(ReaderSettingsOverrides())).isEqualTo(resolved)
    }

    @Test
    fun `normalizes override values after applying them`() {
        val resolved = ReaderSettings().resolve(
            ReaderSettingsOverrides(
                font = ReaderFontRef.Imported(" "),
                fontWeight = 999,
                fontSizeSp = 100f,
                warmOverlayOpacity = -1f,
                focusBand = ReaderFocusBandSettings(visibleLines = 99, opacity = 99f),
            ),
        )

        assertThat(resolved.font).isEqualTo(ReaderFontRef.System)
        assertThat(resolved.fontWeight).isEqualTo(900)
        assertThat(resolved.fontSizeSp).isEqualTo(36f)
        assertThat(resolved.warmOverlayOpacity).isEqualTo(0f)
        assertThat(resolved.focusBand.visibleLines).isEqualTo(8)
        assertThat(resolved.focusBand.opacity).isEqualTo(1f)
    }
}
