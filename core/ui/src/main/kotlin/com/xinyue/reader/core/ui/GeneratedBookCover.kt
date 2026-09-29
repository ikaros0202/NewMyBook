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

private val coverPalettes = listOf(
    Color(0xFFD8D0C2) to Color(0xFF332B24),
    Color(0xFFC5D1CD) to Color(0xFF263B37),
    Color(0xFFC9C5D4) to Color(0xFF332E45),
    Color(0xFFD4C2BD) to Color(0xFF442C28),
    Color(0xFFBBCBD5) to Color(0xFF233A48),
    Color(0xFFD3CCB1) to Color(0xFF403916),
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
            .background(palette.first)
            .border(1.dp, palette.second.copy(alpha = 0.28f), RoundedCornerShape(4.dp))
            .semantics { contentDescription = "《$title》的默认封面" },
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxWidth < 56.dp
        Canvas(Modifier.fillMaxSize()) {
            drawLine(
                color = palette.second.copy(alpha = 0.18f),
                start = Offset(4f, 0f),
                end = Offset(4f, size.height),
                strokeWidth = 6f,
            )
        }
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = if (compact) 4.dp else 10.dp,
                vertical = if (compact) 6.dp else 12.dp,
            ),
            color = palette.second,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = if (compact) 2 else 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun bookCoverLabel(title: String): String {
    val normalized = title.trim().ifBlank { return "新阅" }
    val cjk = normalized.filter { character -> character.code in 0x3400..0x9FFF }
    if (cjk.isNotEmpty()) {
        return cjk.take(4).chunked(2).joinToString("\n")
    }
    val words = normalized.split(Regex("[^\\p{L}\\p{N}]+"))
        .filter(String::isNotBlank)
    return if (words.size >= 2) {
        words.take(3).mapNotNull(String::firstOrNull).joinToString("").uppercase()
    } else {
        normalized.filter(Char::isLetterOrDigit).take(2).uppercase().ifBlank { "新阅" }
    }
}
