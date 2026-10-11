package com.xinyue.reader.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class CoverPalette(
    val paper: Color,
    val ink: Color,
    val accent: Color,
)

private val coverPalettes = listOf(
    CoverPalette(
        paper = Color(0xFFF3E7D3),
        ink = Color(0xFF342B24),
        accent = Color(0xFFB47B4F),
    ),
    CoverPalette(
        paper = Color(0xFF203247),
        ink = Color(0xFFF2E8D8),
        accent = Color(0xFFE0B58F),
    ),
    CoverPalette(
        paper = Color(0xFF9E4E45),
        ink = Color(0xFFF6E7D4),
        accent = Color(0xFFE9BBA0),
    ),
    CoverPalette(
        paper = Color(0xFFA56A45),
        ink = Color(0xFFF7E9D4),
        accent = Color(0xFF55382B),
    ),
    CoverPalette(
        paper = Color(0xFF34373A),
        ink = Color(0xFFF5E9D8),
        accent = Color(0xFFD6AF83),
    ),
    CoverPalette(
        paper = Color(0xFF5A4B5E),
        ink = Color(0xFFF2E8D8),
        accent = Color(0xFFD4B6A2),
    ),
)

@Composable
fun GeneratedBookCover(
    title: String,
    modifier: Modifier = Modifier,
) {
    val palette = remember(title) {
        coverPalettes[Math.floorMod(title.hashCode(), coverPalettes.size)]
    }
    val label = remember(title) { bookCoverLabel(title) }
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(palette.paper)
            .border(1.dp, palette.accent.copy(alpha = 0.48f), RoundedCornerShape(4.dp))
            .semantics { contentDescription = "《$title》的默认封面" },
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxWidth <= 64.dp
        val titleSize = coverTitleSizeSp(label, maxWidth.value, compact)
        Canvas(Modifier.fillMaxSize()) {
            val spineX = size.width * 0.13f
            drawLine(
                color = palette.accent.copy(alpha = 0.66f),
                start = Offset(spineX, 0f),
                end = Offset(spineX, size.height),
                strokeWidth = maxOf(1f, size.width * 0.012f),
            )
            drawLine(
                color = palette.accent.copy(alpha = 0.28f),
                start = Offset(spineX + maxOf(2f, size.width * 0.025f), 0f),
                end = Offset(spineX + maxOf(2f, size.width * 0.025f), size.height),
                strokeWidth = maxOf(1f, size.width * 0.006f),
            )
            for (line in 0 until 4) {
                val topLine = line < 2
                val lineInBand = line % 2
                val y = if (topLine) {
                    size.height * (0.13f + lineInBand * 0.055f)
                } else {
                    size.height * (0.83f + lineInBand * 0.055f)
                }
                val startFraction = if (topLine) 0.29f else 0.4f
                val endFraction = if (topLine) {
                    0.68f + lineInBand * 0.12f
                } else {
                    0.72f + lineInBand * 0.1f
                }
                drawLine(
                    color = palette.accent.copy(alpha = 0.32f),
                    start = Offset(size.width * startFraction, y),
                    end = Offset(size.width * endFraction, y),
                    strokeWidth = maxOf(1f, size.width * 0.006f),
                )
            }
        }
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = if (compact) 5.dp else 14.dp,
                vertical = if (compact) 8.dp else 16.dp,
            ),
            color = palette.ink,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = titleSize.sp,
                lineHeight = (titleSize * 1.18f).sp,
            ),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = if (compact) 7 else 9,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun bookCoverLabel(title: String): String {
    return title.trim()
        .replace(Regex("\\s+"), " ")
        .ifBlank { "新阅" }
}

private fun coverTitleSizeSp(title: String, widthDp: Float, compact: Boolean): Float {
    val base = if (compact) 11f else 18f
    val referenceWidth = if (compact) 52f else 108f
    val widthScale = (widthDp / referenceWidth).coerceIn(0.78f, 1f)
    val length = title.count { !it.isWhitespace() }
    val scale = when {
        length > 36 -> 0.58f
        length > 24 -> 0.68f
        length > 16 -> 0.8f
        length > 10 -> 0.9f
        else -> 1f
    }
    return (base * widthScale * scale).coerceAtLeast(if (compact) 8f else 10f)
}
