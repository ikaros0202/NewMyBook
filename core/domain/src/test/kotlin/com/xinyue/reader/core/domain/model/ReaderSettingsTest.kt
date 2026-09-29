package com.xinyue.reader.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderSettingsTest {
    @Test
    fun `v1 exposes exactly five comfortable built in color themes`() {
        assertThat(ReaderColorTheme.entries).containsExactly(
            ReaderColorTheme.PAPER,
            ReaderColorTheme.SEPIA,
            ReaderColorTheme.GREEN,
            ReaderColorTheme.DARK,
            ReaderColorTheme.OLED_BLACK,
        ).inOrder()
    }

    @Test
    fun `normalizes comfort controls to safe ranges without losing mode choices`() {
        val settings = ReaderSettings(
            font = ReaderFontRef.Serif,
            fontWeight = 949,
            fontSizeSp = 80f,
            letterSpacingEm = 1f,
            lineHeightMultiplier = 0.5f,
            paragraphSpacingEm = 9f,
            firstLineIndentEm = 9f,
            horizontalPaddingDp = 100,
            verticalPaddingDp = 100,
            brightness = 2f,
            alignment = ReaderTextAlignment.JUSTIFY,
            warmOverlayOpacity = 2f,
            focusBand = ReaderFocusBandSettings(
                enabled = true,
                visibleLines = 20,
                opacity = -2f,
            ),
            pageAnimation = ReaderPageAnimation.COVER,
            keepScreenOn = false,
            volumeKeyPageTurn = false,
            showBookTitle = true,
            showChapterTitle = true,
            showPageNumber = true,
            showBookProgress = false,
            showChapterProgress = true,
            showClock = true,
            showBattery = true,
            tapZoneActions = listOf(ReaderTapAction.NONE),
        ).normalized()

        assertThat(settings.font).isEqualTo(ReaderFontRef.Serif)
        assertThat(settings.fontWeight).isEqualTo(900)
        assertThat(settings.fontSizeSp).isEqualTo(36f)
        assertThat(settings.letterSpacingEm).isEqualTo(.2f)
        assertThat(settings.lineHeightMultiplier).isEqualTo(1.2f)
        assertThat(settings.paragraphSpacingEm).isEqualTo(1f)
        assertThat(settings.firstLineIndentEm).isEqualTo(4f)
        assertThat(settings.horizontalPaddingDp).isEqualTo(64)
        assertThat(settings.verticalPaddingDp).isEqualTo(64)
        assertThat(settings.brightness).isEqualTo(1f)
        assertThat(settings.alignment).isEqualTo(ReaderTextAlignment.JUSTIFY)
        assertThat(settings.warmOverlayOpacity).isEqualTo(1f)
        assertThat(settings.focusBand.visibleLines).isEqualTo(8)
        assertThat(settings.focusBand.opacity).isEqualTo(0f)
        assertThat(settings.pageAnimation).isEqualTo(ReaderPageAnimation.COVER)
        assertThat(settings.keepScreenOn).isFalse()
        assertThat(settings.volumeKeyPageTurn).isFalse()
        assertThat(settings.showBookTitle).isTrue()
        assertThat(settings.showChapterTitle).isTrue()
        assertThat(settings.showPageNumber).isTrue()
        assertThat(settings.showBookProgress).isFalse()
        assertThat(settings.showChapterProgress).isTrue()
        assertThat(settings.showClock).isTrue()
        assertThat(settings.showBattery).isTrue()
        assertThat(settings.tapZoneActions).containsExactlyElementsIn(ReaderSettings.DEFAULT_TAP_ZONE_ACTIONS).inOrder()
    }

    @Test
    fun `keeps minus one as system brightness`() {
        assertThat(ReaderSettings(brightness = -1f).normalized().brightness).isEqualTo(-1f)
    }

    @Test
    fun `normalizes every lower boundary and weight step`() {
        val settings = ReaderSettings(
            fontWeight = 449,
            fontSizeSp = -1f,
            letterSpacingEm = -1f,
            lineHeightMultiplier = -1f,
            paragraphSpacingEm = -1f,
            firstLineIndentEm = -1f,
            horizontalPaddingDp = -1,
            verticalPaddingDp = -1,
            warmOverlayOpacity = -1f,
            focusBand = ReaderFocusBandSettings(visibleLines = -1, opacity = 2f),
        ).normalized()

        assertThat(settings.fontWeight).isEqualTo(400)
        assertThat(settings.fontSizeSp).isEqualTo(14f)
        assertThat(settings.letterSpacingEm).isEqualTo(-.05f)
        assertThat(settings.lineHeightMultiplier).isEqualTo(1.2f)
        assertThat(settings.paragraphSpacingEm).isEqualTo(0f)
        assertThat(settings.firstLineIndentEm).isEqualTo(0f)
        assertThat(settings.horizontalPaddingDp).isEqualTo(8)
        assertThat(settings.verticalPaddingDp).isEqualTo(8)
        assertThat(settings.warmOverlayOpacity).isEqualTo(0f)
        assertThat(settings.focusBand.visibleLines).isEqualTo(1)
        assertThat(settings.focusBand.opacity).isEqualTo(1f)
    }

    @Test
    fun `corrupt non finite settings fall back without throwing`() {
        val settings = ReaderSettings(
            font = ReaderFontRef.Imported("  "),
            fontSizeSp = Float.NaN,
            letterSpacingEm = Float.POSITIVE_INFINITY,
            lineHeightMultiplier = Float.NEGATIVE_INFINITY,
            paragraphSpacingEm = Float.NaN,
            firstLineIndentEm = Float.NaN,
            brightness = Float.NaN,
            warmOverlayOpacity = Float.NaN,
            focusBand = ReaderFocusBandSettings(opacity = Float.NaN),
        ).normalized()

        assertThat(settings).isEqualTo(ReaderSettings())
    }

    @Test
    fun `new settings default to zero indent while explicit persisted indent remains intact`() {
        assertThat(ReaderSettings().firstLineIndentEm).isEqualTo(0f)
        assertThat(ReaderSettings(firstLineIndentEm = 2f).normalized().firstLineIndentEm)
            .isEqualTo(2f)
        assertThat(ReaderSettings(firstLineIndentEm = Float.NaN).normalized().firstLineIndentEm)
            .isEqualTo(0f)
    }

    @Test
    fun `line height clamps to the safe minimum and keeps the maximum`() {
        assertThat(ReaderSettings(lineHeightMultiplier = 0.8f).normalized().lineHeightMultiplier)
            .isEqualTo(1.2f)
        assertThat(ReaderSettings(lineHeightMultiplier = 2.4f).normalized().lineHeightMultiplier)
            .isEqualTo(2.4f)
    }

    @Test
    fun `default tap mapping exposes a complete nine zone reading layout`() {
        assertThat(ReaderSettings.DEFAULT_TAP_ZONE_ACTIONS).containsExactly(
            ReaderTapAction.PREVIOUS_PAGE,
            ReaderTapAction.MENU,
            ReaderTapAction.NEXT_PAGE,
            ReaderTapAction.PREVIOUS_PAGE,
            ReaderTapAction.MENU,
            ReaderTapAction.NEXT_PAGE,
            ReaderTapAction.PREVIOUS_PAGE,
            ReaderTapAction.MENU,
            ReaderTapAction.NEXT_PAGE,
        ).inOrder()
    }
}
