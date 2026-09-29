package com.xinyue.reader.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class ReaderColorTheme {
    PAPER,
    SEPIA,
    GREEN,
    DARK,
    OLED_BLACK,
}

@Serializable
sealed interface ReaderFontRef {
    @Serializable
    data object System : ReaderFontRef

    @Serializable
    data object Serif : ReaderFontRef

    @Serializable
    data object SansSerif : ReaderFontRef

    @Serializable
    data class Imported(val fontId: String) : ReaderFontRef
}

@Serializable
enum class ReaderTextAlignment {
    START,
    JUSTIFY,
    CENTER,
}

@Serializable
data class ReaderFocusBandSettings(
    val enabled: Boolean = false,
    val visibleLines: Int = 3,
    val colorArgb: Long = 0x22000000,
    val opacity: Float = 0.14f,
) {
    fun normalized(): ReaderFocusBandSettings = copy(
        visibleLines = visibleLines.coerceIn(MIN_VISIBLE_LINES, MAX_VISIBLE_LINES),
        opacity = opacity.finiteOr(DEFAULT_OPACITY).coerceIn(0f, 1f),
    )

    companion object {
        const val MIN_VISIBLE_LINES = 1
        const val MAX_VISIBLE_LINES = 8
        const val DEFAULT_OPACITY = 0.14f
    }
}

@Serializable
enum class ReaderPageAnimation {
    SLIDE,
    COVER,
    NONE,
}

@Serializable
enum class ReaderTapAction {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    MENU,
    NONE,
}

@Serializable
data class ReaderSettings(
    val font: ReaderFontRef = ReaderFontRef.System,
    val fontWeight: Int = 400,
    val fontSizeSp: Float = 20f,
    val letterSpacingEm: Float = 0f,
    val lineHeightMultiplier: Float = 1.6f,
    val paragraphSpacingEm: Float = 0f,
    val firstLineIndentEm: Float = 0f,
    val alignment: ReaderTextAlignment = ReaderTextAlignment.START,
    val horizontalPaddingDp: Int = 24,
    val verticalPaddingDp: Int = 16,
    val foregroundArgb: Long = 0xFF2B2926,
    val backgroundArgb: Long = 0xFFF6F1E7,
    val warmOverlayArgb: Long = 0xFFFFB35C,
    val warmOverlayOpacity: Float = 0f,
    val focusBand: ReaderFocusBandSettings = ReaderFocusBandSettings(),
    val colorTheme: ReaderColorTheme = ReaderColorTheme.PAPER,
    val brightness: Float = -1f,
    val pageAnimation: ReaderPageAnimation = ReaderPageAnimation.SLIDE,
    val keepScreenOn: Boolean = true,
    val volumeKeyPageTurn: Boolean = true,
    val showBookTitle: Boolean = false,
    val showChapterTitle: Boolean = true,
    val showPageNumber: Boolean = false,
    val showBookProgress: Boolean = true,
    val showChapterProgress: Boolean = false,
    val showClock: Boolean = false,
    val showBattery: Boolean = false,
    val tapZoneActions: List<ReaderTapAction> = DEFAULT_TAP_ZONE_ACTIONS,
) {
    fun normalized(): ReaderSettings = copy(
        font = when (font) {
            is ReaderFontRef.Imported -> font.takeIf { it.fontId.isNotBlank() } ?: ReaderFontRef.System
            else -> font
        },
        fontWeight = ((fontWeight.coerceIn(100, 900) / 100f).roundToInt() * 100)
            .coerceIn(100, 900),
        fontSizeSp = fontSizeSp.finiteOr(20f).coerceIn(14f, 36f),
        letterSpacingEm = letterSpacingEm.finiteOr(0f).coerceIn(-0.05f, 0.20f),
        lineHeightMultiplier = lineHeightMultiplier.finiteOr(1.6f).coerceIn(
            MIN_LINE_HEIGHT_MULTIPLIER,
            MAX_LINE_HEIGHT_MULTIPLIER,
        ),
        paragraphSpacingEm = paragraphSpacingEm.finiteOr(0f).coerceIn(
            MIN_PARAGRAPH_SPACING_EM,
            MAX_PARAGRAPH_SPACING_EM,
        ),
        firstLineIndentEm = firstLineIndentEm.finiteOr(0f).coerceIn(0f, 4f),
        horizontalPaddingDp = horizontalPaddingDp.coerceIn(8, 64),
        verticalPaddingDp = verticalPaddingDp.coerceIn(8, 64),
        brightness = when {
            !brightness.isFinite() -> -1f
            brightness < 0f -> -1f
            else -> brightness.coerceIn(0.02f, 1f)
        },
        warmOverlayOpacity = warmOverlayOpacity.finiteOr(0f).coerceIn(0f, 1f),
        focusBand = focusBand.normalized(),
        tapZoneActions = tapZoneActions.takeIf { it.size == TAP_ZONE_COUNT }
            ?: DEFAULT_TAP_ZONE_ACTIONS,
    )

    fun resolve(overrides: ReaderSettingsOverrides): ReaderSettings {
        val global = normalized()
        return global.copy(
            font = overrides.font ?: global.font,
            fontWeight = overrides.fontWeight ?: global.fontWeight,
            fontSizeSp = overrides.fontSizeSp ?: global.fontSizeSp,
            letterSpacingEm = overrides.letterSpacingEm ?: global.letterSpacingEm,
            lineHeightMultiplier = overrides.lineHeightMultiplier ?: global.lineHeightMultiplier,
            paragraphSpacingEm = overrides.paragraphSpacingEm ?: global.paragraphSpacingEm,
            firstLineIndentEm = overrides.firstLineIndentEm ?: global.firstLineIndentEm,
            alignment = overrides.alignment ?: global.alignment,
            horizontalPaddingDp = overrides.horizontalPaddingDp ?: global.horizontalPaddingDp,
            verticalPaddingDp = overrides.verticalPaddingDp ?: global.verticalPaddingDp,
            foregroundArgb = overrides.foregroundArgb ?: global.foregroundArgb,
            backgroundArgb = overrides.backgroundArgb ?: global.backgroundArgb,
            warmOverlayArgb = overrides.warmOverlayArgb ?: global.warmOverlayArgb,
            warmOverlayOpacity = overrides.warmOverlayOpacity ?: global.warmOverlayOpacity,
            focusBand = overrides.focusBand ?: global.focusBand,
        ).normalized()
    }

    companion object {
        const val MIN_LINE_HEIGHT_MULTIPLIER = 1.2f
        const val MAX_LINE_HEIGHT_MULTIPLIER = 2.4f
        const val MIN_PARAGRAPH_SPACING_EM = 0f
        const val MAX_PARAGRAPH_SPACING_EM = 1f
        const val TAP_ZONE_COUNT = 9

        val DEFAULT_TAP_ZONE_ACTIONS = listOf(
            ReaderTapAction.PREVIOUS_PAGE,
            ReaderTapAction.MENU,
            ReaderTapAction.NEXT_PAGE,
            ReaderTapAction.PREVIOUS_PAGE,
            ReaderTapAction.MENU,
            ReaderTapAction.NEXT_PAGE,
            ReaderTapAction.PREVIOUS_PAGE,
            ReaderTapAction.MENU,
            ReaderTapAction.NEXT_PAGE,
        )
    }
}

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
