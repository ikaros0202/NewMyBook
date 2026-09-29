package com.xinyue.reader.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class XinYueThemeTest {
    @Test
    fun `light and dark schemes define the paper semantic roles`() {
        val light = xinYueLightColorScheme()
        val dark = xinYueDarkColorScheme()

        assertThat(light.primary).isEqualTo(Color(0xFF8B3F32))
        assertThat(light.secondaryContainer).isEqualTo(Color(0xFFE4DDD2))
        assertThat(light.surface).isEqualTo(Color(0xFFF3EEE3))
        assertThat(light.surfaceContainerLow).isEqualTo(Color(0xFFEFE8DC))
        assertThat(light.outlineVariant).isEqualTo(Color(0xFFCFC6B7))

        assertThat(dark.primary).isEqualTo(Color(0xFFD18472))
        assertThat(dark.secondaryContainer).isEqualTo(Color(0xFF4A443C))
        assertThat(dark.surface).isEqualTo(Color(0xFF1C1A17))
        assertThat(dark.surfaceContainerLow).isEqualTo(Color(0xFF24211D))
        assertThat(dark.outlineVariant).isEqualTo(Color(0xFF49433B))
    }

    @Test
    fun `application typography and shapes follow the compact reader system`() {
        assertThat(XinYueTypography.headlineSmall.fontSize).isEqualTo(24.sp)
        assertThat(XinYueTypography.headlineSmall.lineHeight).isEqualTo(30.sp)
        assertThat(XinYueTypography.headlineSmall.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(XinYueTypography.titleSmall.fontSize).isEqualTo(14.sp)
        assertThat(XinYueTypography.bodyMedium.fontSize).isEqualTo(14.sp)
        assertThat(XinYueTypography.bodySmall.fontSize).isEqualTo(12.sp)
        assertThat(XinYueShapeTokens.smallCornerDp.value).isEqualTo(4f)
        assertThat(XinYueShapeTokens.mediumCornerDp.value).isEqualTo(8f)
        assertThat(XinYueShapeTokens.largeCornerDp.value).isEqualTo(12f)
        assertThat(XinYueShapeTokens.emphasisCornerDp.value).isEqualTo(16f)
    }
}
