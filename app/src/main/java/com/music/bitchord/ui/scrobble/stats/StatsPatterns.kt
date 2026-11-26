package com.music.bitchord.ui.scrobble.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.ui.replay.formatDay
import com.music.bitchord.ui.replay.formatHour
import com.music.bitchord.ui.replay.formatListening
import com.music.bitchord.ui.replay.replayCount

/**
 * Insight cards, drawn only from what the summary holds: the peak hour, the busiest
 * day, and how much variety there was. A card is left out when its data is absent.
 */
internal fun LazyListScope.statsPatternItems(summary: ReplaySummary) {
    val peakHour = summary.peakHour
    val busiestDay = summary.busiestDay?.takeIf { summary.busiestDayMs > 0L }
    val hasVariety = summary.distinctArtists > 0 && summary.distinctSongs > 0
    if (peakHour == null && busiestDay == null && !hasVariety) return

    item(key = "stats-patterns-heading") {
        StatsSectionHeading(stringResource(R.string.listening_shape))
    }
    if (peakHour != null) {
        item(key = "stats-pattern-hour") {
            val context = LocalContext.current
            PatternCard(
                icon = Icons.Rounded.Schedule,
                text = stringResource(R.string.listen_most_around, formatHour(context, peakHour)),
            )
        }
    }
    if (busiestDay != null) {
        item(key = "stats-pattern-day") {
            val context = LocalContext.current
            PatternCard(
                icon = Icons.Rounded.Star,
                text = stringResource(
                    R.string.biggest_day_note,
                    formatDay(context, busiestDay),
                    formatListening(context, summary.busiestDayMs),
                ),
            )
        }
    }
    if (hasVariety) {
        item(key = "stats-pattern-variety") {
            val context = LocalContext.current
            val artists = context.replayCount(summary.distinctArtists, R.plurals.replay_artist_count)
            val songs = context.replayCount(summary.distinctSongs, R.plurals.replay_song_count)
            PatternCard(
                icon = Icons.Rounded.LibraryMusic,
                text = "You played $artists and $songs.",
            )
        }
    }
}

@Composable
private fun PatternCard(
    icon: ImageVector,
    text: String,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceVariant,
        contentColor = colors.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
        }
    }
}
