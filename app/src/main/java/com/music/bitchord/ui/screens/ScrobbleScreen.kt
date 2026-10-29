package com.music.bitchord.ui.screens

import android.content.Context
import android.content.SharedPreferences

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.generator.Play
import com.music.bitchord.data.generator.PlaySource
import com.music.bitchord.data.generator.ScrobbleGenerator
import com.music.bitchord.data.generator.ScrobbleHistory
import com.music.bitchord.data.generator.ScrobbleHistorySync
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.ZoneId
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
    var importingYouTube by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val syncPrefs = remember(appContext) {
        appContext.getSharedPreferences(ScrobbleHistorySync.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var autoSyncEnabled by remember(syncPrefs) {
        mutableStateOf(syncPrefs.getBoolean(ScrobbleHistorySync.KEY_ENABLED, false))
    }
    var lastSyncAt by remember(syncPrefs) {
        mutableStateOf(syncPrefs.getLong(ScrobbleHistorySync.KEY_LAST_SYNC_AT, 0L))
    }
    var lastSyncStatus by remember(syncPrefs) {
        mutableStateOf(syncPrefs.getString(ScrobbleHistorySync.KEY_LAST_STATUS, null))
    }
    var lastSyncAdded by remember(syncPrefs) {
        mutableStateOf(syncPrefs.getInt(ScrobbleHistorySync.KEY_LAST_ADDED, 0))
    }

    DisposableEffect(syncPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            scope.launch {
                when (key) {
                    ScrobbleHistorySync.KEY_ENABLED ->
                        autoSyncEnabled = prefs.getBoolean(ScrobbleHistorySync.KEY_ENABLED, false)
                    ScrobbleHistorySync.KEY_LAST_SYNC_AT ->
                        lastSyncAt = prefs.getLong(ScrobbleHistorySync.KEY_LAST_SYNC_AT, 0L)
                    ScrobbleHistorySync.KEY_LAST_STATUS ->
                        lastSyncStatus = prefs.getString(ScrobbleHistorySync.KEY_LAST_STATUS, null)
                    ScrobbleHistorySync.KEY_LAST_ADDED ->
                        lastSyncAdded = prefs.getInt(ScrobbleHistorySync.KEY_LAST_ADDED, 0)
                }
            }
        }
        syncPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { syncPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

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

    // Refresh when moving between sections and after background sync completes.
    // This also picks up plays recorded while the overlay was open.
    LaunchedEffect(selectedSection, lastSyncAt) {
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
                SCROBBLE_TAB_STATS -> statsSection(summary, loadingStats, statsFailed)
                SCROBBLE_TAB_GENERATE -> generateSection(
                    summary = summary,
                    loadingStats = loadingStats,
                    historyPlays = historyPlays,
                    loadingHistory = loadingHistory,
                    historyFailed = historyFailed,
                    importingYouTube = importingYouTube,
                    importMessage = importMessage,
                    autoSyncEnabled = autoSyncEnabled,
                    syncStatusText = backgroundSyncStatusText(
                        lastSyncStatus,
                        lastSyncAt,
                        lastSyncAdded,
                        autoSyncEnabled,
                    ),
                    onAutoSyncChange = { enabled ->
                        autoSyncEnabled = enabled
                        if (enabled) ScrobbleHistorySync.schedule(appContext)
                        else ScrobbleHistorySync.cancel(appContext)
                    },
                    onImportYouTubeHistory = {
                        scope.launch {
                            importingYouTube = true
                            importMessage = null
                            try {
                                val entries = YtMusicRepository.historyWithDates().getOrThrow()
                                val incoming = entries.map { entry ->
                                    Play(
                                        videoId = entry.song.videoId,
                                        title = entry.song.title,
                                        artist = entry.song.artist,
                                        source = PlaySource.YOUTUBE_MUSIC,
                                        atMillis = entry.playedAtMillis,
                                        exact = entry.exact,
                                        localUri = entry.song.localUri,
                                    )
                                }
                                val result = withContext(Dispatchers.IO) {
                                    val added = ScrobbleHistory.append(incoming)
                                    added to ScrobbleHistory.readAll()
                                }
                                historyPlays = result.second
                                historyFailed = false
                                importMessage = when {
                                    entries.isEmpty() -> "No date-labelled rows were recognized on the available history page; no timestamps were guessed."
                                    result.first == 0 -> "History is already up to date."
                                    else -> "Imported ${result.first} new listening entries from the available history page."
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                importMessage = "YouTube Music history import failed: ${error.message ?: "check account sign-in"}"
                            } finally {
                                importingYouTube = false
                            }
                        }
                    },
                    onPlayPlaylist = onPlayPlaylist,
                )
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
                body = "Your existing listening data could not be read. Close and reopen Scrobble to retry.",
            )
        }
        summary == null || summary.isEmpty -> item(key = "stats-empty") {
            EmptyCard(
                title = "No Mylo listening data this month yet",
                body = "Mylo records listening on this device. Play a few tracks and your actual totals and top tracks will appear here. You can import date-labelled YouTube Music history from Generate.",
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

private fun LazyListScope.generateSection(
    summary: ReplaySummary?,
    loadingStats: Boolean,
    historyPlays: List<Play>,
    loadingHistory: Boolean,
    historyFailed: Boolean,
    importingYouTube: Boolean,
    importMessage: String?,
    autoSyncEnabled: Boolean,
    syncStatusText: String,
    onAutoSyncChange: (Boolean) -> Unit,
    onImportYouTubeHistory: () -> Unit,
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
    item(key = "gen-import-youtube") {
        Button(
            onClick = onImportYouTubeHistory,
            enabled = !importingYouTube,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (importingYouTube) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(imageVector = Icons.Rounded.History, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (importingYouTube) "Importing YouTube Music history…" else "Import YouTube Music history")
        }
    }
    if (importMessage != null) {
        item(key = "gen-import-status") {
            Text(
                text = importMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item(key = "gen-background-sync") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Background sync",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Refresh YouTube history about every 12 hours when a network is available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = autoSyncEnabled,
                onCheckedChange = onAutoSyncChange,
                modifier = Modifier.semantics { contentDescription = "Background sync" },
            )
        }
    }
    item(key = "gen-background-sync-status") {
        Text(
            text = syncStatusText,
            style = MaterialTheme.typography.bodySmall,
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

private fun backgroundSyncStatusText(
    status: String?,
    lastSyncAt: Long,
    lastAdded: Int,
    enabled: Boolean,
): String {
    if (!enabled) {
        return if (lastSyncAt > 0L) {
            "Background sync is off. Last successful sync: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastSyncAt))} · $lastAdded new listens"
        } else {
            "Background sync is off."
        }
    }
    return when (status) {
        ScrobbleHistorySync.STATUS_SUCCESS ->
            "Last background sync: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastSyncAt))} · $lastAdded new listens"
        ScrobbleHistorySync.STATUS_NO_DATED_ROWS ->
            "Last sync found no date-labelled history rows; unsupported date groups were skipped."
        ScrobbleHistorySync.STATUS_WAITING_FOR_SIGN_IN ->
            "Waiting for Mylo's YouTube Music sign-in. Sync will check again on the next interval."
        ScrobbleHistorySync.STATUS_RETRYING ->
            "Background sync failed; it will retry when a network is available."
        else -> "Background sync is enabled; waiting for the first run."
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
