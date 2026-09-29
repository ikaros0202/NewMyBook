package com.xinyue.reader.feature.reader

import com.xinyue.reader.core.domain.model.ReaderSettings

enum class GlobalReaderSettingsSection(val title: String) {
    APPEARANCE("全局阅读外观"),
    THEMES("主题与自动切换"),
    FONTS("导入字体"),
    TYPOGRAPHY("精细排版与专注带"),
    BEHAVIOR("阅读行为"),
    INFORMATION("阅读信息"),
}

fun mergeGlobalReaderSettings(
    section: GlobalReaderSettingsSection,
    latest: ReaderSettings,
    edited: ReaderSettings,
): ReaderSettings = when (section) {
    GlobalReaderSettingsSection.APPEARANCE -> latest.copy(
        font = edited.font,
        fontSizeSp = edited.fontSizeSp,
        lineHeightMultiplier = edited.lineHeightMultiplier,
        horizontalPaddingDp = edited.horizontalPaddingDp,
        verticalPaddingDp = edited.verticalPaddingDp,
        foregroundArgb = edited.foregroundArgb,
        backgroundArgb = edited.backgroundArgb,
        colorTheme = edited.colorTheme,
        brightness = edited.brightness,
    )
    GlobalReaderSettingsSection.TYPOGRAPHY -> latest.copy(
        fontWeight = edited.fontWeight,
        letterSpacingEm = edited.letterSpacingEm,
        paragraphSpacingEm = edited.paragraphSpacingEm,
        firstLineIndentEm = edited.firstLineIndentEm,
        alignment = edited.alignment,
        foregroundArgb = edited.foregroundArgb,
        backgroundArgb = edited.backgroundArgb,
        warmOverlayArgb = edited.warmOverlayArgb,
        warmOverlayOpacity = edited.warmOverlayOpacity,
        focusBand = edited.focusBand,
    )
    GlobalReaderSettingsSection.BEHAVIOR -> latest.copy(
        pageAnimation = edited.pageAnimation,
        keepScreenOn = edited.keepScreenOn,
        volumeKeyPageTurn = edited.volumeKeyPageTurn,
        tapZoneActions = edited.tapZoneActions,
    )
    GlobalReaderSettingsSection.INFORMATION -> latest.copy(
        showBookTitle = edited.showBookTitle,
        showChapterTitle = edited.showChapterTitle,
        showPageNumber = edited.showPageNumber,
        showBookProgress = edited.showBookProgress,
        showChapterProgress = edited.showChapterProgress,
        showClock = edited.showClock,
        showBattery = edited.showBattery,
    )
    GlobalReaderSettingsSection.THEMES,
    GlobalReaderSettingsSection.FONTS,
    -> latest
}.normalized()
