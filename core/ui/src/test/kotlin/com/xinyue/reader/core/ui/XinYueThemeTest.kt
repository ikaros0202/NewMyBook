package com.xinyue.reader.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class XinYueThemeTest {
    @Test
    fun `light and dark controls keep readable text contrast`() {
        listOf(xinYueLightColorScheme(), xinYueDarkColorScheme()).forEach { scheme ->
            listOf(
                scheme.onSurface to scheme.surface,
                scheme.onSurfaceVariant to scheme.surface,
                scheme.onSurfaceVariant to scheme.surfaceContainerHigh,
                scheme.primary to scheme.surface,
                scheme.onPrimary to scheme.primary,
                scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSecondaryContainer to scheme.secondaryContainer,
            ).forEach { (text, background) ->
                assertThat(contrast(text, background)).isAtLeast(4.5f)
            }
        }
    }

    @Test
    fun `application typography and shapes follow the compact reader system`() {
        assertThat(XinYueTypography.headlineSmall.fontSize).isEqualTo(22.sp)
        assertThat(XinYueTypography.headlineSmall.lineHeight).isEqualTo(30.sp)
        assertThat(XinYueTypography.headlineSmall.fontWeight).isEqualTo(FontWeight.Medium)
        assertThat(XinYueTypography.titleSmall.fontSize).isEqualTo(14.sp)
        assertThat(XinYueTypography.bodyMedium.fontSize).isEqualTo(14.sp)
        assertThat(XinYueTypography.bodySmall.fontSize).isEqualTo(12.sp)
        assertThat(XinYueShapeTokens.smallCornerDp.value).isEqualTo(4f)
        assertThat(XinYueShapeTokens.mediumCornerDp.value).isEqualTo(8f)
        assertThat(XinYueShapeTokens.largeCornerDp.value).isEqualTo(12f)
        assertThat(XinYueShapeTokens.emphasisCornerDp.value).isEqualTo(16f)
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
