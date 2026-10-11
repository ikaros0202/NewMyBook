package com.xinyue.reader.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A compact reader setting slider that keeps Material's range and discrete-step semantics.
 * The visual track is deliberately quieter than the default tuning-panel treatment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val activeColor = if (enabled) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.38f)
    val inactiveColor = if (enabled) {
        colors.outlineVariant
    } else {
        colors.onSurface.copy(alpha = 0.12f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Slider(
            value = value.coerceIn(valueRange),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription?.let { this.contentDescription = it } },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(activeColor, CircleShape),
                )
            },
            track = { sliderState ->
                Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                    val trackHeight = 4.dp.toPx()
                    val centerY = size.height / 2f
                    val fraction = ((sliderState.value - valueRange.start) /
                        (valueRange.endInclusive - valueRange.start).coerceAtLeast(Float.MIN_VALUE))
                        .coerceIn(0f, 1f)
                    val activeWidth = size.width * fraction
                    drawRoundRect(
                        color = inactiveColor,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2f),
                        size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
                    )
                    if (activeWidth > 0f) {
                        drawRoundRect(
                            color = activeColor,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2f),
                            size = androidx.compose.ui.geometry.Size(activeWidth, trackHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
                        )
                    }
                }
            },
        )
    }
}
