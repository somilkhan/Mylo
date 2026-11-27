package com.music.bitchord.ui.scrobble.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.RankedEntry
import com.music.bitchord.ui.replay.formatListening
import com.music.bitchord.ui.replay.replayCount
import com.music.bitchord.ui.scrobble.MergedStatisticsTrack

private const val TRACK_LIMIT = 5

/** The leader as a larger card, then the next artists as compact rows with a share bar. */
internal fun LazyListScope.statsArtistItems(
    artists: List<RankedEntry>,
    onArtistClick: (RankedEntry) -> Unit,
) {
    if (artists.isEmpty()) return
    val maxMs = artists.maxOf { it.ms }.coerceAtLeast(1L)
    val featured = artists.first()
    item(key = "stats-artists-heading") {
        StatsSectionHeading(stringResource(R.string.top_artists))
    }
    item(key = "stats-artist-featured") {
        FeaturedArtistCard(entry = featured, onClick = { onArtistClick(featured) })
    }
    itemsIndexed(
        artists.drop(1),
        key = { index, entry -> "stats-artist-$index-${entry.browseId.orEmpty()}-${entry.title}" },
    ) { index, entry ->
        CompactArtistRow(
            rank = index + 2,
            entry = entry,
            maxMs = maxMs,
            onClick = { onArtistClick(entry) },
        )
    }
}

/** Tracks use the same row, lighter: no featured card, and only the top few. */
internal fun LazyListScope.statsTrackItems(
    tracks: List<MergedStatisticsTrack>,
    onPlaySong: (Song) -> Unit,
) {
    if (tracks.isEmpty()) return
    val shown = tracks.take(TRACK_LIMIT)
    val maxPlays = shown.maxOf { it.plays }.coerceAtLeast(1L)
    item(key = "stats-tracks-heading") {
        StatsSectionHeading(stringResource(R.string.scrobble_stats_heading_tracks))
    }
    itemsIndexed(
        shown,
        key = { _, track -> "stats-track-${track.identity}" },
    ) { index, track ->
        TrackRow(
            rank = index + 1,
            track = track,
            maxPlays = maxPlays,
            onPlaySong = onPlaySong,
        )
    }
}

@Composable
private fun FeaturedArtistCard(
    entry: RankedEntry,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val listened = formatListening(context, entry.ms)
    val plays = context.replayCount(entry.plays, R.plurals.replay_play_count)
    val topArtist = stringResource(R.string.top_artist)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceVariant,
        contentColor = colors.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StatsArtwork(
                url = entry.artworkUrl,
                fallback = entry.title.take(1).uppercase(),
                size = 96.dp,
                circular = true,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    topArtist,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    plays,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Text(
                    listened,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CompactArtistRow(
    rank: Int,
    entry: RankedEntry,
    maxMs: Long,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val plays = context.replayCount(entry.plays, R.plurals.replay_play_count)
    RankRow(
        rank = rank,
        title = entry.title,
        subtitle = null,
        artwork = {
            StatsArtwork(
                url = entry.artworkUrl,
                fallback = entry.title.take(1).uppercase(),
                size = 44.dp,
                circular = true,
            )
        },
        fraction = entry.ms.toFloat() / maxMs,
        trailing = plays,
        onClick = onClick,
    )
}

@Composable
private fun TrackRow(
    rank: Int,
    track: MergedStatisticsTrack,
    maxPlays: Long,
    onPlaySong: (Song) -> Unit,
) {
    val context = LocalContext.current
    val plays = context.replayCount(track.plays.toInt(), R.plurals.replay_play_count)
    val song = track.song
    val onRowClick: (() -> Unit)? = song?.let { playable -> { onPlaySong(playable) } }
    RankRow(
        rank = rank,
        title = track.title,
        subtitle = track.artist,
        artwork = {
            StatsArtwork(
                url = track.artworkUrl,
                fallback = "♫",
                size = 44.dp,
                circular = false,
            )
        },
        fraction = track.plays.toFloat() / maxPlays,
        trailing = plays,
        onClick = onRowClick,
    )
}

/** One ranked line. Merged for TalkBack; tappable rows are at least 56dp tall. */
@Composable
private fun RankRow(
    rank: Int,
    title: String,
    subtitle: String?,
    artwork: @Composable () -> Unit,
    fraction: Float,
    trailing: String,
    onClick: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val base = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .heightIn(min = 56.dp)
        .semantics(mergeDescendants = true) {}
    val rowModifier = if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
    Row(
        modifier = rowModifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            rank.toString(),
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        artwork()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatsProportionBar(fraction = fraction)
        }
        Text(
            trailing,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
