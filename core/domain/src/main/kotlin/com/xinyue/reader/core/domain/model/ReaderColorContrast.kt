package com.xinyue.reader.core.domain.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class ReaderColorContrastResult(
    val ratio: Double,
    val opaqueForegroundArgb: Long,
    val opaqueBackgroundArgb: Long,
) {
    val isLowContrast: Boolean
        get() = ratio < ReaderColorContrast.WARNING_THRESHOLD
}

/** Pure sRGB contrast and alpha-composition helpers for persisted 32-bit ARGB colors. */
object ReaderColorContrast {
    const val NORMAL_TEXT_MINIMUM: Double = 4.5
    const val LARGE_TEXT_MINIMUM: Double = 3.0
    const val WARNING_THRESHOLD: Double = NORMAL_TEXT_MINIMUM
    private const val LIGHT_LUMINANCE_THRESHOLD = 0.5
    private const val CHANNEL_MAX = 255.0

    fun evaluate(foregroundArgb: Long, backgroundArgb: Long): ReaderColorContrastResult {
        val background = opaque(backgroundArgb)
        val foreground = composite(foregroundArgb, background)
        val foregroundLuminance = relativeLuminance(foreground)
        val backgroundLuminance = relativeLuminance(background)
        val lighter = max(foregroundLuminance, backgroundLuminance)
        val darker = min(foregroundLuminance, backgroundLuminance)
        return ReaderColorContrastResult(
            ratio = (lighter + 0.05) / (darker + 0.05),
            opaqueForegroundArgb = foreground,
            opaqueBackgroundArgb = background,
        )
    }

    fun meetsMinimum(ratio: Double, largeText: Boolean): Boolean =
        ratio.isFinite() && ratio >= if (largeText) LARGE_TEXT_MINIMUM else NORMAL_TEXT_MINIMUM

    fun opaque(argb: Long): Long = OPAQUE_ALPHA or (argb and RGB_MASK)

    fun composite(
        foregroundArgb: Long,
        backgroundArgb: Long,
        opacity: Double = 1.0,
    ): Long {
        val background = opaque(backgroundArgb)
        val safeOpacity = opacity.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0
        val sourceAlpha = channel(foregroundArgb, 24) / CHANNEL_MAX * safeOpacity
        fun compositeChannel(shift: Int): Long {
            val source = channel(foregroundArgb, shift)
            val destination = channel(background, shift)
            return (source * sourceAlpha + destination * (1.0 - sourceAlpha))
                .roundToInt()
                .coerceIn(0, 255)
                .toLong()
        }
        return OPAQUE_ALPHA or
            (compositeChannel(16) shl 16) or
            (compositeChannel(8) shl 8) or
            compositeChannel(0)
    }

    fun relativeLuminance(argb: Long): Double {
        fun linear(shift: Int): Double {
            val srgb = channel(argb, shift) / CHANNEL_MAX
            return if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
    }

    fun isLight(argb: Long): Boolean = relativeLuminance(opaque(argb)) >= LIGHT_LUMINANCE_THRESHOLD

    private fun channel(argb: Long, shift: Int): Double = ((argb ushr shift) and 0xFF).toDouble()

    private const val OPAQUE_ALPHA = 0xFF000000L
    private const val RGB_MASK = 0x00FFFFFFL
}
