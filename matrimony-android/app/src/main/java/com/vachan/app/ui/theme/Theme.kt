package com.vachan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palette: kumkum (deep red), marigold, cream — warm and celebratory without being loud.
private val Kumkum = Color(0xFF9A1B3C)
private val KumkumDark = Color(0xFFFFB2BF)
private val Marigold = Color(0xFFE0951F)
private val MarigoldDark = Color(0xFFFFC46B)
private val Peacock = Color(0xFF00696B)
private val PeacockDark = Color(0xFF6ED7D8)

private val LightColors = lightColorScheme(
    primary = Kumkum,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DE),
    onPrimaryContainer = Color(0xFF3F0014),
    secondary = Color(0xFF7A5900),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDEA6),
    onSecondaryContainer = Color(0xFF261900),
    tertiary = Peacock,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9CF1F2),
    onTertiaryContainer = Color(0xFF002020),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF22191A),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF22191A),
    surfaceVariant = Color(0xFFF4DDDF),
    onSurfaceVariant = Color(0xFF524345),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF0EE),
    surfaceContainer = Color(0xFFFCEAE8),
    surfaceContainerHigh = Color(0xFFF6E4E2),
    surfaceContainerHighest = Color(0xFFF0DEDD),
    outline = Color(0xFF857375),
    outlineVariant = Color(0xFFD7C1C3),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = KumkumDark,
    onPrimary = Color(0xFF5F0027),
    primaryContainer = Color(0xFF7D0A2E),
    onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = MarigoldDark,
    onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF5C4200),
    onSecondaryContainer = Color(0xFFFFDEA6),
    tertiary = PeacockDark,
    onTertiary = Color(0xFF003737),
    tertiaryContainer = Color(0xFF004F50),
    onTertiaryContainer = Color(0xFF9CF1F2),
    background = Color(0xFF191113),
    onBackground = Color(0xFFF0DEDF),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFF0DEDF),
    surfaceVariant = Color(0xFF524345),
    onSurfaceVariant = Color(0xFFD7C1C3),
    surfaceContainerLowest = Color(0xFF140C0D),
    surfaceContainerLow = Color(0xFF22191A),
    surfaceContainer = Color(0xFF261D1E),
    surfaceContainerHigh = Color(0xFF312728),
    surfaceContainerHighest = Color(0xFF3C3233),
    outline = Color(0xFFA08C8E),
    outlineVariant = Color(0xFF524345),
)

/** Semantic colours not covered by Material roles. */
@Immutable
data class ExtendedColors(
    val verified: Color,
    val onVerified: Color,
    val caution: Color,
    val cautionContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val accent: Color,
)

private val LightExtended = ExtendedColors(
    verified = Color(0xFF00696B), onVerified = Color.White,
    caution = Color(0xFF7A5900), cautionContainer = Color(0xFFFFEFC9),
    danger = Color(0xFFB3261E), dangerContainer = Color(0xFFFFE1DD),
    accent = Marigold,
)

private val DarkExtended = ExtendedColors(
    verified = PeacockDark, onVerified = Color(0xFF003737),
    caution = MarigoldDark, cautionContainer = Color(0xFF3F2E00),
    danger = Color(0xFFFFB4AB), dangerContainer = Color(0xFF5C1410),
    accent = MarigoldDark,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        headlineLarge = headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun VachanTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val extended = if (darkTheme) DarkExtended else LightExtended
    androidx.compose.runtime.CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

object VachanColors {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}
