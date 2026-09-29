package com.xinyue.reader.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics

/** A draw-only overlay: it deliberately owns no pointer, click, focus, or accessibility action. */
@Composable
internal fun ReaderWarmOverlay(
    colorArgb: Long,
    opacity: Float,
    modifier: Modifier = Modifier,
) {
    val safeOpacity = opacity.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    if (safeOpacity <= 0f) return
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        drawRect(color = Color(colorArgb.toInt()), alpha = safeOpacity)
    }
}
