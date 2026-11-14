package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Diameter of the scrobble button. A 62dp visual control exceeds the 48dp touch minimum. */
internal val SCROBBLE_FAB_SIZE = 62.dp

/**
 * The FAB belongs to the Home surface only. It gets out of the way while the
 * user is navigating, opening another surface, using the keyboard, or
 * scrolling down to read. It returns as soon as scrolling stops/reverses.
 */
internal fun scrobbleFabVisible(
    selectedTab: Int,
    homeTab: Int,
    overlayOpen: Boolean,
    playerOpen: Boolean = false,
    keyboardVisible: Boolean = false,
    scrollingDown: Boolean = false,
): Boolean = selectedTab == homeTab &&
    !overlayOpen &&
    !playerOpen &&
    !keyboardVisible &&
    !scrollingDown

/**
 * Floating entry point to Scrobble.
 *
 * Keep the layout slot stable while animating the visual surface. This avoids
 * pushing the page or navigation bar when the FAB appears/disappears. Hidden
 * means non-interactive and absent from accessibility; it is not merely
 * transparent. [surface] is supplied by the host so this control uses the
 * same liquid-glass/material treatment as the surrounding navigation chrome.
 */
@Composable
fun ScrobbleFab(
    visible: Boolean,
    open: Boolean,
    onClick: () -> Unit,
    surface: Modifier,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    folded: Boolean = false,
) {
    val spec = if (reduceMotion) {
        snap()
    } else {
        spring<Float>(dampingRatio = 0.78f, stiffness = 360f)
    }
    val scale by animateFloatAsState(
        targetValue = when {
            !visible -> 0.72f
            folded -> 0.88f
            else -> 1f
        },
        animationSpec = spec,
        label = "scrobbleFabScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spec,
        label = "scrobbleFabAlpha",
    )
    val lift by animateFloatAsState(
        targetValue = when {
            !visible -> 14f
            folded -> 4f
            else -> 0f
        },
        animationSpec = spec,
        label = "scrobbleFabLift",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(SCROBBLE_FAB_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift * density
                this.alpha = alpha
            }
            .then(surface)
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics { })
            .then(
                if (visible) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = if (open) "Close Scrobble" else "Open Scrobble",
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        AnimatedContent(
            targetState = open,
            transitionSpec = {
                (fadeIn(tween(130)) + scaleIn(initialScale = 0.76f, animationSpec = tween(160))) togetherWith
                    (fadeOut(tween(90)) + scaleOut(targetScale = 0.76f, animationSpec = tween(120)))
            },
            label = "scrobbleFabGlyph",
        ) { isOpen ->
            Icon(
                imageVector = if (isOpen) Icons.Rounded.Close else Icons.Rounded.GraphicEq,
                contentDescription = if (isOpen) "Close Scrobble" else "Open Scrobble",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
