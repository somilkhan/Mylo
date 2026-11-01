package com.music.bitchord.ui.screens


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.music.bitchord.data.YtMusicRepository
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
    onBrowseMoods: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var summary by remember { mutableStateOf<ReplaySummary?>(null) }
    var loadingStats by remember { mutableStateOf(true) }
    var statsFailed by remember { mutableStateOf(false) }
    var historyPlays by remember { mutableStateOf<List<Play>>(emptyList()) }
    var loadingHistory by remember { mutableStateOf(true) }
    var historyFailed by remember { mutableStateOf(false) }
    var recommendationRequest by remember { mutableIntStateOf(0) }
    var lastRecommendationRequest by remember { mutableIntStateOf(-1) }
    var recommendedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loadingRecommendations by remember { mutableStateOf(false) }
    var recommendationsReady by remember { mutableStateOf(false) }
    var recommendationsFailed by remember { mutableStateOf(false) }
    var openedPlaylistTitle by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(selectedSection, historyPlays, loadingHistory, recommendationRequest) {
        if (
            selectedSection != SCROBBLE_TAB_GENERATE ||
            loadingHistory ||
            recommendationRequest == lastRecommendationRequest
        ) return@LaunchedEffect

        lastRecommendationRequest = recommendationRequest
        loadingRecommendations = true
        recommendationsReady = false
        recommendationsFailed = false
        try {
            val excludedIds = historyPlays.mapNotNull { play ->
                play.videoId.takeIf { it.isNotBlank() }
            }.toSet()
            val result = YtMusicRepository.quickPicks(excludedIds)
            val error = result.exceptionOrNull()
            if (error != null) {
                if (error is CancellationException) throw error
                recommendationsFailed = true
            } else {
                recommendedSongs = result.getOrNull().orEmpty()
                    .asSequence()
                    .filter { it.videoId.isNotBlank() }
                    .distinctBy { it.videoId }
                    .toList()
                recommendationsReady = true
            }
        } catch (cancelled: CancellationException) {
            lastRecommendationRequest = -1
            throw cancelled
        } catch (_: Exception) {
            recommendationsFailed = true
        } finally {
            loadingRecommendations = false
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
                SCROBBLE_TAB_STATS -> statsSection(summary, loadingStats, statsFailed, historyPlays, loadingHistory, historyFailed)
                SCROBBLE_TAB_GENERATE -> generateSection(
                    historyPlays = historyPlays,
                    loadingHistory = loadingHistory,
                    historyFailed = historyFailed,
                    recommendations = recommendedSongs,
                    loadingRecommendations = loadingRecommendations,
                    recommendationsReady = recommendationsReady,
                    recommendationsFailed = recommendationsFailed,
                    onRetryRecommendations = { recommendationRequest += 1 },
                    onPlayPlaylist = onPlayPlaylist,
                    onSavePlaylist = ScrobblePlaylistStore::save,
                    onBrowseMoods = onBrowseMoods,
                )
                SCROBBLE_TAB_PLAYLISTS -> {
                    val openedPlaylist = openedPlaylistTitle?.let { title ->
                        ScrobblePlaylistStore.playlists.firstOrNull { it.title == title }
                    }
                    if (openedPlaylist != null) {
                        playlistDetailSection(
                            playlist = openedPlaylist,
                            onBack = { openedPlaylistTitle = null },
                            onPlayPlaylist = onPlayPlaylist,
                        )
                    } else {
                        openedPlaylistTitle = null
                        playlistsSection(
                            playlists = ScrobblePlaylistStore.playlists,
                            onOpenPlaylist = { openedPlaylistTitle = it },
                            onPlayPlaylist = onPlayPlaylist,
                            onRemovePlaylist = { title ->
                                ScrobblePlaylistStore.remove(title)
                                if (openedPlaylistTitle == title) openedPlaylistTitle = null
                            },
                        )
                    }
                }
                else -> statsSection(summary, loadingStats, statsFailed, historyPlays, loadingHistory, historyFailed)
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
    historyFailed: Boolean,
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
                historyFailed -> Text(
                    "Couldn't read local listening history. Reopen Scrobble to retry.",
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
        historyFailed -> item(key = "stats-recent-history-error") {
            EmptyCard(
                title = "Couldn't load recent listens",
                body = "The local history file could not be read. Close and reopen Scrobble to retry.",
            )
        }
        historyPlays.isEmpty() -> item(key = "stats-recent-history-empty") {
            Text(
                "No listens have been recorded in the local history yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> items(
            historyPlays.sortedByDescending { it.atMillis }.take(10),
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
    historyPlays: List<Play>,
    loadingHistory: Boolean,
    historyFailed: Boolean,
    recommendations: List<Song>,
    loadingRecommendations: Boolean,
    recommendationsReady: Boolean,
    recommendationsFailed: Boolean,
    onRetryRecommendations: () -> Unit,
    onPlayPlaylist: (String, List<Song>) -> Unit,
    onSavePlaylist: (String, String, List<Song>) -> Unit,
    onBrowseMoods: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val topTracks = ScrobbleGenerator.topTracks(
        historyPlays,
        now,
        zone,
        windowDays = 90,
        limit = 25,
    ).map { it.play.toSong() }
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
            text = "Play mixes built from your actual listening history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    when {
        loadingHistory -> item(key = "gen-loading-top-tracks") {
            Text("Loading top tracks…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        historyFailed -> item(key = "gen-top-tracks-error") {
            EmptyCard(
                title = "Couldn't load top tracks",
                body = "The local history file could not be read. Close and reopen Scrobble to retry.",
            )
        }
        topTracks.isNotEmpty() -> item(key = "gen-play-top-tracks") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Button(
                    onClick = { onPlayPlaylist("Top tracks", topTracks) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play top tracks")
                }
                TextButton(
                    onClick = {
                        onSavePlaylist("Top tracks", "Your most-played tracks from the last 90 days.", topTracks)
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Save to Playlists") }
            }
        }
        else -> item(key = "gen-top-tracks-empty") {
            Text(
                "Top tracks appear after Mylo or YouTube Music listening is recorded.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Button(
                    onClick = { onPlayPlaylist("Recent", recentSongs) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play recent")
                }
                TextButton(
                    onClick = {
                        onSavePlaylist("Recent", "Tracks you've listened to in the last seven days.", recentSongs)
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Save to Playlists") }
            }
        }
    }
    if (!loadingHistory && !historyFailed) {
        if (rediscoverSongs.isNotEmpty()) {
            item(key = "gen-rediscover") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Button(
                        onClick = { onPlayPlaylist("Rediscover", rediscoverSongs) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Rediscover forgotten tracks")
                    }
                    TextButton(
                        onClick = {
                            onSavePlaylist(
                                "Rediscover",
                                "Tracks played at least three times and left quiet for 60+ days.",
                                rediscoverSongs,
                            )
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Save to Playlists") }
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
    item(key = "gen-discover-heading") {
        Text(
            text = "Discover",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    when {
        loadingRecommendations -> item(key = "gen-discover-loading") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    "Finding recommendations from YouTube Music…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        recommendationsFailed -> {
            item(key = "gen-discover-error") {
                EmptyCard(
                    title = "Recommendations unavailable",
                    body = "YouTube Music couldn't load discovery picks. Check your connection and try again.",
                )
            }
            item(key = "gen-discover-retry") {
                TextButton(onClick = onRetryRecommendations) { Text("Try again") }
            }
        }
        recommendationsReady && recommendations.isEmpty() -> {
            item(key = "gen-discover-empty") {
                EmptyCard(
                    title = "No new recommendations",
                    body = "YouTube Music didn't return playable tracks outside your recorded listening history.",
                )
            }
            item(key = "gen-discover-refresh-empty") {
                TextButton(onClick = onRetryRecommendations) { Text("Refresh recommendations") }
            }
        }
        recommendationsReady -> {
            item(key = "gen-discover-play") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Button(
                        onClick = { onPlayPlaylist("Discover", recommendations) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(imageVector = Icons.Rounded.Explore, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play discovery picks")
                    }
                    TextButton(
                        onClick = {
                            onSavePlaylist(
                                "Discover",
                                "New YouTube Music picks outside your recorded listening history.",
                                recommendations,
                            )
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Save to Playlists") }
                }
            }
            item(key = "gen-discover-refresh") {
                TextButton(onClick = onRetryRecommendations) { Text("Refresh recommendations") }
            }
            val myMix = interleaveScrobbleMix(topTracks, recommendations)
            if (topTracks.isNotEmpty() && recommendations.isNotEmpty() && myMix.isNotEmpty()) {
                item(key = "gen-my-mix") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Button(
                            onClick = { onPlayPlaylist("My mix", myMix) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Play My mix")
                        }
                        TextButton(
                            onClick = {
                                onSavePlaylist(
                                    "My mix",
                                    "A blend of familiar favourites and new discovery picks.",
                                    myMix,
                                )
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("Save to Playlists") }
                    }
                }
            }
        }
    }
    item(key = "gen-mood-heading") {
        Text(
            text = "Mood & genres",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    item(key = "gen-browse-moods") {
        Button(
            onClick = onBrowseMoods,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Rounded.Explore, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Browse moods & genres")
        }
    }
}

/**
 * Alternates familiar tracks with fresh recommendations while preserving each
 * playable identity. Local files use their URI; catalogue tracks use videoId.
 */
internal fun interleaveScrobbleMix(
    topTracks: List<Song>,
    recommendations: List<Song>,
    limit: Int = 25,
): List<Song> {
    if (limit <= 0) return emptyList()
    val mixed = ArrayList<Song>(minOf(limit, topTracks.size + recommendations.size))
    val seen = HashSet<String>()

    fun append(song: Song?) {
        if (song == null || mixed.size >= limit) return
        val identity = song.localUri?.takeIf { it.isNotBlank() }?.let { "local:$it" }
            ?: song.videoId.takeIf { it.isNotBlank() }?.let { "video:$it" }
            ?: return
        if (seen.add(identity)) mixed += song
    }

    var index = 0
    while (mixed.size < limit && (index < topTracks.size || index < recommendations.size)) {
        append(topTracks.getOrNull(index))
        append(recommendations.getOrNull(index))
        index++
    }
    return mixed
}

private fun Play.toSong(): Song = Song(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    localUri = localUri,
)

private fun LazyListScope.playlistDetailSection(
    playlist: SavedScrobblePlaylist,
    onBack: () -> Unit,
    onPlayPlaylist: (String, List<Song>) -> Unit,
) {
    item(key = "pl-detail-back") {
        TextButton(onClick = onBack) {
            Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Playlists")
        }
    }
    item(key = "pl-detail-header") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(playlist.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "${playlist.songs.size} tracks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onPlayPlaylist(playlist.title, playlist.songs) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Play all")
            }
        }
    }
    items(playlist.songs, key = { song ->
        "pl-detail-${playlist.title}-${song.localUri ?: song.videoId}-${playlist.songs.indexOf(song)}"
    }) { song ->
        val index = playlist.songs.indexOf(song)
        ScrobbleRow(
            title = song.title.ifBlank { "Unknown track" },
            subtitle = song.artist.ifBlank { "Unknown artist" },
            artworkUrl = song.thumbnailUrl,
            onClick = { onPlayPlaylist(playlist.title, playlist.songs.drop(index)) },
        )
    }
}

private fun LazyListScope.playlistsSection(
    playlists: List<SavedScrobblePlaylist>,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (String, List<Song>) -> Unit,
    onRemovePlaylist: (String) -> Unit,
) {
    item(key = "pl-title") {
        Text(
            text = "Playlists",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    if (playlists.isEmpty()) {
        item(key = "pl-empty") {
            Text(
                text = "Save Top tracks, Recent, Rediscover or Discover from Generate. Saved mixes stay on this device until you remove them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    } else {
        items(playlists, key = { "scrobble-playlist-${it.title}" }) { playlist ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = playlist.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${playlist.songs.size} tracks · Saved on this device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onOpenPlaylist(playlist.title) }) {
                        Text("Open")
                    }
                    Button(onClick = { onPlayPlaylist(playlist.title, playlist.songs) }) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play")
                    }
                    TextButton(onClick = { onRemovePlaylist(playlist.title) }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrobbleRow(
    title: String,
    subtitle: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.History,
    artworkUrl: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
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
