package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Tab indices used by the scrobble page's bar. */
internal const val SCROBBLE_TAB_STATS = 0
internal const val SCROBBLE_TAB_GENERATE = 1
internal const val SCROBBLE_TAB_PLAYLISTS = 2

/** Where a generated playlist's listening comes from. YouTube Music is the default. */
internal enum class ScrobbleSource(val label: String) {
    YOUTUBE_MUSIC("YouTube Music"),
    LAST_FM("Last.fm"),
    LISTENBRAINZ("ListenBrainz"),
    COMBINED("Combined"),
}

private val SourceHints = mapOf(
    ScrobbleSource.YOUTUBE_MUSIC to "Default. Uses your YouTube Music history and your Mylo plays.",
    ScrobbleSource.LAST_FM to "Uses your Last.fm history. Connect Last.fm first.",
    ScrobbleSource.LISTENBRAINZ to "Uses your ListenBrainz history. Connect ListenBrainz first.",
    ScrobbleSource.COMBINED to "Blends every connected source. Mylo plays count once.",
)

/** One generator mode. Three groups, at most three modes each. */
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
 * History import and playback-history recording are not wired into this screen yet.
 * Do not show fabricated listening totals or tracks as if they were user data.
 */

/**
 * The scrobble page: Statistics, Generate and Playlists.
 *
 * Until the history pipeline is integrated, this screen deliberately avoids
 * presenting sample content as real listening data. The bar that switches
 * sections is drawn by MainActivity, so this composable does not draw navigation.
 *
 * [bottomPadding] clears the floating bars, which sit over the page.
 */
@Composable
fun ScrobbleScreen(
    selectedSection: Int,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    var source by remember { mutableStateOf(ScrobbleSource.YOUTUBE_MUSIC) }
    val generated = remember { mutableStateListOf<String>() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 58.dp,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (selectedSection) {
                SCROBBLE_TAB_STATS -> statsSection()
                SCROBBLE_TAB_GENERATE -> generateSection(
                    source = source,
                    onSourceChange = { source = it },
                    onGenerate = { /* Real playlist generation is not wired to history yet. */ },
                )
                SCROBBLE_TAB_PLAYLISTS -> playlistsSection(generated)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.statsSection() {
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
            text = "Last 30 days. Mylo plays are exact, YouTube plays are approximate.",
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
                text = "History import and Mylo play recording are not connected yet. Your real statistics will appear here once that pipeline is implemented.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item(key = "stats-top-empty") {
        Text(
            text = "Top tracks will appear after listening history is connected.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.generateSection(
    source: ScrobbleSource,
    onSourceChange: (ScrobbleSource) -> Unit,
    onGenerate: (GeneratorMode) -> Unit,
) {
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
            text = "Build a playlist from your listening.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item(key = "gen-sources") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ScrobbleSource.entries.forEach { option ->
                FilterChip(
                    selected = option == source,
                    onClick = { onSourceChange(option) },
                    label = { Text(option.label) },
                )
            }
        }
    }
    item(key = "gen-hint") {
        Text(
            text = SourceHints.getValue(source),
            style = MaterialTheme.typography.bodySmall,
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
            SampleRow(
                title = mode.title,
                subtitle = if (mode.title == "By genre") {
                    "Unavailable until real listening history and genre metadata are connected"
                } else {
                    "Not available until listening history is connected"
                },
                trailing = null,
                onClick = null,
                leadingIcon = Icons.Rounded.Explore,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.playlistsSection(generated: List<String>) {
    item(key = "pl-title") {
        Text(
            text = "Playlists",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    item(key = "pl-lede") {
        Text(
            text = "Generated on this device. Refresh weekly or freeze.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (generated.isEmpty()) {
        item(key = "pl-empty") {
            Text(
                text = "Nothing generated yet. Pick a mode in Generate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    } else {
        items(generated, key = { it }) { name ->
            SampleRow(
                title = name,
                subtitle = "Sample result. Refreshes weekly.",
                trailing = null,
                leadingIcon = Icons.Rounded.MusicNote,
                playable = true,
            )
        }
    }
}

/**
 * One row in the scrobble lists. Kept to a single shape so all sections read
 * as the same family.
 */
@Composable
private fun SampleRow(
    title: String,
    subtitle: String,
    trailing: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.History,
    playable: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
                maxLines = 1,
            )
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (playable) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
