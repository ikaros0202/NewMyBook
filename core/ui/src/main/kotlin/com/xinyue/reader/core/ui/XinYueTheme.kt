package com.xinyue.reader.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun xinYueLightColorScheme(): ColorScheme = lightColorScheme(
    primary = Color(0xFF8B3F32),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBD0C9),
    onPrimaryContainer = Color(0xFF4A211A),
    inversePrimary = Color(0xFFD18472),
    secondary = Color(0xFF756E63),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4DDD2),
    onSecondaryContainer = Color(0xFF2C2924),
    tertiary = Color(0xFF5F6B62),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE4DC),
    onTertiaryContainer = Color(0xFF202922),
    background = Color(0xFFF3EEE3),
    onBackground = Color(0xFF28241E),
    surface = Color(0xFFF3EEE3),
    onSurface = Color(0xFF28241E),
    surfaceVariant = Color(0xFFE7DFD3),
    onSurfaceVariant = Color(0xFF756E63),
    surfaceTint = Color(0xFF8B3F32),
    inverseSurface = Color(0xFF342F29),
    inverseOnSurface = Color(0xFFF2EDE4),
    outline = Color(0xFF70685B),
    outlineVariant = Color(0xFFCFC6B7),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF9F6F0),
    surfaceDim = Color(0xFFDAD1C4),
    surfaceContainerLowest = Color(0xFFFFFCF6),
    surfaceContainerLow = Color(0xFFEFE8DC),
    surfaceContainer = Color(0xFFE9E1D5),
    surfaceContainerHigh = Color(0xFFE2D9CC),
    surfaceContainerHighest = Color(0xFFDCD3C6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

internal fun xinYueDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFFD18472),
    onPrimary = Color(0xFF4A211A),
    primaryContainer = Color(0xFF6E3027),
    onPrimaryContainer = Color(0xFFF5DAD2),
    inversePrimary = Color(0xFF8B3F32),
    secondary = Color(0xFFB7AFA2),
    onSecondary = Color(0xFF332F29),
    secondaryContainer = Color(0xFF4A443C),
    onSecondaryContainer = Color(0xFFE4DDD2),
    tertiary = Color(0xFFB5C2B7),
    onTertiary = Color(0xFF202922),
    tertiaryContainer = Color(0xFF3B4A3F),
    onTertiaryContainer = Color(0xFFDCE4DC),
    background = Color(0xFF1C1A17),
    onBackground = Color(0xFFEEE8DC),
    surface = Color(0xFF1C1A17),
    onSurface = Color(0xFFEEE8DC),
    surfaceVariant = Color(0xFF49433B),
    onSurfaceVariant = Color(0xFFB7AFA2),
    surfaceTint = Color(0xFFD18472),
    inverseSurface = Color(0xFFEEE8DC),
    inverseOnSurface = Color(0xFF342F29),
    outline = Color(0xFF9D9387),
    outlineVariant = Color(0xFF49433B),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF443D35),
    surfaceDim = Color(0xFF1C1A17),
    surfaceContainerLowest = Color(0xFF151310),
    surfaceContainerLow = Color(0xFF24211D),
    surfaceContainer = Color(0xFF2B2722),
    surfaceContainerHigh = Color(0xFF373129),
    surfaceContainerHighest = Color(0xFF423B32),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private fun appTextStyle(
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily = FontFamily.SansSerif,
) = TextStyle(
    fontFamily = fontFamily,
    fontSize = fontSize,
    lineHeight = lineHeight,
    fontWeight = fontWeight,
)

internal val XinYueTypography = Typography(
    displayLarge = appTextStyle(57.sp, 64.sp, fontFamily = FontFamily.Serif),
    displayMedium = appTextStyle(45.sp, 52.sp, fontFamily = FontFamily.Serif),
    displaySmall = appTextStyle(36.sp, 44.sp, fontFamily = FontFamily.Serif),
    headlineLarge = appTextStyle(32.sp, 40.sp, FontWeight.SemiBold, FontFamily.Serif),
    headlineMedium = appTextStyle(28.sp, 36.sp, FontWeight.SemiBold, FontFamily.Serif),
    headlineSmall = appTextStyle(24.sp, 30.sp, FontWeight.SemiBold, FontFamily.Serif),
    titleLarge = appTextStyle(22.sp, 28.sp, FontWeight.SemiBold, FontFamily.Serif),
    titleMedium = appTextStyle(16.sp, 22.sp, FontWeight.SemiBold, FontFamily.Serif),
    titleSmall = appTextStyle(14.sp, 20.sp, FontWeight.SemiBold, FontFamily.Serif),
    bodyLarge = appTextStyle(16.sp, 24.sp),
    bodyMedium = appTextStyle(14.sp, 20.sp),
    bodySmall = appTextStyle(12.sp, 17.sp),
    labelLarge = appTextStyle(14.sp, 20.sp, FontWeight.Medium),
    labelMedium = appTextStyle(12.sp, 16.sp, FontWeight.Medium),
    labelSmall = appTextStyle(11.sp, 16.sp, FontWeight.Medium),
)

internal object XinYueShapeTokens {
    val smallCornerDp: Dp = 4.dp
    val mediumCornerDp: Dp = 8.dp
    val largeCornerDp: Dp = 12.dp
    val emphasisCornerDp: Dp = 16.dp
}

internal val XinYueShapes = Shapes(
    extraSmall = RoundedCornerShape(XinYueShapeTokens.smallCornerDp),
    small = RoundedCornerShape(XinYueShapeTokens.smallCornerDp),
    medium = RoundedCornerShape(XinYueShapeTokens.mediumCornerDp),
    large = RoundedCornerShape(XinYueShapeTokens.largeCornerDp),
    extraLarge = RoundedCornerShape(XinYueShapeTokens.emphasisCornerDp),
)

@Composable
fun XinYueTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) xinYueDarkColorScheme() else xinYueLightColorScheme(),
        typography = XinYueTypography,
        shapes = XinYueShapes,
        content = content,
    )
}
