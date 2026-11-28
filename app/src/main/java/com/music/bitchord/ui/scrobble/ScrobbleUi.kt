package com.music.bitchord.ui.scrobble

import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.liquidGlassRim
import java.util.Locale

/*
 * The pieces Generate and Playlists are drawn from, so the two tabs read as
 * one page with Stats: the same 24dp cards on the black page, the same small
 * uppercase section labels, the same hairlines, and one white pill for the
 * thing the page is for.
 */

internal val ScrobbleCardShape = RoundedCornerShape(24.dp)

/** Converts Compose window-local anchor bounds into screen coordinates for a full-window popup. */
internal fun Rect.toScreenCoordinates(view: View): Rect {
    val screen = IntArray(2)
    val window = IntArray(2)
    view.getLocationOnScreen(screen)
    view.getLocationInWindow(window)
    return translate(Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat()))
}

/** Host-owned policy; standalone controls still honor the app and system preferences. */
internal val LocalScrobbleReduceMotion = staticCompositionLocalOf { false }

@Composable
internal fun scrobbleReduceMotion(requested: Boolean = false): Boolean {
    val appReduceMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    // Compose's Android window recomposer observes the system animator duration scale.
    val systemMotion = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    return requested || LocalScrobbleReduceMotion.current || appReduceMotion ||
        systemMotion?.scaleFactor == 0f
}

private val ScrobblePressEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Stats' card: a translucent grey fill and a hairline edge, so it sits on black without a shadow. */
@Composable
internal fun Modifier.scrobbleCard(shape: androidx.compose.ui.graphics.Shape = ScrobbleCardShape): Modifier {
    val colors = MaterialTheme.colorScheme
    return this
        .clip(shape)
        .background(colors.surfaceVariant.copy(alpha = 0.55f))
        .border(0.5.dp, colors.onSurface.copy(alpha = 0.07f), shape)
}

/** A page title the size of Stats', with an optional line under it. */
@Composable
internal fun ScrobblePageTitle(
    title: String,
    subtitle: String?,
    topPadding: Dp,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = PAGE_GUTTER, top = topPadding + 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

/** The uppercase label over a group, set like Settings'. */
@Composable
internal fun ScrobbleSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = 4.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/** The line between two rows of a card, inset to where the row's text starts. */
@Composable
internal fun ScrobbleHairline(start: Dp = 0.dp) {
    Box(
        Modifier
            .padding(start = start)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
    )
}

/** How far a pressed surface sinks: a little, quickly, and back a touch slower. */
@Composable
internal fun pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.97f): Float {
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    // Clickable emits PressInteraction for Enter/D-pad too. Focused controls stay
    // still, including pointer presses while keyboard/programmatic focus is retained.
    val noTransform = scrobbleReduceMotion() || focused
    val scale by animateFloatAsState(
        targetValue = if (pressed && !noTransform) pressedScale else 1f,
        animationSpec = if (noTransform) snap() else tween(
            durationMillis = if (pressed) 90 else 160,
            easing = ScrobblePressEasing,
        ),
        label = "press scale",
    )
    return if (noTransform) 1f else scale
}

/**
 * The one white pill a page or sheet leads with. [busy] swaps the label for
 * a small spinner without changing the pill's size or accessible name.
 */
@Composable
internal fun ScrobblePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = pressScale(interaction, 0.96f)
    val actionable = enabled && !busy
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = if (compact) 48.dp else 52.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = actionable,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                if (!actionable) disabled()
                if (busy) {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }
            }
            .padding(vertical = if (compact) 6.dp else 0.dp),
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = if (actionable) scale else 1f
                    scaleY = if (actionable) scale else 1f
                }
                .heightIn(min = if (compact) 36.dp else 52.dp)
                .clip(CircleShape)
                .background(colors.primary)
                .then(if (actionable && pressed) Modifier.liquidGlassRim(CircleShape) else Modifier)
                .alpha(if (enabled) 1f else 0.35f)
                .padding(horizontal = if (compact) 16.dp else 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Keep the real label measured while busy; the parent names the progress action.
            Text(
                text = label,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onPrimary,
                maxLines = 1,
                modifier = if (busy) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier,
            )
            if (busy) {
                CircularProgressIndicator(
                    color = colors.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp).clearAndSetSemantics { },
                )
            }
        }
    }
}

/** A quieter pill beside or instead of [ScrobblePrimaryButton]: grey fill, white label. */
@Composable
internal fun ScrobbleSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, 0.96f)
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = if (compact) 48.dp else 52.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = if (compact) 6.dp else 0.dp),
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = if (enabled) scale else 1f
                    scaleY = if (enabled) scale else 1f
                }
                .heightIn(min = if (compact) 36.dp else 52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                .alpha(if (enabled) 1f else 0.35f)
                .padding(horizontal = if (compact) 16.dp else 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

/**
 * A round glyph button for a page's top corner — sort, generate, more. It
 * reports its stable target bounds through [onAnchor] for a menu to hang off.
 * [active] is a selected color/rim state, never a persistent transform.
 */
@Composable
internal fun ScrobbleIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    filled: Boolean = true,
    onAnchor: ((Rect) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    val fill by animateColorAsState(
        targetValue = when {
            active -> colors.secondaryContainer
            filled -> colors.surfaceVariant
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "icon button fill",
    )
    val ink by animateColorAsState(
        targetValue = if (active) colors.onSecondaryContainer else colors.onSurface,
        animationSpec = tween(180),
        label = "icon button ink",
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .then(if (onAnchor != null) Modifier.onGloballyPositioned { onAnchor(it.boundsInWindow()) } else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = description
                selected = active
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = if (enabled) scale else 1f
                    scaleY = if (enabled) scale else 1f
                }
                .size(36.dp)
                .clip(CircleShape)
                .background(fill)
                .then(if (active && enabled) Modifier.liquidGlassRim(CircleShape) else Modifier)
                .alpha(if (enabled) 1f else 0.35f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * A short status line in a card: a spinner or a glyph, what is happening,
 * and one optional action on the right.
 */
@Composable
internal fun ScrobbleStatusCard(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scrobbleCard(RoundedCornerShape(20.dp))
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> leading()
            busy -> CircularProgressIndicator(
                color = colors.onSurface,
                trackColor = colors.onSurface.copy(alpha = 0.12f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
            icon != null -> Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!body.isNullOrBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.width(10.dp))
            ScrobbleSecondaryButton(label = action, onClick = onAction, compact = true)
        }
    }
}

/** A titled run of rows in one card, with an optional footnote under it. */
@Composable
internal fun ScrobbleGroup(
    label: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (label != null) ScrobbleSectionLabel(label)
        Column(Modifier.fillMaxWidth().scrobbleCard(RoundedCornerShape(20.dp)), content = content)
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}
