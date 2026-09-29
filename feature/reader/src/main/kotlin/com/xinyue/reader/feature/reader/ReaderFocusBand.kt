package com.xinyue.reader.feature.reader

import com.xinyue.reader.core.domain.model.ReaderFocusBandSettings
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class ReaderLineBounds(
    val topPx: Float,
    val bottomPx: Float,
)

internal data class ReaderFocusBandRect(
    val topPx: Float,
    val bottomPx: Float,
)

internal fun calculateReaderFocusBandRect(
    enabled: Boolean,
    visibleLines: Int,
    viewportHeightPx: Float,
    lines: List<ReaderLineBounds>,
): ReaderFocusBandRect? {
    if (!enabled || !viewportHeightPx.isFinite() || viewportHeightPx <= 0f) return null
    val visible = lines.mapNotNull { line ->
        if (!line.topPx.isFinite() || !line.bottomPx.isFinite() || line.bottomPx <= line.topPx) {
            null
        } else {
            val top = line.topPx.coerceIn(0f, viewportHeightPx)
            val bottom = line.bottomPx.coerceIn(0f, viewportHeightPx)
            ReaderLineBounds(top, bottom).takeIf { it.bottomPx > it.topPx }
        }
    }
    if (visible.isEmpty()) return null
    val count = visibleLines.coerceIn(
        ReaderFocusBandSettings.MIN_VISIBLE_LINES,
        ReaderFocusBandSettings.MAX_VISIBLE_LINES,
    ).coerceAtMost(visible.size)
    val viewportCenter = viewportHeightPx / 2f
    val start = (0..visible.size - count).minByOrNull { candidate ->
        val top = visible[candidate].topPx
        val bottom = visible[candidate + count - 1].bottomPx
        abs((top + bottom) / 2f - viewportCenter)
    } ?: return null
    return ReaderFocusBandRect(
        topPx = visible[start].topPx,
        bottomPx = visible[start + count - 1].bottomPx,
    )
}

internal fun ReaderFocusBandSettings.effectiveColorArgb(): Int {
    val normalized = normalized()
    val configuredAlpha = ((normalized.colorArgb ushr 24) and 0xFF).toInt()
    val effectiveAlpha = (configuredAlpha * normalized.opacity).roundToInt().coerceIn(0, 255)
    return (normalized.colorArgb.toInt() and 0x00FFFFFF) or (effectiveAlpha shl 24)
}
