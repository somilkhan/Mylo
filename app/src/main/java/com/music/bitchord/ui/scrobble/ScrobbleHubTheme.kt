package com.music.bitchord.ui.scrobble

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform

private val ScrobbleGradientColors = darkColorScheme(
    primary = Color(0xFF4B4262),
    onPrimary = Color(0xFFF4F0FF),
    primaryContainer = Color(0xFF4B4262),
    onPrimaryContainer = Color(0xFFF4F0FF),
    secondary = Color(0xFFE8B9CD),
    onSecondary = Color(0xFF1D1722),
    secondaryContainer = Color(0xFF3A3546),
    onSecondaryContainer = Color(0xFFF2EFF5),
    tertiary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFF1D1722),
    tertiaryContainer = Color(0xFF4A4650),
    onTertiaryContainer = Color(0xFFF2EFF5),
    background = Color.Transparent,
    onBackground = Color(0xFFF2EFF5),
    surface = Color(0xC72A2730),
    onSurface = Color(0xFFF2EFF5),
    surfaceVariant = Color(0xC72A2730),
    onSurfaceVariant = Color(0xFFAAA4B4),
    surfaceTint = Color(0xFF4B4262),
    inverseSurface = Color(0xFFF2EFF5),
    inverseOnSurface = Color(0xFF1D1722),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF4A4650),
    outlineVariant = Color(0xFF3A3546),
    scrim = Color.Black,
    surfaceDim = Color(0xFF100E15),
    surfaceBright = Color(0xFF4A4650),
    surfaceContainerLowest = Color(0xFF100E15),
    surfaceContainerLow = Color(0xC72A2730),
    surfaceContainer = Color(0xC72A2730),
    surfaceContainerHigh = Color(0xC72A2730),
    surfaceContainerHighest = Color(0xC72A2730),
)

internal val LocalScrobbleGradientEnabled = staticCompositionLocalOf { false }

/** Keep the existing Mylo button colors when the gradient theme is off. */
@Composable
internal fun scrobbleTextButtonColors(contentColor: Color) =
    if (LocalScrobbleGradientEnabled.current) {
        ButtonDefaults.textButtonColors(contentColor = contentColor)
    } else {
        ButtonDefaults.textButtonColors()
    }

/** Outlined actions need a readable foreground over the dark gradient surfaces. */
@Composable
internal fun scrobbleOutlinedButtonColors(contentColor: Color) =
    if (LocalScrobbleGradientEnabled.current) {
        ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
    } else {
        ButtonDefaults.outlinedButtonColors()
    }

/** A readable accent for foregrounds and progress indicators on the dark gradient. */
@Composable
internal fun scrobbleAccentColor() =
    if (LocalScrobbleGradientEnabled.current) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }

/** Keep checkbox colors unchanged when the gradient theme is off. */
@Composable
internal fun scrobbleCheckboxColors() =
    if (LocalScrobbleGradientEnabled.current) {
        CheckboxDefaults.colors(
            checkedColor = MaterialTheme.colorScheme.secondary,
            checkmarkColor = MaterialTheme.colorScheme.onSecondary,
            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        CheckboxDefaults.colors()
    }

/** Use the approved pink accent for filled actions only in the gradient theme. */
@Composable
internal fun scrobblePrimaryButtonColors() =
    if (LocalScrobbleGradientEnabled.current) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        )
    } else {
        ButtonDefaults.buttonColors()
    }

/** Applies the approved v3 palette only while the three Scrobble Hub pages are hosted. */
@Composable
internal fun ScrobbleHubTheme(
    gradientEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val appColors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                if (!gradientEnabled) {
                    drawRect(appColors.background)
                } else {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color(0xFF1D1722),
                            0.42f to Color(0xFF100E15),
                            1f to Color(0xFF07060A),
                            startY = 0f,
                            endY = size.height,
                        ),
                    )
                    withTransform({
                        scale(scaleX = 1f, scaleY = size.height * 0.38f / (size.width * 1.2f), pivot = Offset.Zero)
                    }) {
                        drawRect(
                            Brush.radialGradient(
                                0f to Color(0xFF4A2428),
                                0.7f to Color.Transparent,
                                center = Offset.Zero,
                                radius = size.width * 1.2f,
                            ),
                        )
                    }
                    val rightGlowCenter = Offset(size.width, size.height * 0.22f)
                    withTransform({
                        scale(scaleX = 1f, scaleY = size.height * 0.30f / (size.width * 0.9f), pivot = rightGlowCenter)
                    }) {
                        drawRect(
                            Brush.radialGradient(
                                0f to Color(0xFF202640),
                                0.72f to Color.Transparent,
                                center = rightGlowCenter,
                                radius = size.width * 0.9f,
                            ),
                        )
                    }
                }
            },
    ) {
        CompositionLocalProvider(LocalScrobbleGradientEnabled provides gradientEnabled) {
            MaterialTheme(
                colorScheme = if (gradientEnabled) ScrobbleGradientColors else appColors,
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes,
                content = content,
            )
        }
    }
}
