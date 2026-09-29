package com.xinyue.reader.feature.reader

import com.xinyue.reader.core.domain.model.BUILT_IN_DARK_ID
import com.xinyue.reader.core.domain.model.BUILT_IN_GREEN_ID
import com.xinyue.reader.core.domain.model.BUILT_IN_OLED_ID
import com.xinyue.reader.core.domain.model.BUILT_IN_PAPER_ID
import com.xinyue.reader.core.domain.model.BUILT_IN_SEPIA_ID
import com.xinyue.reader.core.domain.model.ReaderColorTheme
import com.xinyue.reader.core.domain.model.ReaderSettings

/** The single palette definition shared by presets and both appearance editors. */
internal data class ReaderBuiltInPalette(
    val id: String,
    val name: String,
    val theme: ReaderColorTheme,
    val foregroundArgb: Long,
    val backgroundArgb: Long,
)

internal val READER_BUILT_IN_PALETTES: List<ReaderBuiltInPalette> = listOf(
    ReaderBuiltInPalette(
        id = BUILT_IN_PAPER_ID,
        name = "纸白",
        theme = ReaderColorTheme.PAPER,
        foregroundArgb = 0xFF2B2926L,
        backgroundArgb = 0xFFF6F1E7L,
    ),
    ReaderBuiltInPalette(
        id = BUILT_IN_SEPIA_ID,
        name = "米黄",
        theme = ReaderColorTheme.SEPIA,
        foregroundArgb = 0xFF40362BL,
        backgroundArgb = 0xFFF4E8CEL,
    ),
    ReaderBuiltInPalette(
        id = BUILT_IN_GREEN_ID,
        name = "护眼",
        theme = ReaderColorTheme.GREEN,
        foregroundArgb = 0xFF25352BL,
        backgroundArgb = 0xFFDDEBD9L,
    ),
    ReaderBuiltInPalette(
        id = BUILT_IN_DARK_ID,
        name = "深灰",
        theme = ReaderColorTheme.DARK,
        foregroundArgb = 0xFFE5E1DCL,
        backgroundArgb = 0xFF252423L,
    ),
    ReaderBuiltInPalette(
        id = BUILT_IN_OLED_ID,
        name = "OLED 黑",
        theme = ReaderColorTheme.OLED_BLACK,
        foregroundArgb = 0xFFE6E6E6L,
        backgroundArgb = 0xFF000000L,
    ),
)

internal fun ReaderColorTheme.builtInPalette(): ReaderBuiltInPalette =
    READER_BUILT_IN_PALETTES.first { it.theme == this }

internal fun ReaderSettings.withBuiltInPalette(theme: ReaderColorTheme): ReaderSettings {
    val palette = theme.builtInPalette()
    return copy(
        colorTheme = palette.theme,
        foregroundArgb = palette.foregroundArgb,
        backgroundArgb = palette.backgroundArgb,
    )
}

internal fun ReaderSettings.matchesBuiltInPalette(theme: ReaderColorTheme): Boolean {
    val palette = theme.builtInPalette()
    return colorTheme == palette.theme &&
        foregroundArgb == palette.foregroundArgb &&
        backgroundArgb == palette.backgroundArgb
}
