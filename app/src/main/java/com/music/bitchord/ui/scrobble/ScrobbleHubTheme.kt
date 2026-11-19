package com.music.bitchord.ui.scrobble

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
    surface = Color(0xFF2A2730),
    onSurface = Color(0xFFF2EFF5),
    surfaceVariant = Color(0xFF2A2730),
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
    surfaceContainerLow = Color(0xFF1D1722),
    surfaceContainer = Color(0xFF2A2730),
    surfaceContainerHigh = Color(0xFF35313C),
    surfaceContainerHighest = Color(0xFF3A3546),
)

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
                            colors = listOf(
                                Color(0xFF1D1722),
                                Color(0xFF100E15),
                                Color(0xFF07060A),
                            ),
                            startY = 0f,
                            endY = size.height,
                        ),
                    )
                    withTransform({
                        scale(scaleX = 1f, scaleY = 0.38f, pivot = Offset.Zero)
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
                        scale(scaleX = 1f, scaleY = 0.30f, pivot = rightGlowCenter)
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
        MaterialTheme(
            colorScheme = if (gradientEnabled) ScrobbleGradientColors else appColors,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}
