package com.xinyue.reader.feature.reader

import android.graphics.Paint
import android.text.style.LineHeightSpan
import kotlin.math.roundToInt

/** Adds space only to the final visual line of one original paragraph. */
class ParagraphSpacingSpan(
    val paragraphEnd: Int,
    spacingPx: Float,
) : LineHeightSpan {
    private val spacingPx = spacingPx.coerceAtLeast(0f).roundToInt()

    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        lineHeight: Int,
        fm: Paint.FontMetricsInt,
    ) {
        if (spacingPx == 0 || end < paragraphEnd) return
        fm.descent += spacingPx
        fm.bottom += spacingPx
    }
}
