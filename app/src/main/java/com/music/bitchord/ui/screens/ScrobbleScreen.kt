package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
                SCROBBLE_TAB_STATS -> statsSection()
                SCROBBLE_TAB_GENERATE -> generateSection()
                SCROBBLE_TAB_PLAYLISTS -> playlistsSection()
                else -> statsSection()
            }
        }
    }
}

private fun LazyListScope.statsSection() {
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
            text = "Your listening history, in one place.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item(key = "stats-empty") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "No listening history available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Mylo play recording and external history imports are not connected yet. Real statistics will appear here once that pipeline is implemented.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                    "Unavailable until listening history and genre metadata are connected"
                } else {
                    "Unavailable until real listening history is connected"
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
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
