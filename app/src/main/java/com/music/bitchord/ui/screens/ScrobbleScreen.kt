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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.music.bitchord.data.generator.Play
import com.music.bitchord.data.generator.PlaySource
import com.music.bitchord.data.generator.ScrobbleGenerator
import com.music.bitchord.data.generator.ScrobbleHistory
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.YearMonth
import java.text.DateFormat
import java.util.Date

internal const val SCROBBLE_TAB_STATS = 0
internal const val SCROBBLE_TAB_GENERATE = 1
internal const val SCROBBLE_TAB_PLAYLISTS = 2

private data class GeneratorMode(val title: String, val subtitle: String, val group: String)

private val GeneratorModes = listOf(
    GeneratorMode("Song radio", "Starts from one song you pick", "Discover"),
    GeneratorMode("Similar artists", "Artists close to your top list", "Discover"),
    GeneratorMode("Never heard", "New to you, close to your taste", "Discover"),
    GeneratorMode("My mix", "Mylo plays and YouTube, blended", "Mood"),
    GeneratorMode("By genre", "Needs genre tags, not confirmed yet", "Mood"),
)

/**
 * Scrobble's UI shell. Statistics use Mylo's local aggregates; generated
 * queues and YouTube Music history use the persisted play-history store.
 */
@Composable
fun ScrobbleScreen(
    selectedSection: Int,
    bottomPadding: Dp,
    onSectionChange: (Int) -> Unit,
    onPlayPlaylist: (String, List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var summary by remember { mutableStateOf<ReplaySummary?>(null) }
    var loadingStats by remember { mutableStateOf(true) }
    var statsFailed by remember { mutableStateOf(false) }
    var historyPlays by remember { mutableStateOf<List<Play>>(emptyList()) }
    var loadingHistory by remember { mutableStateOf(true) }
    var historyFailed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            summary = ListeningStats.summary(ReplayPeriod.THIS_MONTH)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            summary = null
            statsFailed = true
        } finally {
            loadingStats = false
        }
    }

    // Refresh when moving between sections. Reopening the overlay also reloads
    // any plays or imported rows added while it was closed.
    LaunchedEffect(selectedSection) {
        loadingHistory = true
        try {
            historyPlays = withContext(Dispatchers.IO) { ScrobbleHistory.readAll() }
            historyFailed = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            historyPlays = emptyList()
            historyFailed = true
        } finally {
            loadingHistory = false
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
                SCROBBLE_TAB_STATS -> statsSection(summary, loadingStats, statsFailed, historyPlays, loadingHistory)
                SCROBBLE_TAB_GENERATE -> generateSection(
                    summary = summary,
                    loadingStats = loadingStats,
                    historyPlays = historyPlays,
                    loadingHistory = loadingHistory,
                    historyFailed = historyFailed,
                    onPlayPlaylist = onPlayPlaylist,
                )
                SCROBBLE_TAB_PLAYLISTS -> playlistsSection()
                else -> statsSection(summary, loadingStats, statsFailed, historyPlays, loadingHistory)
            }
        }
    }
}

