package com.vachan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vachan.app.R

/*
 * Aangan design language — "editorial warm".
 *
 * An aangan is the courtyard at the heart of an Indian home: open to the sky, closed to the
 * street. The palette borrows from it — limewashed ivory walls, terracotta clay, tulsi sage and
 * the ink of a handwritten letter. The UI is mostly ivory and ink; clay is reserved for intent
 * (interest, the brand) and sage for trust (verification), so colour always means something.
 */

private object Palette {
    val Ink = Color(0xFF1C1917)
    val InkSoft = Color(0xFF3A3530)
    val InkMuted = Color(0xFF6F675E)
    val Stone = Color(0xFFA39A8E)
    val Hairline = Color(0xFFE5DDD1)
    val Ivory = Color(0xFFF8F4EE)
    val Paper = Color(0xFFFFFFFF)
    val Sand1 = Color(0xFFF3EEE6)
    val Sand2 = Color(0xFFEEE7DD)
    val Sand3 = Color(0xFFE8E0D4)
    val Sand4 = Color(0xFFE1D8CA)

    val Clay = Color(0xFFB0532F)
    val ClayContainer = Color(0xFFF6E2D6)
    val OnClayContainer = Color(0xFF4A1C0B)
    val Sage = Color(0xFF2F6A50)
    val SageContainer = Color(0xFFDDEDE3)
    val OnSageContainer = Color(0xFF0E2C1F)
    val Gold = Color(0xFF9A7432)
    val GoldContainer = Color(0xFFF3E8CF)

    // Dark: a courtyard at night — warm charcoal, never pure black.
    val Night = Color(0xFF12100E)
    val NightSurface = Color(0xFF1B1815)
    val Night1 = Color(0xFF1F1C18)
    val Night2 = Color(0xFF24201C)
    val Night3 = Color(0xFF2C2823)
    val Night4 = Color(0xFF35302A)
    val Moon = Color(0xFFF2ECE4)
    val MoonMuted = Color(0xFFB3AA9E)
    val NightHairline = Color(0xFF3A342D)
    val ClayLight = Color(0xFFE8916B)
    val ClayDeep = Color(0xFF4A2414)
    val SageLight = Color(0xFF8CCBAA)
    val SageDeep = Color(0xFF173A2A)
    val GoldLight = Color(0xFFE2C27F)
}

private val LightColors = lightColorScheme(
    primary = Palette.Ink,
    onPrimary = Palette.Ivory,
    primaryContainer = Palette.Sand2,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.Clay,
    onSecondary = Color.White,
    secondaryContainer = Palette.ClayContainer,
    onSecondaryContainer = Palette.OnClayContainer,
    tertiary = Palette.Sage,
    onTertiary = Color.White,
    tertiaryContainer = Palette.SageContainer,
    onTertiaryContainer = Palette.OnSageContainer,
    background = Palette.Ivory,
    onBackground = Palette.Ink,
    surface = Palette.Ivory,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Sand2,
    onSurfaceVariant = Palette.InkMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.Ivory,
    inversePrimary = Palette.Sand3,
    surfaceBright = Palette.Paper,
    surfaceDim = Palette.Sand3,
    surfaceContainerLowest = Palette.Paper,
    surfaceContainerLow = Palette.Sand1,
    surfaceContainer = Palette.Sand2,
    surfaceContainerHigh = Palette.Sand3,
    surfaceContainerHighest = Palette.Sand4,
    outline = Palette.Stone,
    outlineVariant = Palette.Hairline,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFBE3DF),
    onErrorContainer = Color(0xFF5C1410),
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Palette.Moon,
    onPrimary = Palette.Night,
    primaryContainer = Palette.Night3,
    onPrimaryContainer = Palette.Moon,
    secondary = Palette.ClayLight,
    onSecondary = Color(0xFF3A1406),
    secondaryContainer = Palette.ClayDeep,
    onSecondaryContainer = Color(0xFFF8DCCD),
    tertiary = Palette.SageLight,
    onTertiary = Color(0xFF0A2A1C),
    tertiaryContainer = Palette.SageDeep,
    onTertiaryContainer = Color(0xFFC9EBD8),
    background = Palette.Night,
    onBackground = Palette.Moon,
    surface = Palette.Night,
    onSurface = Palette.Moon,
    surfaceVariant = Palette.Night3,
    onSurfaceVariant = Palette.MoonMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = Palette.Moon,
    inverseOnSurface = Palette.Night,
    inversePrimary = Palette.InkSoft,
    surfaceBright = Palette.Night4,
    surfaceDim = Palette.Night,
    surfaceContainerLowest = Palette.NightSurface,
    surfaceContainerLow = Palette.Night1,
    surfaceContainer = Palette.Night2,
    surfaceContainerHigh = Palette.Night3,
    surfaceContainerHighest = Palette.Night4,
    outline = Color(0xFF7D746A),
    outlineVariant = Palette.NightHairline,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1410),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

