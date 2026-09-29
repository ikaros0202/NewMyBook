package com.xinyue.reader.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReaderSettingsOverrides(
    val font: ReaderFontRef? = null,
    val fontWeight: Int? = null,
    val fontSizeSp: Float? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightMultiplier: Float? = null,
    val paragraphSpacingEm: Float? = null,
    val firstLineIndentEm: Float? = null,
    val alignment: ReaderTextAlignment? = null,
    val horizontalPaddingDp: Int? = null,
    val verticalPaddingDp: Int? = null,
    val foregroundArgb: Long? = null,
    val backgroundArgb: Long? = null,
    val warmOverlayArgb: Long? = null,
    val warmOverlayOpacity: Float? = null,
    val focusBand: ReaderFocusBandSettings? = null,
) {
    companion object {
        /** Returns a sparse appearance-only delta from [global] to [desired]. */
        fun diff(global: ReaderSettings, desired: ReaderSettings): ReaderSettingsOverrides {
            val base = global.normalized()
            val target = desired.normalized()
            return ReaderSettingsOverrides(
                font = target.font.takeIf { it != base.font },
                fontWeight = target.fontWeight.takeIf { it != base.fontWeight },
                fontSizeSp = target.fontSizeSp.takeIf { it != base.fontSizeSp },
                letterSpacingEm = target.letterSpacingEm.takeIf { it != base.letterSpacingEm },
                lineHeightMultiplier = target.lineHeightMultiplier.takeIf { it != base.lineHeightMultiplier },
                paragraphSpacingEm = target.paragraphSpacingEm.takeIf { it != base.paragraphSpacingEm },
                firstLineIndentEm = target.firstLineIndentEm.takeIf { it != base.firstLineIndentEm },
                alignment = target.alignment.takeIf { it != base.alignment },
                horizontalPaddingDp = target.horizontalPaddingDp.takeIf { it != base.horizontalPaddingDp },
                verticalPaddingDp = target.verticalPaddingDp.takeIf { it != base.verticalPaddingDp },
                foregroundArgb = target.foregroundArgb.takeIf { it != base.foregroundArgb },
                backgroundArgb = target.backgroundArgb.takeIf { it != base.backgroundArgb },
                warmOverlayArgb = target.warmOverlayArgb.takeIf { it != base.warmOverlayArgb },
                warmOverlayOpacity = target.warmOverlayOpacity.takeIf { it != base.warmOverlayOpacity },
                focusBand = target.focusBand.takeIf { it != base.focusBand },
            )
        }
    }
}
