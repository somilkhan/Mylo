package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary

internal const val SCROBBLE_TAB_STATS = 0
internal const val SCROBBLE_TAB_GENERATE = 1
internal const val SCROBBLE_TAB_PLAYLISTS = 2

private data class GeneratorMode(val title: String, val subtitle: String, val group: String)

private val GeneratorModes = listOf(
    GeneratorMode("Top tracks", "Played most in the last 90 days", "Your history"),
    GeneratorMode("Recent", "What you played this week", "Your history"),
    GeneratorMode("Rediscover", "Loved before, quiet for 60+ days", "Your history"),
    GeneratorMode("Song radio", "Starts from one song you pick", "Discover"),
    GeneratorMode("Similar artists", "Artists close to your top list", "Discover"),
    GeneratorMode("Never heard", "New to you, close to your taste", "Discover"),
    GeneratorMode("My mix", "Mylo plays and YouTube, blended", "Mood"),
    GeneratorMode("By genre", "Needs genre tags, not confirmed yet", "Mood"),
)

/**
 * Scrobble's UI shell. Navigation is local to this screen; history sources and
 * playlist actions stay unavailable until they are connected to real data.
 */
@Composable
fun ScrobbleScreen(
    selectedSection: Int,
    bottomPadding: Dp,
    onSectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var summary by remember { mutableStateOf<ReplaySummary?>(null) }
    var loadingStats by remember { mutableStateOf(false) }
    var statsFailed by remember { mutableStateOf(false) }

    LaunchedEffect(selectedSection) {
        if (selectedSection == SCROBBLE_TAB_STATS) {
            loadingStats = true
            statsFailed = false
            val result = runCatching { ListeningStats.summary(ReplayPeriod.THIS_MONTH) }
            summary = result.getOrNull()
            statsFailed = result.isFailure
            loadingStats = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 58.dp,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "scrobble-sections") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf("Statistics", "Generate", "Playlists").forEachIndexed { index, label ->
                        FilterChip(
                            selected = selectedSection == index,
                            onClick = { onSectionChange(index) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            when (selectedSection) {
                SCROBBLE_TAB_STATS -> statsSection(summary, loadingStats, statsFailed)
                SCROBBLE_TAB_GENERATE -> generateSection()
                SCROBBLE_TAB_PLAYLISTS -> playlistsSection()
                else -> statsSection(summary, loadingStats, statsFailed)
            }
        }
    }
}

private fun LazyListScope.statsSection(
    summary: ReplaySummary?,
    loading: Boolean,
    failed: Boolean,
) {
    item(key = "stats-title") {
        Text(
            text = "Statistics",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    item(key = "stats-lede") {
        Text(
            text = "Mylo listening · This month",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    when {
        loading -> item(key = "stats-loading") {
            Text("Loading listening statistics…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        failed -> item(key = "stats-error") {
            EmptyCard(
                title = "Couldn't load listening statistics",
                body = "Your existing listening data could not be read. Return to Statistics to retry.",
            )
        }
        summary == null || summary.isEmpty -> item(key = "stats-empty") {
            EmptyCard(
                title = "No Mylo listening data this month yet",
                body = "Mylo records listening on this device. Play a few tracks and your actual totals and top tracks will appear here. External YouTube Music history import is separate and is not connected yet.",
            )
        }
        else -> {
            item(key = "stats-overview") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = summary.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric(value = "${summary.minutes}", label = "Minutes")
                        Metric(value = "${summary.totalPlays}", label = "Plays")
                        Metric(value = "${summary.distinctSongs}", label = "Tracks")
                    }
                }
            }
            item(key = "stats-top-heading") {
                Text(
                    text = "Top tracks",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(summary.songs.take(10), key = { "stats-track-${it.song.videoId}" }) { ranked ->
                ScrobbleRow(
                    title = ranked.song.title,
                    subtitle = "${ranked.song.artist.ifBlank { "Unknown artist" }} · ${ranked.plays} plays · ${ranked.ms / 60_000} min",
                    leadingIcon = Icons.Rounded.History,
                )
            }
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RowScope.Metric(value: String, label: String) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LazyListScope.generateSection() {
    item(key = "gen-title") {
        Text(
            text = "Generate",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    item(key = "gen-lede") {
        Text(
            text = "Build playlists from your listening history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    listOf("Your history", "Discover", "Mood").forEach { group ->
        item(key = "gen-group-$group") {
            Text(
                text = group.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(
            GeneratorModes.filter { it.group == group },
            key = { "gen-${it.title}" },
        ) { mode ->
            ScrobbleRow(
                title = mode.title,
                subtitle = if (mode.title == "By genre") {
                    "${mode.subtitle} · Unavailable until listening history and genre metadata are connected"
                } else {
                    "${mode.subtitle} · Unavailable until real listening history is connected"
                },
                leadingIcon = Icons.Rounded.Explore,
            )
        }
    }
}

private fun LazyListScope.playlistsSection() {
    item(key = "pl-title") {
        Text(
            text = "Playlists",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    item(key = "pl-empty") {
        Text(
            text = "No generated playlists yet. Playlist generation will be enabled when real listening history is available.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ScrobbleRow(
    title: String,
    subtitle: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.History,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