/** Semantic colours the Material roles don't cover. */
@Immutable
data class ExtendedColors(
    /** Brand clay: interest, hearts, the mark. */
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    /** Sage: verification and trust. */
    val verified: Color,
    val onVerified: Color,
    val verifiedContainer: Color,
    /** Gold: exceptional compatibility only. */
    val gold: Color,
    val goldContainer: Color,
    val caution: Color,
    val cautionContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    /** Card surface (paper on ivory) and hairline borders. */
    val card: Color,
    val hairline: Color,
    val muted: Color,
)

private val LightExtended = ExtendedColors(
    accent = Palette.Clay, onAccent = Color.White,
    accentContainer = Palette.ClayContainer, onAccentContainer = Palette.OnClayContainer,
    verified = Palette.Sage, onVerified = Color.White, verifiedContainer = Palette.SageContainer,
    gold = Palette.Gold, goldContainer = Palette.GoldContainer,
    caution = Color(0xFF7A5200), cautionContainer = Color(0xFFFBF0D6),
    danger = Color(0xFFA8231B), dangerContainer = Color(0xFFFBE6E2),
    card = Palette.Paper, hairline = Palette.Hairline, muted = Palette.InkMuted,
)

private val DarkExtended = ExtendedColors(
    accent = Palette.ClayLight, onAccent = Color(0xFF3A1406),
    accentContainer = Palette.ClayDeep, onAccentContainer = Color(0xFFF8DCCD),
    verified = Palette.SageLight, onVerified = Color(0xFF0A2A1C), verifiedContainer = Palette.SageDeep,
    gold = Palette.GoldLight, goldContainer = Color(0xFF3D2F12),
    caution = Color(0xFFF2C46B), cautionContainer = Color(0xFF3B2C08),
    danger = Color(0xFFFFB4AB), dangerContainer = Color(0xFF4D1712),
    card = Palette.NightSurface, hairline = Palette.NightHairline, muted = Palette.MoonMuted,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

// ----------------------------------------------------------------------------- typography

/** Instrument Serif: an editorial display face for names and headlines. */
val DisplayFamily = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Inter: a highly legible interface face with tabular numbers. */
@OptIn(ExperimentalTextApi::class)
val TextFamily = FontFamily(inter(400), inter(500), inter(600), inter(700))

private val TrimBoth = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun display(size: Int, line: Int, tracking: Double = -0.01) = TextStyle(
    fontFamily = DisplayFamily, fontWeight = FontWeight.Normal,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em, lineHeightStyle = TrimBoth,
)

private fun text(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = TextFamily, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em, lineHeightStyle = TrimBoth,
)

val AanganTypography = Typography(
    displayLarge = display(56, 58, -0.02),
    displayMedium = display(46, 50, -0.02),
    displaySmall = display(38, 42, -0.015),
    headlineLarge = display(34, 38),
    headlineMedium = display(30, 34),
    headlineSmall = display(26, 30),
    titleLarge = text(20, 26, FontWeight.SemiBold, -0.01),
    titleMedium = text(16, 22, FontWeight.SemiBold, -0.005),
    titleSmall = text(14, 20, FontWeight.SemiBold),
    bodyLarge = text(16, 24, FontWeight.Normal, -0.003),
    bodyMedium = text(14, 21, FontWeight.Normal),
    bodySmall = text(13, 18, FontWeight.Normal),
    labelLarge = text(15, 20, FontWeight.SemiBold),
    labelMedium = text(13, 16, FontWeight.Medium),
    labelSmall = text(11, 14, FontWeight.Medium, 0.02),
)

/** Small uppercase eyebrow label used above sections ("WHY YOU MIGHT CLICK"). */
val EyebrowStyle = text(11, 14, FontWeight.SemiBold, 0.12)

val AanganShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun AanganTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalExtendedColors provides if (darkTheme) DarkExtended else LightExtended) {
        MaterialTheme(colorScheme = colors, typography = AanganTypography, shapes = AanganShapes) {
            // Text outside a Surface/Scaffold inherits this; without it, it defaults to black in dark mode.
            CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
        }
    }
}

object AanganColors {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}
