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
    primary = Color(0xFFB44C1C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFF0E7),
    onPrimaryContainer = Color(0xFF60300F),
    inversePrimary = Color(0xFFFFB68C),
    secondary = Color(0xFF66666B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F1F3),
    onSecondaryContainer = Color(0xFF303035),
    tertiary = Color(0xFF6F5E50),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3E9DF),
    onTertiaryContainer = Color(0xFF352B24),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF242426),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF242426),
    surfaceVariant = Color(0xFFF2F2F4),
    onSurfaceVariant = Color(0xFF66666B),
    surfaceTint = Color(0xFFB44C1C),
    inverseSurface = Color(0xFF29292C),
    inverseOnSurface = Color(0xFFF5F5F7),
    outline = Color(0xFF85858B),
    outlineVariant = Color(0xFFE2E2E7),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDEDEE2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F8FA),
    surfaceContainer = Color(0xFFF4F4F6),
    surfaceContainerHigh = Color(0xFFEFEFF2),
    surfaceContainerHighest = Color(0xFFE7E7EB),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

internal fun xinYueDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB68C),
    onPrimary = Color(0xFF542300),
    primaryContainer = Color(0xFF6F3310),
    onPrimaryContainer = Color(0xFFFFEBDD),
    inversePrimary = Color(0xFFB44C1C),
    secondary = Color(0xFFC2C2C8),
    onSecondary = Color(0xFF2C2C30),
    secondaryContainer = Color(0xFF414146),
    onSecondaryContainer = Color(0xFFE9E9EE),
    tertiary = Color(0xFFD4C3B5),
    onTertiary = Color(0xFF342B25),
    tertiaryContainer = Color(0xFF514235),
    onTertiaryContainer = Color(0xFFF3E9DF),
    background = Color(0xFF18181B),
    onBackground = Color(0xFFEEEEF1),
    surface = Color(0xFF18181B),
    onSurface = Color(0xFFEEEEF1),
    surfaceVariant = Color(0xFF424247),
    onSurfaceVariant = Color(0xFFC2C2C8),
    surfaceTint = Color(0xFFFFB68C),
    inverseSurface = Color(0xFFEEEEF1),
    inverseOnSurface = Color(0xFF29292C),
    outline = Color(0xFF98989F),
    outlineVariant = Color(0xFF424247),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF39393E),
    surfaceDim = Color(0xFF18181B),
    surfaceContainerLowest = Color(0xFF101012),
    surfaceContainerLow = Color(0xFF222226),
    surfaceContainer = Color(0xFF2A2A2F),
    surfaceContainerHigh = Color(0xFF323237),
    surfaceContainerHighest = Color(0xFF3A3A40),
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
    headlineLarge = appTextStyle(30.sp, 38.sp, FontWeight.Medium),
    headlineMedium = appTextStyle(26.sp, 34.sp, FontWeight.Medium),
    headlineSmall = appTextStyle(22.sp, 30.sp, FontWeight.Medium),
    titleLarge = appTextStyle(20.sp, 28.sp, FontWeight.Medium),
    titleMedium = appTextStyle(16.sp, 24.sp, FontWeight.Medium),
    titleSmall = appTextStyle(14.sp, 20.sp, FontWeight.Medium),
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
