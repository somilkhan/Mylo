package com.music.bitchord.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Apple Music's signature red. No longer the primary accent, but kept for the
// spots (Replay's rank badge) that want that specific red regardless of theme.
val AccentRed = Color(0xFFFA2D48)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF2A2A2E),
    onPrimaryContainer = Color(0xFFF5F5F7),
    inversePrimary = Color(0xFFC7C7CC),
    secondary = Color(0xFFC7C7CC),
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF29292D),
    onSecondaryContainer = Color(0xFFF5F5F7),
    tertiary = Color(0xFFA1A1AA),
    onTertiary = Color(0xFF18181A),
    tertiaryContainer = Color(0xFF38383D),
    onTertiaryContainer = Color(0xFFF5F5F7),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0D0D0F),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFFB8B8BE),
    surfaceTint = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFFEAEAEE),
    inverseOnSurface = Color(0xFF1C1C1E),
    error = Color(0xFFFF6B63),
    onError = Color(0xFF300806),
    errorContainer = Color(0xFF4A1715),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF66666D),
    outlineVariant = Color(0xFF38383D),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF0D0D0F),
    surfaceBright = Color(0xFF303034),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF252529),
    surfaceContainerHighest = Color(0xFF303034),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E5EA),
    onPrimaryContainer = Color(0xFF1C1C1E),
    inversePrimary = Color(0xFF5E5E63),
    secondary = Color(0xFF5E5E63),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEAEAEE),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF63636A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0F0F3),
    onTertiaryContainer = Color(0xFF242428),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFF7F7F9),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF0F0F4),
    onSurfaceVariant = Color(0xFF55555B),
    surfaceTint = Color(0xFF000000),
    inverseSurface = Color(0xFF2C2C2E),
    inverseOnSurface = Color(0xFFF2F2F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF77777C),
    outlineVariant = Color(0xFFD1D1D6),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFDADAE0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F9),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFEAEAEE),
    surfaceContainerHighest = Color(0xFFE0E0E5),
)

/** Use the platform sans-serif to avoid bundling a proprietary system font. */
val SFProDisplay = FontFamily.SansSerif

// Heavy, tight typography — the backbone of the MYLO look.
private val MyloTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.W600, fontSize = 11.sp),
).withFamily(SFProDisplay)

/** Applies [family] to every style in the scale, so nothing is left on Roboto. */
private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

@Composable
fun BitChordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MyloTypography,
        content = content,
    )
}

/**
 * Draws the status and navigation bar glyphs dark or light.
 *
 * `enableEdgeToEdge()` decides this from the *system* dark-mode setting, which
 * is the wrong input the moment the in-app theme disagrees with it: Light theme
 * on a phone in dark mode left white icons on a white bar, invisible. The bars
 * have to follow the content the app is actually painting. The player supplies
 * its own stable light-icon value and paints contrast behind it; every other
 * surface follows the theme. Hence a parameter rather than reading it here.
 */
@Composable
fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = findWindow(view) ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = dark
            isAppearanceLightNavigationBars = dark
        }
    }
}