private fun LazyListScope.statsSection(
    summary: ReplaySummary?,
    loading: Boolean,
    failed: Boolean,
    historyPlays: List<Play>,
    loadingHistory: Boolean,
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
                body = "Your existing listening data could not be read. Close and reopen Scrobble to retry.",
            )
        }
        summary == null || summary.isEmpty -> item(key = "stats-empty") {
            EmptyCard(
                title = "No Mylo listening data this month yet",
                body = "Mylo records listening on this device. Play a few tracks and your actual totals and top tracks will appear here. Enable background history sync in Settings to add date-labelled YouTube Music listens silently.",
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

    val zone = ZoneId.systemDefault()
    val currentMonth = YearMonth.now(zone)
    val monthlyHistory = historyPlays.filter {
        YearMonth.from(it.localDay(zone)) == currentMonth
    }
    item(key = "stats-source-split") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "History source split · This month",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when {
                loadingHistory -> Text(
                    "Loading source history…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                monthlyHistory.isEmpty() -> Text(
                    "History fills as you listen in Mylo or enable background YouTube Music sync in Settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    val sourceCounts = monthlyHistory.groupingBy { it.source }.eachCount()
                    listOf(
                        PlaySource.MYLO,
                        PlaySource.YOUTUBE_MUSIC,
                        PlaySource.LAST_FM,
                        PlaySource.LISTENBRAINZ,
                    ).filter { (sourceCounts[it] ?: 0) > 0 }.forEach { source ->
                        Text(
                            text = "${sourceLabel(source)} · ${sourceCounts[source]} listens",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    item(key = "stats-recent-history-heading") {
        Text(
            text = "Recent listens",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    when {
        loadingHistory -> item(key = "stats-recent-history-loading") {
            Text("Loading recent listens…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        monthlyHistory.isEmpty() -> item(key = "stats-recent-history-empty") {
            Text(
                "No listens recorded in the local history for this month.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> items(
            monthlyHistory.sortedByDescending { it.atMillis }.take(10),
            key = { "history-${it.source}-${it.songKey()}-${it.atMillis}" },
        ) { play ->
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(play.atMillis))
            ScrobbleRow(
                title = play.title.ifBlank { "Unknown track" },
                subtitle = "${play.artist.ifBlank { "Unknown artist" }} · ${sourceLabel(play.source)} · ${if (play.exact) "Exact" else "Approx."} · $date",
                leadingIcon = Icons.Rounded.History,
            )
        }
    }
}

private fun sourceLabel(source: PlaySource): String = when (source) {
    PlaySource.MYLO -> "Mylo"
    PlaySource.YOUTUBE_MUSIC -> "YouTube Music"
    PlaySource.LAST_FM -> "Last.fm"
    PlaySource.LISTENBRAINZ -> "ListenBrainz"
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

private fun LazyListScope.generateSection(
    summary: ReplaySummary?,
    loadingStats: Boolean,
    historyPlays: List<Play>,
    loadingHistory: Boolean,
    historyFailed: Boolean,
    onPlayPlaylist: (String, List<Song>) -> Unit,
) {
    val topTracks = summary?.songs?.take(25)?.map { it.song }.orEmpty()
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val recentSongs = ScrobbleGenerator.recent(historyPlays, now, zone, days = 7, limit = 25)
        .map { it.play.toSong() }
    val rediscoverSongs = ScrobbleGenerator.rediscover(
        historyPlays,
        now,
        zone,
        quietDays = 60,
        minPlays = 3,
        limit = 25,
    ).map { it.play.toSong() }

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
            text = "Play mixes built from your actual Mylo listening.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    when {
        loadingStats -> item(key = "gen-loading-stats") {
            Text("Loading listening statistics…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        topTracks.isNotEmpty() -> item(key = "gen-play-top-tracks") {
            Button(
                onClick = { onPlayPlaylist("Top tracks", topTracks) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Play top tracks")
            }
        }
    }
    when {
        loadingHistory -> item(key = "gen-loading-history") {
            Text("Loading recent listens…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        historyFailed -> item(key = "gen-history-error") {
            EmptyCard(
                title = "Couldn't load play history",
                body = "The local history file could not be read. Close and reopen Scrobble to retry.",
            )
        }
        recentSongs.isNotEmpty() -> item(key = "gen-play-recent") {
            Button(
                onClick = { onPlayPlaylist("Recent", recentSongs) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Play recent")
            }
        }
    }
    if (!loadingHistory && !historyFailed) {
        if (rediscoverSongs.isNotEmpty()) {
            item(key = "gen-rediscover") {
                Button(
                    onClick = { onPlayPlaylist("Rediscover", rediscoverSongs) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Rediscover forgotten tracks")
                }
            }
        } else {
            item(key = "gen-rediscover-empty") {
                Text(
                    text = "Rediscover unlocks after a song has been recorded on at least three separate days and then stays quiet for 60 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    listOf("Discover", "Mood").forEach { group ->
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
                    "${mode.subtitle} · Unavailable until genre metadata is connected"
                } else {
                    "${mode.subtitle} · Requires a recommendation source that is not connected yet"
                },
                leadingIcon = Icons.Rounded.Explore,
            )
        }
    }
}

private fun Play.toSong(): Song = Song(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = null,
    localUri = localUri,
)

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
            text = "No saved playlists yet. Scrobble can play mixes on demand; saving, freezing, and scheduled refresh are not wired yet.",
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
