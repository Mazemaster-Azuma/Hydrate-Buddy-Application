package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.model.ThemeMode

@Immutable
data class HydrateExtendedColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warmAccent: Color,
    val cardGradientStart: Color,
    val cardGradientEnd: Color,
    val waterRingTrack: Color,
    val buddyHighlight: Color
)

private val LightExtendedColors = HydrateExtendedColors(
    success = MintSuccessLight,
    successContainer = MintSuccessContainerLight,
    onSuccessContainer = MintOnSuccessContainerLight,
    warmAccent = AmberWarm,
    cardGradientStart = Color(0xFFFFFFFF),
    cardGradientEnd = Color(0xFFF7FBFF),
    waterRingTrack = Color(0xFFEAF1F7),
    buddyHighlight = Color(0xFF7DD3FC)
)

private val DarkExtendedColors = HydrateExtendedColors(
    success = MintSuccessDark,
    successContainer = MintSuccessContainerDark,
    onSuccessContainer = Color(0xFFD1FAE5),
    warmAccent = Color(0xFFFBBF24),
    cardGradientStart = Color(0xFF0E2B3E),
    cardGradientEnd = Color(0xFF0D3338),
    waterRingTrack = Color(0xFF173D54),
    buddyHighlight = Color(0xFF0284C7)
)

val LocalHydrateColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColorScheme = lightColorScheme(
    primary = AquaPrimaryLight,
    onPrimary = AquaOnPrimaryLight,
    primaryContainer = AquaPrimaryContainerLight,
    onPrimaryContainer = AquaOnPrimaryContainerLight,
    secondary = TurquoiseSecondaryLight,
    onSecondary = TurquoiseOnSecondaryLight,
    secondaryContainer = TurquoiseSecondaryContainerLight,
    onSecondaryContainer = TurquoiseOnSecondaryContainerLight,
    tertiary = LavenderTertiaryLight,
    onTertiary = LavenderOnTertiaryLight,
    tertiaryContainer = LavenderTertiaryContainerLight,
    onTertiaryContainer = LavenderOnTertiaryContainerLight,
    background = WhiteBackgroundLight,
    onBackground = SlateOnBackgroundLight,
    surface = WhiteSurfaceLight,
    onSurface = SlateOnBackgroundLight,
    surfaceContainerLowest = WhiteSurfaceLight,
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = WhiteSurfaceVariantLight,
    surfaceContainerHigh = Color(0xFFEDF2F7),
    surfaceVariant = WhiteSurfaceVariantLight,
    onSurfaceVariant = SlateOnSurfaceVariantLight,
    outline = HairlineOutlineLight,
    outlineVariant = HairlineOutlineVariantLight,
    error = CoralErrorLight,
    errorContainer = CoralErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = AquaPrimaryDark,
    onPrimary = AquaOnPrimaryDark,
    primaryContainer = AquaPrimaryContainerDark,
    onPrimaryContainer = AquaOnPrimaryContainerDark,
    secondary = TurquoiseSecondaryDark,
    onSecondary = TurquoiseOnSecondaryDark,
    secondaryContainer = TurquoiseSecondaryContainerDark,
    onSecondaryContainer = TurquoiseOnSecondaryContainerDark,
    tertiary = LavenderTertiaryDark,
    onTertiary = LavenderOnTertiaryDark,
    tertiaryContainer = LavenderTertiaryContainerDark,
    onTertiaryContainer = LavenderOnTertiaryContainerDark,
    background = OceanBackgroundDark,
    onBackground = OceanOnBackgroundDark,
    surface = OceanSurfaceDark,
    onSurface = OceanOnBackgroundDark,
    surfaceVariant = OceanSurfaceVariantDark,
    onSurfaceVariant = OceanOnSurfaceVariantDark,
    outline = OceanOutlineDark,
    outlineVariant = OceanOutlineVariantDark,
    error = CoralErrorDark,
    errorContainer = CoralErrorContainerDark
)

val HydrateShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun HydrateBuddyTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (useDarkTheme) DarkExtendedColors else LightExtendedColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDarkTheme
            insetsController.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    CompositionLocalProvider(LocalHydrateColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HydrateTypography,
            shapes = HydrateShapes,
            content = content
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
