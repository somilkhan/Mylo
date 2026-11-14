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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
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

private enum class ScrobbleMixMode(val title: String, val description: String) {
    FAVOURITES("Your favourites", "Your most-played tracks from Mylo listening stats"),
    RECENT("Recently played", "Your latest unique listens"),
    TASTE_MIX("Taste mix", "A balanced blend of favourites, recent listens and fresh picks"),
    RADIO("Song radio", "Build a radio mix from a track you choose"),
    DISCOVER("Discover", "Fresh YouTube Music picks outside your listening history"),
    REDISCOVER("Rediscover", "Tracks played 3+ times on this device and quiet for 60 days"),
}

internal const val SCROBBLE_TAB_STATS = 0
internal const val SCROBBLE_TAB_GENERATE = 1
internal const val SCROBBLE_TAB_PLAYLISTS = 2

@Composable
fun ScrobbleScreen(
    selectedSection: Int,
    bottomPadding: Dp,
    onPlayPlaylist: (String, List<Song>, Int) -> Unit,
    onBrowseMoods: () -> Unit,
    syncedHistory: List<Song> = emptyList(),
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val context = LocalContext.current
    val historyRevision by ListeningHistory.revision.collectAsStateWithLifecycle()
    var period by remember { mutableStateOf(ReplayPeriod.THIS_MONTH) }
    var summary by remember { mutableStateOf<ReplaySummary?>(null) }
    var allTimeSummary by remember { mutableStateOf<ReplaySummary?>(null) }
    var loadingAllTimeStats by remember { mutableStateOf(true) }
    var loadingStats by remember { mutableStateOf(true) }
    var statsFailed by remember { mutableStateOf(false) }
    var listens by remember { mutableStateOf<List<ListeningHistory.Entry>>(emptyList()) }
    var loadingHistory by remember { mutableStateOf(true) }
    var historyFailed by remember { mutableStateOf(false) }
    var recommendations by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loadingRecommendations by remember { mutableStateOf(false) }
    var recommendationsFailed by remember { mutableStateOf(false) }
    var recommendationRequest by remember { mutableIntStateOf(0) }
    var mixMode by remember { mutableStateOf(ScrobbleMixMode.FAVOURITES) }
    var selectedSeed by remember { mutableStateOf<Song?>(null) }
    var mixTrackCount by remember { mutableIntStateOf(20) }
    var generationRequest by remember { mutableIntStateOf(0) }
    var generatingMix by remember { mutableStateOf(false) }
    var generationError by remember { mutableStateOf<String?>(null) }
    var generatedMix by remember { mutableStateOf<List<Song>>(emptyList()) }
    var generatedTitle by remember { mutableStateOf("") }
    var mixSaved by remember { mutableStateOf(false) }
    var pendingRemovePlaylist by remember { mutableStateOf<String?>(null) }
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

    LaunchedEffect(historyRevision) {
        loadingAllTimeStats = true
        try {
            allTimeSummary = ListeningStats.summary(ReplayPeriod.ALL_TIME)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            allTimeSummary = null
        } finally {
            loadingAllTimeStats = false
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

    val topTracks = summary?.songs.orEmpty()
        .sortedWith(compareByDescending { it.plays }.thenByDescending { it.ms })
        .map { it.song }.distinctBy(::songIdentity).take(25)
    val recentTracks = (syncedHistory + listens.sortedByDescending { it.startedAt }.map { it.song })
        .asSequence()
        .filter { it.videoId.isNotBlank() || !it.localUri.isNullOrBlank() || !it.localPath.isNullOrBlank() }
        .distinctBy(::songIdentity)
        .take(25)
        .toList()
    val allTimePlayCounts = allTimeSummary?.songs.orEmpty().associate { songIdentity(it.song) to it.plays }
    val rediscoverTracks = buildRediscoverMix(listens, allTimePlayCounts, System.currentTimeMillis())

    LaunchedEffect(recentTracks, topTracks) {
        if (selectedSeed == null) selectedSeed = (recentTracks + topTracks).firstOrNull { it.videoId.isNotBlank() }
    }

    LaunchedEffect(generationRequest) {
        if (generationRequest == 0) return@LaunchedEffect
        generatingMix = true
        generationError = null
        mixSaved = false
        generatedMix = emptyList()
        try {
            val generated = when (mixMode) {
                ScrobbleMixMode.FAVOURITES -> topTracks.take(mixTrackCount)
                ScrobbleMixMode.RECENT -> recentTracks.take(mixTrackCount)
                ScrobbleMixMode.TASTE_MIX -> {
                    val excluded = (syncedHistory.map { it.videoId } + listens.map { it.song.videoId }).filter(String::isNotBlank).toSet()
                    val fresh = recommendations.ifEmpty {
                        runCatching { YtMusicRepository.quickPicks(excluded).getOrThrow() }.getOrDefault(emptyList())
                    }
                    (topTracks.take(12) + recentTracks.take(12) + fresh.filter { it.videoId.isNotBlank() && it.videoId !in excluded })
                        .distinctBy(::songIdentity).shuffled().take(mixTrackCount)
                }
                ScrobbleMixMode.REDISCOVER -> rediscoverTracks.take(mixTrackCount)
                ScrobbleMixMode.DISCOVER -> {
                    val excluded = (syncedHistory.map { it.videoId } + listens.map { it.song.videoId } + topTracks.map { it.videoId })
                        .filter(String::isNotBlank).toSet()
                    (recommendations.ifEmpty { YtMusicRepository.quickPicks(excluded).getOrThrow() })
                        .filter { it.videoId.isNotBlank() && it.videoId !in excluded }
                        .distinctBy(::songIdentity).take(mixTrackCount)
                }
                ScrobbleMixMode.RADIO -> {
                    val seed = selectedSeed?.takeIf { it.videoId.isNotBlank() }
                        ?: throw IllegalStateException("Choose a YouTube Music track for Song radio")
                    YtMusicRepository.radio(seed.videoId).getOrThrow()
                        .filter { it.videoId.isNotBlank() && it.videoId != seed.videoId }
                        .distinctBy(::songIdentity).take(mixTrackCount)
                }
            }
            if (generated.isEmpty()) throw IllegalStateException("No tracks were available for this mix. Try another mode or refresh your listening data.")
            generatedMix = generated
            generatedTitle = when (mixMode) {
                ScrobbleMixMode.FAVOURITES -> "Your favourites"
                ScrobbleMixMode.RECENT -> "Recently played"
                ScrobbleMixMode.TASTE_MIX -> "Your taste mix"
                ScrobbleMixMode.RADIO -> "Radio · ${selectedSeed?.title.orEmpty()}"
                ScrobbleMixMode.DISCOVER -> "Discover"
                ScrobbleMixMode.REDISCOVER -> "Rediscover"
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            generationError = error.message ?: "Couldn't generate this mix. Try again."
        } finally {
            generatingMix = false
        }
    }

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
                 .filter { it.videoId.isNotBlank() && it.videoId !in excluded }
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
        pendingRemovePlaylist?.let { title ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingRemovePlaylist = null },
                title = { Text("Remove saved mix?") },
                text = { Text("‘$title’ will be removed from this device. Your listening history will stay untouched.") },
                confirmButton = {
                    TextButton(onClick = {
                        ScrobblePlaylistStore.remove(title)
                        if (openedPlaylist == title) openedPlaylist = null
                        pendingRemovePlaylist = null
                    }) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { pendingRemovePlaylist = null }) { Text("Cancel") } },
            )
        }
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 104.dp, bottom = bottomPadding + 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
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
                                items(summary!!.songs.sortedWith(compareByDescending { it.plays }.thenByDescending { it.ms }).take(12), key = { "stat-${songIdentity(it.song)}" }) { ranked ->
                                    TrackRow(
                                        song = ranked.song,
                                        supporting = "${ranked.plays} ${if (ranked.plays == 1) "play" else "plays"} · ${formatListeningTime(ranked.ms)}",
                                        onClick = { onPlayPlaylist("Most played", listOf(ranked.song), 0) },
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
                            PageHeading("Make a mix", "Choose what you want to hear. Mylo builds the queue from your listening and YouTube Music.")
                        }
                        item("generate-modes") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Start with", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ScrobbleMixMode.entries.chunked(2).forEach { rowModes ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            rowModes.forEach { mode ->
                                                FilterChip(
                                                    selected = mixMode == mode,
                                                    enabled = !generatingMix,
                                                    onClick = { mixMode = mode; generatedMix = emptyList(); generationError = null; mixSaved = false },
                                                    label = { Text(mode.title) },
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(mixMode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (mixMode == ScrobbleMixMode.RADIO) {
                            item("generate-seed") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Choose a starting track", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    val seeds = (recentTracks + topTracks).filter { it.videoId.isNotBlank() }.distinctBy(::songIdentity).take(12)
                                    if (seeds.isEmpty()) {
                                        EmptyPanel("No radio seed yet", "Play or sync a YouTube Music track first. Local-only files cannot start YouTube Music radio.")
                                    } else {
                                        seeds.forEach { song ->
                                            Row(
                                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                                    .background(if (selectedSeed?.videoId == song.videoId) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
                                                    .clickable(enabled = !generatingMix) { selectedSeed = song }.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                AsyncImage(model = song.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                                                Spacer(Modifier.width(10.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                if (selectedSeed?.videoId == song.videoId) Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (mixMode == ScrobbleMixMode.REDISCOVER && loadingAllTimeStats) {
                            item("rediscover-loading") { LoadingLine("Checking your all-time play counts") }
                        }
                        item("generate-count") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Mix length", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.weight(1f))
                                    Text("${mixTrackCount} tracks", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Slider(value = mixTrackCount.toFloat(), onValueChange = { mixTrackCount = it.toInt().coerceIn(5, 35) }, valueRange = 5f..35f, steps = 29, enabled = !generatingMix)
                            }
                        }
                        item("generate-action") {
                            Button(
                                onClick = { generationRequest++ },
                                enabled = !generatingMix && !loadingHistory &&
                                    (mixMode !in setOf(ScrobbleMixMode.FAVOURITES, ScrobbleMixMode.TASTE_MIX) || !loadingStats) &&
                                    (mixMode != ScrobbleMixMode.REDISCOVER || !loadingAllTimeStats) &&
                                    (mixMode != ScrobbleMixMode.RADIO || selectedSeed?.videoId?.isNotBlank() == true),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (generatingMix) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.PlayArrow, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (generatingMix) "Building your mix…" else "Generate mix")
                            }
                        }
                        generationError?.let { error ->
                            item("generate-error") { EmptyPanel("Couldn't build this mix", error) }
                        }
                        if (generatedMix.isNotEmpty()) {
                            item("generated-result-heading") {
                                PageHeading(generatedTitle, "${generatedMix.size} tracks ready")
                            }
                            itemsIndexed(generatedMix, key = { _, song -> "generated-${songIdentity(song)}" }) { index, song ->
                                TrackRow(song, onClick = { onPlayPlaylist(generatedTitle, generatedMix, index) })
                            }
                            item("generated-actions") {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = { onPlayPlaylist(generatedTitle, generatedMix, 0) }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Rounded.PlayArrow, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Play mix")
                                    }
                                    OutlinedButton(onClick = {
                                        ScrobblePlaylistStore.save(generatedTitle, mixMode.description, generatedMix)
                                        mixSaved = true
                                    }, enabled = !mixSaved) { Text(if (mixSaved) "Saved" else "Save") }
                                }
                            }
                        }
                        item("generate-discovery-refresh") {
                            OutlinedButton(onClick = { recommendationRequest++ }, enabled = !loadingRecommendations && !generatingMix, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Refresh, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (loadingRecommendations) "Refreshing picks…" else "Refresh discovery")
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
                                Button(onClick = { onPlayPlaylist(selected.title, selected.songs, 0) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Rounded.PlayArrow, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Play all · ${selected.songs.size} tracks")
                                }
                            }
                            items(selected.songs.size, key = { index -> "saved-${selected.title}-${songIdentity(selected.songs[index])}-$index" }) { index ->
                                val song = selected.songs[index]
                                TrackRow(song, onClick = { onPlayPlaylist(selected.title, selected.songs, index) })
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
                                    var menuExpanded by remember(playlist.title) { mutableStateOf(false) }
                                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                                .clickable { openedPlaylist = playlist.title },
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            AsyncImage(
                                                model = playlist.songs.firstOrNull()?.thumbnailUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(68.dp).clip(RoundedCornerShape(14.dp)),
                                            )
                                            Spacer(Modifier.width(14.dp))
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(playlist.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("${playlist.songs.size} tracks · On this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(playlist.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Box {
                                                IconButton(onClick = { menuExpanded = true }) {
                                                    Icon(Icons.Rounded.MoreVert, contentDescription = "More playlist actions")
                                                }
                                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                                    DropdownMenuItem(text = { Text("Open") }, onClick = { menuExpanded = false; openedPlaylist = playlist.title })
                                                    DropdownMenuItem(
                                                        text = { Text("Remove") },
                                                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                                                        onClick = { menuExpanded = false; pendingRemovePlaylist = playlist.title },
                                                    )
                                                }
                                            }
                                        }
                                        Button(onClick = { onPlayPlaylist(playlist.title, playlist.songs, 0) }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Rounded.PlayArrow, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Play mix")
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
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = songs.firstOrNull()?.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (songs.isEmpty()) "Keep listening to unlock this mix" else "${songs.size} tracks", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (songs.isEmpty()) {
            Text("Your mix will appear here as your listening history grows.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
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

private fun formatListeningTime(totalMs: Long): String {
    val totalMinutes = totalMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

