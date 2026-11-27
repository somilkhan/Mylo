package com.music.bitchord.ui.scrobble.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.ui.components.CoverPlaceholder
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.SHELF_CARD_WIDTH
import com.music.bitchord.ui.components.ShimmerBox
import com.music.bitchord.ui.components.TRACK_NUMBER_WIDTH
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.icons.SfIcons
import java.text.NumberFormat

private val RowArtShape = RoundedCornerShape(8.dp)
private val ShelfArtShape = RoundedCornerShape(12.dp)

/** Grouped digits in the user's locale: 12,408 / 12.408 / 12 408. */
@Composable
internal fun rememberCountFormat(): NumberFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { NumberFormat.getIntegerInstance(locale) }
}

/**
 * A section's heading, with an optional trailing action in the accent — the
 * Home shelves' "Show all" heading, at the same size and inset.
 */
@Composable
internal fun StatsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PAGE_GUTTER, end = PAGE_GUTTER - 8.dp, top = 30.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}

/**
 * The period's scrobble count, large, as the page's one focal point, with
 * the distinct artists, albums and tracks behind it set quietly underneath.
 */
@Composable
internal fun StatsHero(
    scrobbles: Long,
    window: String,
    artists: Long,
    albums: Long,
    tracks: Long,
    modifier: Modifier = Modifier,
) {
    val format = rememberCountFormat()
    val breakdown = listOf(
        pluralStringResource(R.plurals.scrobble_stats_artist_count, artists.toQuantity(), format.format(artists)),
        pluralStringResource(R.plurals.scrobble_stats_album_count, albums.toQuantity(), format.format(albums)),
        pluralStringResource(R.plurals.scrobble_stats_track_count, tracks.toQuantity(), format.format(tracks)),
    ).joinToString("  ·  ")
    val label = stringResource(R.string.scrobble_stats_scrobbles)
    val number = format.format(scrobbles)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER)
            .padding(top = 28.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$number $label, $window. $breakdown"
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = number,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 56.sp,
                lineHeight = 62.sp,
                letterSpacing = (-1.4).sp,
                fontWeight = FontWeight.W800,
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        Text(
            text = window,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = breakdown,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
        )
    }
}

/**
 * One line of a top-artists or top-tracks chart: rank, name, an optional
 * second line, the play count, and under it all a hairline bar sized against
 * the chart's leader — the only colour on the row.
 */
@Composable
internal fun StatsRankedRow(
    rank: Int,
    title: String,
    subtitle: String?,
    plays: Long,
    fraction: Float,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    val format = rememberCountFormat()
    val playsLabel = pluralStringResource(
        R.plurals.scrobble_stats_scrobble_count,
        plays.toQuantity(),
        format.format(plays),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull("$rank", title, subtitle, playsLabel).joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank",
            modifier = Modifier.width(TRACK_NUMBER_WIDTH),
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.width(PAGE_GUTTER))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = format.format(plays),
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ProportionBar(fraction)
        }
    }
}

@Composable
private fun ProportionBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
        )
    }
}

/** An album card on the shelf: the Home shelves' card, with the play count under it. */
@Composable
internal fun StatsAlbumShelf(
    albums: List<StatsAlbumItem>,
    onClick: (StatsAlbumItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val format = rememberCountFormat()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(albums, key = { index, album -> "$index:${album.title}:${album.artist}" }) { _, album ->
            val plays = pluralStringResource(
                R.plurals.scrobble_stats_scrobble_count,
                album.plays.toQuantity(),
                format.format(album.plays),
            )
            Column(
                modifier = Modifier
                    .width(SHELF_CARD_WIDTH)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { onClick(album) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${album.title}, ${album.artist}, $plays"
                    },
            ) {
                Artwork(
                    url = album.artworkUrl,
                    icon = SfIcons.Album,
                    size = SHELF_CARD_WIDTH,
                    shape = ShelfArtShape,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = plays,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
        }
    }
}

internal data class StatsAlbumItem(
    val title: String,
    val artist: String,
    val plays: Long,
    val artworkUrl: String?,
)

/** A recent scrobble: the song row's shape, with when it was played at the end. */
@Composable
internal fun StatsRecentRow(
    title: String,
    artist: String,
    artworkUrl: String?,
    trailing: String,
    nowPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = PAGE_GUTTER, vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $artist, $trailing"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = artworkUrl, icon = SfIcons.MusicNote, size = 52.dp, shape = RowArtShape)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        when {
            busy -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            nowPlaying -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = SfIcons.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            else -> Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Artwork with Mylo's placeholder underneath, so a cover that loads late — or
 * one Last.fm doesn't have — never leaves a hole or changes the layout.
 */
@Composable
internal fun Artwork(
    url: String?,
    icon: ImageVector,
    size: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .thumbnailBorder(shape),
    ) {
        CoverPlaceholder(icon = icon, modifier = Modifier.fillMaxSize())
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A Last.fm avatar, or the account's initial when there is none. */
@Composable
internal fun StatsAvatar(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A whole-page message — not connected, nothing to show, or a failure with
 * nothing saved — centred under the header with at most one action.
 */
@Composable
internal fun StatsMessage(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER + 16.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(action, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Saved figures are on screen because the last refresh failed. */
@Composable
internal fun StatsStaleNotice(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PAGE_GUTTER, end = PAGE_GUTTER - 8.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = SfIcons.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.scrobble_stats_stale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
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

/** Stands in for the hero and the first chart while the first snapshot loads. */
@Composable
internal fun StatsSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = PAGE_GUTTER).padding(top = 30.dp)) {
            ShimmerBox(Modifier.width(84.dp).height(14.dp), RoundedCornerShape(50))
            Spacer(Modifier.height(14.dp))
            ShimmerBox(Modifier.width(188.dp).height(44.dp), RoundedCornerShape(10.dp))
            Spacer(Modifier.height(14.dp))
            ShimmerBox(Modifier.width(132.dp).height(14.dp), RoundedCornerShape(50))
            Spacer(Modifier.height(18.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.82f).height(12.dp), RoundedCornerShape(50))
        }
        Spacer(Modifier.height(40.dp))
        ShimmerBox(
            Modifier.padding(horizontal = PAGE_GUTTER).width(124.dp).height(18.dp),
            RoundedCornerShape(50),
        )
        Spacer(Modifier.height(10.dp))
        repeat(5) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(TRACK_NUMBER_WIDTH + PAGE_GUTTER))
                Column(Modifier.weight(1f)) {
                    ShimmerBox(
                        Modifier.fillMaxWidth(SkeletonWidths[index % SkeletonWidths.size]).height(14.dp),
                        RoundedCornerShape(50),
                    )
                    Spacer(Modifier.height(12.dp))
                    ShimmerBox(Modifier.fillMaxWidth().height(3.dp), RoundedCornerShape(50))
                }
            }
        }
    }
}

private val SkeletonWidths = listOf(0.62f, 0.48f, 0.7f, 0.4f, 0.55f)

/** Plural quantity for a count that can outgrow an Int. */
internal fun Long.toQuantity(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
