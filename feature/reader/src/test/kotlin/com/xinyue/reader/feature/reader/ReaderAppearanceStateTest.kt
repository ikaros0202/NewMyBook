package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.ReaderFocusBandSettings
import com.xinyue.reader.core.domain.model.ReaderSettings
import org.junit.Test

class ReaderAppearanceStateTest {
    @Test
    fun `book overrides contain only appearance values that differ from global settings`() {
        val global = ReaderSettings(
            fontSizeSp = 20f,
            lineHeightMultiplier = 1.6f,
            brightness = 0.4f,
            keepScreenOn = true,
        )
        val desired = global.copy(
            fontSizeSp = 24f,
            warmOverlayOpacity = 0.2f,
            focusBand = ReaderFocusBandSettings(enabled = true),
            brightness = 0.8f,
            keepScreenOn = false,
        )

        val overrides = desired.appearanceOverridesComparedWith(global)

        assertThat(overrides.fontSizeSp).isEqualTo(24f)
        assertThat(overrides.lineHeightMultiplier).isNull()
        assertThat(overrides.warmOverlayOpacity).isEqualTo(0.2f)
        assertThat(overrides.focusBand?.enabled).isTrue()
        assertThat(global.resolve(overrides).brightness).isEqualTo(global.brightness)
        assertThat(global.resolve(overrides).keepScreenOn).isEqualTo(global.keepScreenOn)
    }

    @Test
    fun `edit reports dirty only when normalized preview differs from original`() {
        val original = ReaderSettings(fontSizeSp = 20f)
        val unchanged = ReaderAppearanceEdit(
            scope = ReaderSettingsScope.GLOBAL,
            original = original,
            preview = original.copy(fontSizeSp = 20f),
            originalStableAnchorOffset = 120,
        )
        val changed = unchanged.copy(preview = original.copy(fontSizeSp = 25f))

        assertThat(unchanged.isDirty).isFalse()
        assertThat(changed.isDirty).isTrue()
    }

    @Test
    fun `clean scope switch loads the target saved appearance instead of copying the previous scope`() {
        val global = ReaderSettings(fontSizeSp = 20f, firstLineIndentEm = 0f)
        val currentBook = ReaderSettings(fontSizeSp = 31f, firstLineIndentEm = 2f)
        val edit = ReaderAppearanceEdit(
            scope = ReaderSettingsScope.GLOBAL,
            original = global,
            preview = global,
            originalStableAnchorOffset = 120,
        )

        val switched = edit.changeScope(ReaderSettingsScope.CURRENT_BOOK, currentBook)

        assertThat(switched.original).isEqualTo(currentBook)
        assertThat(switched.preview).isEqualTo(currentBook)
        assertThat(switched.isDirty).isFalse()
    }

    @Test
    fun `line spacing uses the persisted minimum multiplier in the layout spec`() {
        val spec = ReaderLayoutSpec(
            widthPx = 320,
            heightPx = 480,
            fontSizePx = 30f,
            lineHeightPx = 48f,
        )

        val compact = spec.withAppearance(
            previous = ReaderSettings(fontSizeSp = 20f, lineHeightMultiplier = 1.6f),
            next = ReaderSettings(fontSizeSp = 20f, lineHeightMultiplier = 1.2f),
        )

        assertThat(compact.lineHeightPx).isEqualTo(36f)
    }

    @Test
    fun `line spacing normalizes a persisted value below the minimum before layout`() {
        val invalid = ReaderSettings(fontSizeSp = 20f, lineHeightMultiplier = 0.8f)

        assertThat(invalid.normalized().lineHeightMultiplier).isEqualTo(1.2f)

        val spec = ReaderLayoutSpec(
            widthPx = 320,
            heightPx = 480,
            fontSizePx = 48f,
            lineHeightPx = 76.8f,
        )
        val normalized = spec.withAppearance(previous = invalid, next = invalid)

        assertThat(normalized.lineHeightPx).isWithin(0.001f).of(57.6f)
    }

    @Test
    fun `ARGB parser accepts exactly eight hexadecimal digits`() {
        assertThat(parseArgbHex("#FF2B2926")).isEqualTo(0xFF2B2926)
        assertThat(parseArgbHex("ff000000")).isEqualTo(0xFF000000)
        assertThat(parseArgbHex("123456")).isNull()
        assertThat(parseArgbHex("GG000000")).isNull()
    }
}
