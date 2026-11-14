package com.music.bitchord.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.ListeningHistory
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

internal const val SCROBBLE_TAB_STATS = 0
internal const val SCROBBLE_TAB_GENERATE = 1
internal const val SCROBBLE_TAB_PLAYLISTS = 2

@Composable
fun ScrobbleScreen(
    selectedSection: Int,
    bottomPadding: Dp,
    onPlayPlaylist: (String, List<Song>) -> Unit,
    onBrowseMoods: () -> Unit,
    syncedHistory: List<Song> = emptyList(),
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val context = LocalContext.current
    val historyRevision by ListeningHistory.revision.collectAsStateWithLifecycle()
    var period by remember { mutableStateOf(ReplayPeriod.THIS_MONTH) }
    var summary by remember { mutableStateOf<ReplaySummary?>(null) }
    var loadingStats by remember { mutableStateOf(true) }
    var statsFailed by remember { mutableStateOf(false) }
    var listens by remember { mutableStateOf<List<ListeningHistory.Entry>>(emptyList()) }
    var loadingHistory by remember { mutableStateOf(true) }
    var historyFailed by remember { mutableStateOf(false) }
    var recommendations by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loadingRecommendations by remember { mutableStateOf(false) }
    var recommendationsFailed by remember { mutableStateOf(false) }
    var recommendationRequest by remember { mutableIntStateOf(0) }
    var openedPlaylist by remember { mutableStateOf<String?>(null) }
    val statsListState = rememberLazyListState()
    val generateListState = rememberLazyListState()
    val playlistsListState = rememberLazyListState()

    BackHandler(enabled = selectedSection == SCROBBLE_TAB_PLAYLISTS && openedPlaylist != null) {
        openedPlaylist = null
    }

    LaunchedEffect(context) {
        ScrobblePlaylistStore.init(context)
    }

    LaunchedEffect(context, historyRevision) {
        delay(250)
        loadingHistory = true
        try {
            listens = withContext(Dispatchers.IO) { ListeningHistory.read(context) }
            historyFailed = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            historyFailed = true
        } finally {
            loadingHistory = false
        }
    }

    LaunchedEffect(period, historyRevision) {
        loadingStats = true
        statsFailed = false
        try {
            summary = ListeningStats.summary(period)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            statsFailed = true
        } finally {
            loadingStats = false
        }
    }

    val topTracks = summary?.songs.orEmpty().map { it.song }.distinctBy(::songIdentity).take(25)
    val recentTracks = (syncedHistory + listens.sortedByDescending { it.startedAt }.map { it.song })
        .asSequence()
        .filter { it.videoId.isNotBlank() || !it.localUri.isNullOrBlank() || !it.localPath.isNullOrBlank() }
        .distinctBy(::songIdentity)
        .take(25)
        .toList()
    val rediscoverCutoff = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000
    val rediscoverTracks = listens.groupBy { songIdentity(it.song) }
        .values.mapNotNull { events ->
            val latest = events.maxByOrNull { it.startedAt } ?: return@mapNotNull null
            latest.song.takeIf { events.size >= 3 && latest.startedAt <= rediscoverCutoff }
        }.distinctBy(::songIdentity).take(25)

    LaunchedEffect(selectedSection, syncedHistory, listens, loadingHistory, summary, recommendationRequest) {
        if (selectedSection != SCROBBLE_TAB_GENERATE || loadingHistory) return@LaunchedEffect
        loadingRecommendations = true
        recommendationsFailed = false
        try {
            val excluded = (syncedHistory.map { it.videoId } + listens.map { it.song.videoId } + topTracks.map { it.videoId })
                .filter(String::isNotBlank).toSet()
            val result = YtMusicRepository.quickPicks(excluded)
            result.exceptionOrNull()?.let { throw it }
            recommendations = result.getOrNull().orEmpty()
                .filter { it.videoId.isNotBlank() }
                .distinctBy(::songIdentity)
            recommendationsFailed = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            recommendationsFailed = true
        } finally {
            loadingRecommendations = false
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedContent(
            targetState = selectedSection,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                else {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(220)) { direction * it / 12 } + fadeIn(tween(160))) togetherWith
                        (slideOutHorizontally(tween(180)) { -direction * it / 12 } + fadeOut(tween(120)))
                }
            },
            label = "scrobble-pages",
        ) { section ->
            LazyColumn(
                state = when (section) {
                    SCROBBLE_TAB_GENERATE -> generateListState
                    SCROBBLE_TAB_PLAYLISTS -> playlistsListState
                    else -> statsListState
                },
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 64.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (section) {
                    SCROBBLE_TAB_STATS -> {
                        item("stats-heading") {
                            PageHeading("Your listening", "A clear picture of what you play in Mylo")
                        }
                        item("stats-periods") {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ReplayPeriod.entries.forEach { option ->
                                    FilterChip(
                                        selected = period == option,
                                        onClick = { period = option },
                                        label = { Text(option.chip) },
                                    )
                                }
                            }
                        }
                        when {
                            loadingStats -> item("stats-loading") { LoadingLine("Calculating your listening") }
                            statsFailed -> item("stats-error") {
                                EmptyPanel("Statistics unavailable", "Your listening totals could not be read. Reopen Scrobble to retry.")
                            }
                            summary == null || summary?.isEmpty == true -> item("stats-empty") {
                                EmptyPanel("Your story starts here", "Play music in Mylo. Your listening time, play count and favourite tracks will build here.")
                            }
                            else -> {
                                item("stats-overview") {
                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Text(summary!!.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            MetricCard(formatListeningTime(summary!!.totalMs), "Listening", Modifier.weight(1f))
                                            MetricCard(summary!!.totalPlays.toString(), "Plays", Modifier.weight(1f))
                                            MetricCard(summary!!.distinctSongs.toString(), "Tracks", Modifier.weight(1f))
                                        }
                                        Text(
                                            text = "Across ${summary!!.distinctArtists} artists and ${summary!!.distinctAlbums} albums",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                item("stats-top-heading") { SectionHeading("Most played") }
                                items(summary!!.songs.take(12), key = { "stat-${songIdentity(it.song)}" }) { ranked ->
                                    TrackRow(
                                        song = ranked.song,
                                        supporting = "${ranked.plays} plays · ${ranked.ms / 60_000} min",
                                        onClick = { onPlayPlaylist("Most played", listOf(ranked.song)) },
                                    )
                                }
                                if (summary!!.artists.isNotEmpty()) {
                                    item("stats-artists-heading") { SectionHeading("Top artists") }
                                    items(summary!!.artists.take(5), key = { "artist-${it.title}" }) { artist ->
                                        InfoPanel(artist.title, "${artist.plays} plays · ${artist.ms / 60_000} min")
                                    }
                                }
                                if (summary!!.albums.isNotEmpty()) {
                                    item("stats-albums-heading") { SectionHeading("Top albums") }
                                    items(summary!!.albums.take(5), key = { "album-${it.title}-${it.subtitle.orEmpty()}" }) { album ->
                                        InfoPanel(album.title, "${album.subtitle.orEmpty()} · ${album.plays} plays")
                                    }
                                }
                                if (summary!!.genres.isNotEmpty()) {
                                    item("stats-genres-heading") { SectionHeading("Top genres") }
                                    items(summary!!.genres.take(5), key = { "genre-${it.title}" }) { genre ->
                                        InfoPanel(genre.title, "${genre.plays} plays · ${genre.ms / 60_000} min")
                                    }
                                }
                                summary!!.busiestDay?.let { day ->
                                    item("stats-busiest-day") {
                                        InfoPanel("Your biggest day", "$day · ${summary!!.busiestDayMs / 60_000} minutes")
                                    }
                                }
                                summary!!.peakHour?.let { hour ->
                                    item("stats-peak-hour") {
                                        InfoPanel("Peak listening hour", "%02d:00–%02d:00".format(hour, (hour + 1) % 24))
                                    }
                                }
                            }
                        }
                        item("stats-device-note") {
                            Text(
                                "These totals come from Mylo's on-device listening statistics. Account history remains separate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    SCROBBLE_TAB_GENERATE -> {
                        item("generate-heading") {
                            PageHeading("Made from your taste", "Ready-to-play mixes shaped by your listening")
                        }
                        if (loadingHistory) {
                            item("generate-history-loading") { LoadingLine("Reading your listening history") }
                        } else if (historyFailed) {
                            item("generate-history-error") {
                                EmptyPanel("History unavailable", "Mylo couldn't read your device listening history.")
                            }
                        } else {
                            item("mix-favourites") { GeneratedMixCardContent(
                                title = "Your favourites",
                                description = "Your most-played tracks from the selected statistics period",
                                songs = topTracks,
                                onPlay = onPlayPlaylist,
                                onSave = { ScrobblePlaylistStore.save("Your favourites", "Most-played tracks", topTracks) },
                            ) }
                            item("mix-recent") { GeneratedMixCardContent(
                                title = "Recently played",
                                description = "A fresh queue from your latest unique listens",
                                songs = recentTracks,
                                onPlay = onPlayPlaylist,
                                onSave = { ScrobblePlaylistStore.save("Recently played", "Your latest unique listens", recentTracks) },
                            ) }
                            if (rediscoverTracks.isNotEmpty()) {
                                item("mix-rediscover") { GeneratedMixCardContent(
                                    title = "Rediscover",
                                    description = "Tracks you played at least three times and haven't heard in 60 days",
                                    songs = rediscoverTracks,
                                    onPlay = onPlayPlaylist,
                                    onSave = { ScrobblePlaylistStore.save("Rediscover", "Played 3+ times and quiet for 60 days", rediscoverTracks) },
                                ) }
                            } else {
                                item("mix-rediscover-empty") {
                                    EmptyPanel("Rediscover unlocks over time", "Tracks played at least three times and left quiet for 60 days will appear here.")
                                }
                            }
                            when {
                                loadingRecommendations -> item("discover-loading") { LoadingLine("Finding fresh picks") }
                                recommendationsFailed -> item("discover-error") {
                                    EmptyPanel("Discovery couldn't load", "Check your connection and try again.")
                                }
                                recommendations.isEmpty() -> item("discover-empty") {
                                    EmptyPanel("No fresh picks yet", "YouTube Music did not return recommendations outside your recent listening.")
                                }
                                else -> item("discover-card") {
                                    GeneratedMixCardContent(
                                        title = "Discover",
                                        description = "New recommendations beyond your recent listening",
                                        songs = recommendations,
                                        onPlay = onPlayPlaylist,
                                        onSave = { ScrobblePlaylistStore.save("Discover", "New recommendations", recommendations) },
                                    )
                                }
                            }
                            item("discover-actions") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { recommendationRequest++ }, enabled = !loadingRecommendations) {
                                        Icon(Icons.Rounded.Refresh, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Refresh picks")
                                    }
                                    OutlinedButton(onClick = onBrowseMoods) {
                                        Icon(Icons.Rounded.Explore, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Moods")
                                    }
                                }
                            }
                        }
                    }

                    SCROBBLE_TAB_PLAYLISTS -> {
                        val selected = openedPlaylist?.let { title -> ScrobblePlaylistStore.playlists.firstOrNull { it.title == title } }
                        if (selected != null) {
                            item("playlist-back") {
                                TextButton(onClick = { openedPlaylist = null }) {
                                    Icon(Icons.Rounded.History, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("All playlists")
                                }
                            }
                            item("playlist-detail-heading") {
                                PageHeading(selected.title, selected.reason)
                                Button(onClick = { onPlayPlaylist(selected.title, selected.songs) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Rounded.PlayArrow, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Play all · ${selected.songs.size} tracks")
                                }
                            }
                            items(selected.songs.size, key = { index -> "saved-${selected.title}-${songIdentity(selected.songs[index])}-$index" }) { index ->
                                val song = selected.songs[index]
                                TrackRow(song, onClick = { onPlayPlaylist(selected.title, listOf(song)) })
                            }
                        } else {
                            item("playlists-heading") {
                                PageHeading("Your mixes", "Saved on this device, ready whenever you are")
                            }
                            if (ScrobblePlaylistStore.playlists.isEmpty()) {
                                item("playlists-empty") {
                                    EmptyPanel("No saved mixes yet", "Open Generate and save a mix. Your saved playlists won't change your listening history.")
                                }
                            } else {
                                items(ScrobblePlaylistStore.playlists.toList(), key = { "saved-${it.title}" }) { playlist ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(22.dp),
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                AsyncImage(
                                                    model = playlist.songs.firstOrNull()?.thumbnailUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(playlist.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                                    Text("${playlist.songs.size} tracks · Saved on device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Text(playlist.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(onClick = { onPlayPlaylist(playlist.title, playlist.songs) }) { Text("Play") }
                                                TextButton(onClick = { openedPlaylist = playlist.title }) { Text("Open") }
                                                TextButton(onClick = { ScrobblePlaylistStore.remove(playlist.title) }) { Text("Remove") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoPanel(title: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyPanel(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoadingLine(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrackRow(song: Song, supporting: String = "", onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(supporting.ifBlank { song.artist }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${song.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GeneratedMixCardContent(
    title: String,
    description: String,
    songs: List<Song>,
    onPlay: (String, List<Song>) -> Unit,
    onSave: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (songs.isEmpty()) {
                Text("Keep listening to unlock this mix.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("${songs.size} tracks ready", style = MaterialTheme.typography.labelLarge)
                songs.take(3).forEach { song ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = song.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPlay(title, songs) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play mix")
                    }
                    TextButton(onClick = onSave) { Text("Save") }
                }
            }
        }
    }
}

private fun formatListeningTime(totalMs: Long): String {
    val totalMinutes = totalMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun songIdentity(song: Song): String =
    song.localUri?.takeIf(String::isNotBlank)?.let { "local:$it" }
        ?: song.localPath?.takeIf(String::isNotBlank)?.let { "path:$it" }
        ?: song.videoId.takeIf(String::isNotBlank)?.let { "video:$it" }
        ?: "${song.title.lowercase()}|${song.artist.lowercase()}"
