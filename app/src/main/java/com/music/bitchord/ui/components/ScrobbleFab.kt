package com.music.bitchord.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Diameter of the scrobble button. Matches the Search circle beside it. */
internal val SCROBBLE_FAB_SIZE = 62.dp

/**
 * Whether the scrobble button is shown. Home only, and never while another
 * full-screen page (Settings, Replay, history) is over the tabs.
 */
internal fun scrobbleFabVisible(
    selectedTab: Int,
    homeTab: Int,
    overlayOpen: Boolean,
): Boolean = selectedTab == homeTab && !overlayOpen

/**
 * The floating scrobble button.
 *
 * It always occupies [SCROBBLE_FAB_SIZE] so the layout around it does not
 * move when it appears or disappears. When [visible] is false it shrinks and
 * fades out and stops taking taps. When [open] is true the glyph turns into a
 * close icon.
 *
 * [surface] is the caller's material (liquid glass, haze or solid), so the
 * button matches the bar it sits in.
 */
@Composable
fun ScrobbleFab(
    visible: Boolean,
    open: Boolean,
    onClick: () -> Unit,
    surface: Modifier,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val spec = if (reduceMotion) {
        spring<Float>(dampingRatio = 1f, stiffness = 10_000f)
    } else {
        spring<Float>(dampingRatio = 0.72f, stiffness = 320f)
    }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = spec,
        label = "scrobbleFabScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spec,
        label = "scrobbleFabAlpha",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(SCROBBLE_FAB_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .then(surface)
            .then(
                if (visible) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = if (open) "Close scrobble" else "Open scrobble",
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Icon(
            imageVector = if (open) Icons.Rounded.Close else Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}
