package com.music.bitchord.ui.scrobble.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.ShimmerBox
import com.music.bitchord.ui.icons.SfIcons

private val RowArtShape = RoundedCornerShape(8.dp)
internal val ListArtSize = 52.dp

/**
 * The list's heading: what it shows, and on the right a small button with
 * that list's symbol that opens the menu of lists. [onAnchor] gets the
 * button's bounds, which the menu hangs off. A hairline fades in under it once
 * rows pass beneath, while it is pinned.
 */
@Composable
internal fun StatsListHeader(
    title: String,
    icon: ImageVector,
    menuOpen: Boolean,
    pinned: Boolean,
    onOpenMenu: () -> Unit,
    onAnchor: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val rule by animateFloatAsState(if (pinned) 1f else 0f, tween(180), label = "list header rule")
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = PAGE_GUTTER + 4.dp, end = PAGE_GUTTER, top = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            ListModeButton(icon, menuOpen, onOpenMenu, onAnchor)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .graphicsLayer { alpha = rule }
                .background(colors.onSurface.copy(alpha = 0.1f)),
        )
    }
}

@Composable
private fun ListModeButton(
    icon: ImageVector,
    menuOpen: Boolean,
    onClick: () -> Unit,
    onAnchor: (Rect) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Pressed, and while its menu is up, the button sinks a little — the
    // menu reads as coming out of it.
    val scale by animateFloatAsState(
        targetValue = if (pressed || menuOpen) 0.94f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 90 else 160),
        label = "list mode button",
    )
    val label = stringResource(R.string.scrobble_stats_list_change)
    Row(
        modifier = Modifier
            .onGloballyPositioned { onAnchor(it.boundsInWindow()) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(colors.surfaceVariant)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 10.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Icon(
            SfIcons.ChevronUpDown,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
    }
}

/** "Today", "Yesterday", "Monday", "12 November": where a day's scrobbles start. */
@Composable
internal fun StatsDayHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PAGE_GUTTER + 4.dp, end = PAGE_GUTTER, top = 18.dp, bottom = 6.dp)
            .semantics { heading() },
    )
}

/**
 * One row of any of the lists: artwork — round for an artist — the name and
 * a second line, and whatever goes at the end. A rank, when there is one, sits
 * small in front of the artwork.
 */
@Composable
internal fun StatsListRow(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    placeholder: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    round: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = PAGE_GUTTER, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(26.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (round) {
            StatsAvatar(url = artworkUrl, name = title, size = ListArtSize)
        } else {
            Artwork(url = artworkUrl, icon = placeholder, size = ListArtSize, shape = RowArtShape)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** A play count at the end of a ranked row: the figure, and "plays" under it. */
@Composable
internal fun StatsPlays(plays: Long, busy: Boolean = false) {
    Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
        if (busy) {
            StatsRowSpinner()
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = rememberCountFormat().format(plays),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = pluralStringResource(R.plurals.scrobble_stats_plays_unit, plays.toQuantity()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** When a recent scrobble was played, or that it is playing now. */
@Composable
internal fun StatsWhen(text: String, nowPlaying: Boolean, busy: Boolean = false) {
    when {
        busy -> StatsRowSpinner()
        nowPlaying -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = SfIcons.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        else -> Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatsRowSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The end of the loaded history: a spinner while more comes, or a retry. */
@Composable
internal fun StatsListFooter(loading: Boolean, failed: Boolean, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = PAGE_GUTTER),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> StatsRowSpinner()
            failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.scrobble_stats_load_more_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(R.string.retry),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** A quiet line in place of rows when a list has none. */
@Composable
internal fun StatsListEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 40.dp),
    )
}

/** Rows in the list's shape while its first page loads. */
@Composable
internal fun StatsListSkeleton(rows: Int = 6) {
    Column(Modifier.fillMaxWidth()) {
        repeat(rows) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PAGE_GUTTER, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerBox(Modifier.size(ListArtSize), RowArtShape)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    ShimmerBox(
                        Modifier.fillMaxWidth(SkeletonWidths[index % SkeletonWidths.size]).height(14.dp),
                        RoundedCornerShape(50),
                    )
                    Spacer(Modifier.height(8.dp))
                    ShimmerBox(
                        Modifier.fillMaxWidth(SkeletonWidths[(index + 2) % SkeletonWidths.size] * 0.6f).height(12.dp),
                        RoundedCornerShape(50),
                    )
                }
            }
        }
    }
}

private val SkeletonWidths = listOf(0.62f, 0.48f, 0.7f, 0.4f, 0.55f)
