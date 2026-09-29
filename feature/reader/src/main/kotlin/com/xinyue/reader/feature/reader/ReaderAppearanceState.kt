package com.xinyue.reader.feature.reader

import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides

enum class ReaderSettingsScope {
    GLOBAL,
    CURRENT_BOOK,
}

data class ReaderAppearanceEdit(
    val scope: ReaderSettingsScope,
    val original: ReaderSettings,
    val preview: ReaderSettings,
    val originalStableAnchorOffset: Int,
) {
    val isDirty: Boolean
        get() = original.normalized() != preview.normalized()
}

internal fun ReaderAppearanceEdit.changeScope(
    targetScope: ReaderSettingsScope,
    targetOriginal: ReaderSettings,
): ReaderAppearanceEdit {
    val normalizedOriginal = targetOriginal.normalized()
    return copy(
        scope = targetScope,
        original = normalizedOriginal,
        preview = if (isDirty) {
            preview.withGlobalBehaviorFrom(normalizedOriginal)
        } else {
            normalizedOriginal
        },
    )
}

/**
 * Produces the sparse, appearance-only delta stored for a single book.
 * Interaction and information-bar settings deliberately remain global.
 */
internal fun ReaderSettings.appearanceOverridesComparedWith(
    globalSettings: ReaderSettings,
): ReaderSettingsOverrides = ReaderSettingsOverrides.diff(globalSettings, this)

/** Copies only settings that are intentionally global across all books. */
internal fun ReaderSettings.withGlobalBehaviorFrom(source: ReaderSettings): ReaderSettings {
    val behavior = source.normalized()
    return copy(
        brightness = behavior.brightness,
        pageAnimation = behavior.pageAnimation,
        keepScreenOn = behavior.keepScreenOn,
        volumeKeyPageTurn = behavior.volumeKeyPageTurn,
        showBookTitle = behavior.showBookTitle,
        showChapterTitle = behavior.showChapterTitle,
        showPageNumber = behavior.showPageNumber,
        showBookProgress = behavior.showBookProgress,
        showChapterProgress = behavior.showChapterProgress,
        showClock = behavior.showClock,
        showBattery = behavior.showBattery,
        tapZoneActions = behavior.tapZoneActions,
    ).normalized()
}

internal fun ReaderLayoutSpec.withAppearance(
    previous: ReaderSettings,
    next: ReaderSettings,
): ReaderLayoutSpec {
    val before = previous.normalized()
    val after = next.normalized()
    val pixelsPerSp = (fontSizePx / before.fontSizeSp).takeIf { it.isFinite() && it > 0f } ?: 1f
    val nextFontSizePx = (after.fontSizeSp * pixelsPerSp).coerceAtLeast(1f)
    return copy(
        fontSizePx = nextFontSizePx,
        lineHeightPx = nextFontSizePx * after.lineHeightMultiplier,
        font = after.font,
        fontWeight = after.fontWeight,
        letterSpacingEm = after.letterSpacingEm,
        paragraphSpacingPx = nextFontSizePx * after.paragraphSpacingEm,
        firstLineIndentPx = nextFontSizePx * after.firstLineIndentEm,
        alignment = after.alignment,
    )
}
