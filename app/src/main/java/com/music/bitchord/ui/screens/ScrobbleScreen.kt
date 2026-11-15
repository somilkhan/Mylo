package com.music.bitchord.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Sort
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.ListeningHistory
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.data.stats.RankedSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

private enum class ScrobbleMixMode(val title: String, val description: String) {
    FAVOURITES("Your favourites", "Your most-played tracks from Mylo listening stats"),
    RECENT("Recently played", "Your latest unique listens"),
    TASTE_MIX("Taste mix", "A balanced blend of favourites, recent listens and fresh picks"),
    RADIO("Song radio", "Build a radio mix from a track you choose"),
    ARTIST_MIX("Artist mix", "Explore more tracks from an artist you already enjoy"),
    NEVER_HEARD("Never heard", "Find catalogue picks missing from your listening history"),
    DISCOVER("Discover", "Fresh YouTube Music picks outside your listening history"),
    TAG("By genre", "Build a mix from a genre or tag"),
    LIBRARY("Your library", "Tracks saved to your YouTube Music library"),
    REDISCOVER("Rediscover", "Tracks played 3+ times on this device and quiet for 60 days"),
}

private val SCROBBLE_GENRE_TAGS = listOf("pop", "rock", "hip-hop", "electronic", "jazz", "lofi", "metal", "indie", "classical", "r&b", "ambient", "punk")

internal const val SCROBBLE_TAB_STATS = 0
internal const val SCROBBLE_TAB_GENERATE = 1
internal const val SCROBBLE_TAB_PLAYLISTS = 2

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScrobbleScreen(
    selectedSection: Int,
    bottomPadding: Dp,
    onPlayPlaylist: (String, List<Song>, Int) -> Unit,
    onBrowseMoods: () -> Unit,
    onViewPlaylists: () -> Unit,
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
    var allTimeStatsFailed by remember { mutableStateOf(false) }
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
    var tagQuery by remember { mutableStateOf("") }
    var mixTrackCount by remember { mutableIntStateOf(20) }
    var generationRequest by remember { mutableIntStateOf(0) }
    var generatingMix by remember { mutableStateOf(false) }
    var generationError by remember { mutableStateOf<String?>(null) }
    var generatedMix by remember { mutableStateOf<List<Song>>(emptyList()) }
    var generatedTitle by remember { mutableStateOf("") }
    var mixSaved by remember { mutableStateOf(false) }
    var pendingRemovePlaylist by remember { mutableStateOf<String?>(null) }
    var openedPlaylist by remember { mutableStateOf<String?>(null) }
    var playlistQuery by remember { mutableStateOf("") }
    var playlistSortNewest by remember { mutableStateOf(true) }
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
        allTimeStatsFailed = false
        try {
            allTimeSummary = ListeningStats.summary(ReplayPeriod.ALL_TIME)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            allTimeSummary = null
            allTimeStatsFailed = true
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
        .sortedWith(compareByDescending<RankedSong> { it.plays }.thenByDescending { it.ms })
        .map { it.song }.distinctBy(::songIdentity).take(25)
    val recentTracks = (syncedHistory + listens.sortedByDescending { it.startedAt }.map { it.song })
        .asSequence()
        .filter { it.videoId.isNotBlank() || !it.localUri.isNullOrBlank() || !it.localPath.isNullOrBlank() }
        .distinctBy(::songIdentity)
        .take(25)
        .toList()
    val allTimePlayCounts = allTimeSummary?.songs.orEmpty().associate { songIdentity(it.song) to it.plays }
    val rediscoverTracks = buildRediscoverMix(listens, allTimePlayCounts, System.currentTimeMillis())
    val savedPlaylists = ScrobblePlaylistStore.playlists.toList()
    val savedPlaylistForGeneratedMix = savedPlaylists.firstOrNull { playlist ->
        playlist.songs.map(::songIdentity) == generatedMix.map(::songIdentity)
    }

    LaunchedEffect(recentTracks, topTracks) {
        if (selectedSeed == null) selectedSeed = (recentTracks + topTracks).firstOrNull(::isYouTubeCatalogTrack)
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
                ScrobbleMixMode.TAG -> buildTagMix(
                    YtMusicRepository.search("${tagQuery.trim()} music", SearchFilter.SONGS).getOrThrow(),
                    mixTrackCount,
                )
                ScrobbleMixMode.LIBRARY -> buildLibraryMix(
                    YtMusicRepository.library().getOrThrow(),
                    mixTrackCount,
                )
                ScrobbleMixMode.REDISCOVER -> rediscoverTracks.take(mixTrackCount)
                ScrobbleMixMode.NEVER_HEARD -> {
                    val heard = (syncedHistory.map { it.videoId } + listens.map { it.song.videoId } +
                        topTracks.map { it.videoId } + recentTracks.map { it.videoId })
                        .filter(String::isNotBlank).toSet()
                    buildNeverHeardMix(
                        YtMusicRepository.quickPicks(heard).getOrThrow(),
                        heard,
                        mixTrackCount,
                    )
                }
                ScrobbleMixMode.DISCOVER -> {
                    val excluded = (syncedHistory.map { it.videoId } + listens.map { it.song.videoId } + topTracks.map { it.videoId })
                        .filter(String::isNotBlank).toSet()
                    (recommendations.ifEmpty { YtMusicRepository.quickPicks(excluded).getOrThrow() })
                        .filter { it.videoId.isNotBlank() && it.videoId !in excluded }
                        .distinctBy(::songIdentity).take(mixTrackCount)
                }
                ScrobbleMixMode.RADIO -> {
                    val seed = selectedSeed?.takeIf(::isYouTubeCatalogTrack)
                        ?: throw IllegalStateException("Choose a YouTube Music track for Song radio")
                    YtMusicRepository.radio(seed.videoId).getOrThrow()
                        .filter { it.videoId.isNotBlank() && it.videoId != seed.videoId }
                        .distinctBy(::songIdentity).take(mixTrackCount)
                }
                ScrobbleMixMode.ARTIST_MIX -> {
                    val seed = selectedSeed?.takeIf(::isYouTubeCatalogTrack)
                        ?: throw IllegalStateException("Choose a track by the artist you want to explore")
                    val artistBrowseId = seed.artistId
                        ?: YtMusicRepository.findArtistPageId(seed.artist).getOrThrow()
                        ?: throw IllegalStateException("Couldn't find an exact artist match for ${seed.artist}")
                    YtMusicRepository.artistPage(artistBrowseId).getOrThrow().songs
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
                ScrobbleMixMode.ARTIST_MIX -> "More by ${selectedSeed?.artist.orEmpty()}"
                ScrobbleMixMode.NEVER_HEARD -> "New to you"
                ScrobbleMixMode.DISCOVER -> "Discover"
                ScrobbleMixMode.TAG -> "${tagQuery.trim().replaceFirstChar { it.uppercase() }} mix"
                ScrobbleMixMode.LIBRARY -> "Your library mix"
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
        // Do not leave picks from an older history/profile visible while this
        // request is refreshing. Generate is gated below until this request settles.
        recommendations = emptyList()
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
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
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
            val pageVisibilityScope = this
            LazyColumn(
                state = when (section) {
                    SCROBBLE_TAB_GENERATE -> generateListState
                    SCROBBLE_TAB_PLAYLISTS -> playlistsListState
                    else -> statsListState
                },
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 96.dp, bottom = bottomPadding + 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (section) {
                    SCROBBLE_TAB_STATS -> {
                        item("stats-heading") {
                            PageHeading("Your listening", "A personal replay of what you play in Mylo")
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
                            loadingStats -> item("stats-loading") {
                                LoadingLine("Building your listening report")
                            }
                            statsFailed -> item("stats-error") {
                                EmptyPanel(
                                    "Statistics unavailable",
                                    "Your listening totals could not be read. Reopen Scrobble to try again.",
                                )
                            }
                            summary == null || summary?.isEmpty == true -> item("stats-empty") {
                                EmptyPanel(
                                    "Your story starts here",
                                    "Play music in Mylo. Your listening time, favourite tracks and listening patterns will appear here.",
                                )
                            }
                            else -> {
                                item("stats-overview") {
                                    StatsOverviewCard(
                                        summary = summary!!,
                                        topTracks = topTracks,
                                        reduceMotion = reduceMotion,
                                    )
                                }
                                if (summary!!.hourOfDay.any { it > 0L }) {
                                    item("stats-hourly-activity") {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            SectionHeading("Your listening rhythm")
                                            HourlyActivityChart(summary!!.hourOfDay, reduceMotion)
                                        }
                                    }
                                }
                                if (summary!!.busiestDay != null || summary!!.peakHour != null) {
                                    item("stats-insights") {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            SectionHeading("A little about your habits")
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                summary!!.busiestDay?.let { day ->
                                                    StatsInsightTile(
                                                        title = "Biggest day",
                                                        value = formatStatsDay(day),
                                                        detail = "${summary!!.busiestDayMs / 60_000} min listened",
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                }
                                                summary!!.peakHour?.let { hour ->
                                                    StatsInsightTile(
                                                        title = "Peak hour",
                                                        value = "%02d:00".format(hour),
                                                        detail = "Your most active hour",
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                val rankedTracks = summary!!.songs
                                    .sortedWith(compareByDescending<RankedSong> { it.plays }.thenByDescending { it.ms })
                                    .take(12)
                                if (rankedTracks.isNotEmpty()) {
                                    item("stats-top-heading") {
                                        StatsSectionHeading("Most played", "${summary!!.songs.size} tracks")
                                    }
                                    itemsIndexed(
                                        rankedTracks,
                                        key = { _, ranked -> "stat-${songIdentity(ranked.song)}" },
                                    ) { index, ranked ->
                                        StatsRankedTrack(
                                            rank = index + 1,
                                            song = ranked.song,
                                            supporting = "${ranked.plays} ${if (ranked.plays == 1) "play" else "plays"} · ${formatListeningTime(ranked.ms)}",
                                            onClick = { onPlayPlaylist("Most played", listOf(ranked.song), 0) },
                                        )
                                    }
                                }
                                if (summary!!.artists.isNotEmpty()) {
                                    item("stats-artists-heading") {
                                        StatsSectionHeading("Top artists", "Your most-played")
                                    }
                                    items(
                                        summary!!.artists.take(5),
                                        key = { "artist-${it.title.lowercase()}" },
                                    ) { artist ->
                                        StatsEntityRow(
                                            title = artist.title,
                                            subtitle = "${artist.plays} plays · ${formatListeningTime(artist.ms)}",
                                            artworkUrl = artist.artworkUrl,
                                        )
                                    }
                                }
                                if (summary!!.albums.isNotEmpty()) {
                                    item("stats-albums-heading") {
                                        StatsSectionHeading("Top albums", "By listening time")
                                    }
                                    items(
                                        summary!!.albums.take(5),
                                        key = { "album-${it.title}-${it.subtitle.orEmpty()}" },
                                    ) { album ->
                                        StatsEntityRow(
                                            title = album.title,
                                            subtitle = listOfNotNull(album.subtitle, "${album.plays} plays").joinToString(" · "),
                                            artworkUrl = album.artworkUrl,
                                        )
                                    }
                                }
                                if (summary!!.genres.isNotEmpty()) {
                                    item("stats-genres-heading") {
                                        StatsSectionHeading("Top genres", "From your listening")
                                    }
                                    items(
                                        summary!!.genres.take(5),
                                        key = { "genre-${it.title.lowercase()}" },
                                    ) { genre ->
                                        InfoPanel(
                                            genre.title,
                                            "${genre.plays} plays · ${formatListeningTime(genre.ms)}",
                                        )
                                    }
                                }
                            }
                        }
                        item("stats-device-note") {
                            Text(
                                "Private by design · These insights are calculated from listening data stored on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    SCROBBLE_TAB_GENERATE -> {
                        item("generate-heading") {
                            PageHeading("The mix studio", "A soundtrack shaped around your listening. Choose a direction, then let Mylo do the rest.")
                        }
                        if (historyFailed) {
                            item("generate-history-warning") {
                                InfoPanel(
                                    "Device history unavailable",
                                    "Local listening data could not be read. Rediscover and Never heard are unavailable until you reopen Scrobble to retry.",
                                )
                            }
                        }
                        if (allTimeStatsFailed) {
                            item("generate-all-time-stats-warning") {
                                InfoPanel(
                                    "All-time stats unavailable",
                                    "Rediscover needs completed-play counts from this device. Reopen Scrobble to retry.",
                                )
                            }
                        }
                        if (recommendationsFailed) {
                            item("generate-recommendations-warning") {
                                InfoPanel(
                                    "Fresh picks unavailable",
                                    "Check your connection, then use Refresh picks below to try again.",
                                )
                            }
                        }
                        item("generate-modes") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Choose your sound", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.weight(1f))
                                    Text("${ScrobbleMixMode.entries.size} MODES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                                }
                                ScrobbleMixMode.entries.chunked(2).forEach { rowModes ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        rowModes.forEach { mode ->
                                            val selectedMode = mixMode == mode
                                            Column(
                                                modifier = Modifier.weight(1f)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(
                                                        if (selectedMode) MaterialTheme.colorScheme.surfaceVariant
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                                                    )
                                                    .border(
                                                        width = if (selectedMode) 1.25.dp else 1.dp,
                                                        color = if (selectedMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
                                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
                                                        shape = RoundedCornerShape(16.dp),
                                                    )
                                                    .semantics { selected = selectedMode }
                                                    .clickable(enabled = !generatingMix, role = Role.RadioButton) {
                                                        mixMode = mode
                                                        generatedMix = emptyList()
                                                        generationError = null
                                                        mixSaved = false
                                                    }
                                                    .padding(horizontal = 13.dp, vertical = 14.dp),
                                                verticalArrangement = Arrangement.spacedBy(9.dp),
                                            ) {
                                                Box(
                                                    Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                                                        .background(if (selectedMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        imageVector = when (mode) {
                                                            ScrobbleMixMode.FAVOURITES -> Icons.Rounded.History
                                                            ScrobbleMixMode.RECENT -> Icons.Rounded.Refresh
                                                            ScrobbleMixMode.TASTE_MIX -> Icons.Rounded.GraphicEq
                                                            ScrobbleMixMode.RADIO -> Icons.Rounded.PlayArrow
                                                            ScrobbleMixMode.ARTIST_MIX -> Icons.Rounded.Explore
                                                            ScrobbleMixMode.NEVER_HEARD -> Icons.Rounded.Search
                                                            ScrobbleMixMode.DISCOVER -> Icons.Rounded.Explore
                                                            ScrobbleMixMode.TAG -> Icons.Rounded.Search
                                                            ScrobbleMixMode.LIBRARY -> Icons.Rounded.GraphicEq
                                                            ScrobbleMixMode.REDISCOVER -> Icons.Rounded.History
                                                        },
                                                        contentDescription = null,
                                                        tint = if (selectedMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(19.dp),
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Text(
                                                        mode.title,
                                                        modifier = Modifier.weight(1f),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    if (selectedMode) {
                                                        Icon(
                                                            Icons.Rounded.CheckCircle,
                                                            contentDescription = "Selected",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(17.dp),
                                                        )
                                                    }
                                                }
                                                Text(
                                                    mode.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    minLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        if (rowModes.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        if (mixMode == ScrobbleMixMode.RADIO || mixMode == ScrobbleMixMode.ARTIST_MIX) {
                            item("generate-seed") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(if (mixMode == ScrobbleMixMode.RADIO) "Choose a starting track" else "Choose an artist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    val seeds = (recentTracks + topTracks).filter(::isYouTubeCatalogTrack).distinctBy(::songIdentity).take(12)
                                    if (seeds.isEmpty()) {
                                        if (mixMode == ScrobbleMixMode.RADIO) {
                                            EmptyPanel("No radio seed yet", "Play or sync a YouTube Music track first. Local-only files cannot start YouTube Music radio.")
                                        } else {
                                            EmptyPanel("No artist seed yet", "Play or sync a track by the artist you want to explore. Local-only files cannot look up an artist catalogue.")
                                        }
                                    } else {
                                        seeds.forEach { song ->
                                            val seedSelected = selectedSeed?.videoId == song.videoId
                                            Row(
                                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                                    .background(if (seedSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
                                                    .border(
                                                        width = if (seedSelected) 1.25.dp else 1.dp,
                                                        color = if (seedSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
                                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                                                        shape = RoundedCornerShape(14.dp),
                                                    )
                                                    .semantics { selected = seedSelected }
                                                    .clickable(enabled = !generatingMix, role = Role.RadioButton) {
                                                        if (selectedSeed?.videoId != song.videoId) {
                                                            selectedSeed = song
                                                            generatedMix = emptyList()
                                                            generationError = null
                                                            mixSaved = false
                                                        }
                                                    }.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                AsyncImage(model = song.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                                                Spacer(Modifier.width(10.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                if (seedSelected) Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (mixMode == ScrobbleMixMode.TAG) {
                            item("generate-tag-options") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Choose a genre or tag", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    OutlinedTextField(
                                        value = tagQuery,
                                        onValueChange = {
                                            tagQuery = it
                                            generatedMix = emptyList()
                                            generationError = null
                                            mixSaved = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = !generatingMix,
                                        label = { Text("Genre or tag") },
                                        placeholder = { Text("e.g. Punjabi, indie pop, synthwave") },
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        SCROBBLE_GENRE_TAGS.forEach { tag ->
                                            FilterChip(
                                                selected = tagQuery.equals(tag, ignoreCase = true),
                                                onClick = {
                                                    tagQuery = tag
                                                    generatedMix = emptyList()
                                                    generationError = null
                                                    mixSaved = false
                                                },
                                                enabled = !generatingMix,
                                                label = { Text(tag) },
                                            )
                                        }
                                    }
                                    Text(
                                        "Searches YouTube Music for real tracks matching this tag.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (mixMode == ScrobbleMixMode.LIBRARY) {
                            item("generate-library-info") {
                                InfoPanel(
                                    "Your YouTube Music library",
                                    "Builds from Liked Music and tracks saved to your library. You need a signed-in YouTube Music account; your local listening history is not changed.",
                                )
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
                                    (mixMode !in setOf(ScrobbleMixMode.TASTE_MIX, ScrobbleMixMode.DISCOVER) || !loadingRecommendations) &&
                                    (mixMode !in setOf(ScrobbleMixMode.FAVOURITES, ScrobbleMixMode.TASTE_MIX) || !loadingStats) &&
                                    (mixMode != ScrobbleMixMode.TAG || tagQuery.isNotBlank()) &&
                                    (mixMode != ScrobbleMixMode.REDISCOVER || !loadingAllTimeStats) &&
                                    (mixMode != ScrobbleMixMode.REDISCOVER || !allTimeStatsFailed) &&
                                    (mixMode !in setOf(ScrobbleMixMode.REDISCOVER, ScrobbleMixMode.NEVER_HEARD) || !historyFailed) &&
                                    (mixMode !in setOf(ScrobbleMixMode.RADIO, ScrobbleMixMode.ARTIST_MIX) || selectedSeed?.let(::isYouTubeCatalogTrack) == true),
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
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        ArtworkMosaic(generatedMix, Modifier.size(112.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                            Text("YOUR NEW MIX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                            Text(generatedTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text("${generatedMix.size} tracks · ${mixMode.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            item("generated-actions") {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = { onPlayPlaylist(generatedTitle, generatedMix, 0) }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Rounded.PlayArrow, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Play mix")
                                    }
                                    OutlinedButton(onClick = {
                                        if (ScrobblePlaylistStore.save(generatedTitle, mixMode.description, generatedMix)) {
                                            mixSaved = true
                                        } else {
                                            generationError = "You have 20 saved mixes. Remove one before saving another."
                                        }
                                    }, enabled = !mixSaved) { Text(if (mixSaved) "Saved" else "Save") }
                                }
                            }
                            item("generated-save-feedback") {
                                AnimatedContent(
                                    targetState = mixSaved,
                                    transitionSpec = {
                                        if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                                        else if (targetState) {
                                            (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(180))) togetherWith fadeOut(tween(100))
                                        } else {
                                            fadeIn(tween(100)) togetherWith (slideOutHorizontally(tween(140)) { -it / 5 } + fadeOut(tween(100)))
                                        }
                                    },
                                    label = "generated-save-feedback",
                                ) { saved ->
                                    if (saved) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            val sharedArtworkState = rememberSharedContentState(playlistSharedElementKey(generatedMix))
                                            ArtworkMosaic(
                                                generatedMix,
                                                Modifier.size(48.dp)
                                                    .then(if (reduceMotion) Modifier else Modifier.sharedElement(
                                                        sharedContentState = sharedArtworkState,
                                                        animatedVisibilityScope = pageVisibilityScope,
                                                    ))
                                                    .clip(RoundedCornerShape(12.dp)),
                                            )
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(savedPlaylistForGeneratedMix?.title ?: generatedTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("Saved to Playlists", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            TextButton(onClick = {
                                                playlistQuery = ""
                                                playlistSortNewest = true
                                                openedPlaylist = null
                                                onViewPlaylists()
                                            }) { Text("View playlists") }
                                        }
                                    } else {
                                        Spacer(Modifier.height(0.dp))
                                    }
                                }
                            }
                        }
                            itemsIndexed(generatedMix, key = { _, song -> "generated-${songIdentity(song)}" }) { index, song ->
                                TrackRow(song, onClick = { onPlayPlaylist(generatedTitle, generatedMix, index) })
                            }
                        item("generate-discovery-refresh") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { recommendationRequest++ }, enabled = !loadingRecommendations && !generatingMix, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Refresh, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (loadingRecommendations) "Refreshing…" else "Refresh picks")
                                }
                                OutlinedButton(onClick = onBrowseMoods, enabled = !generatingMix, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Explore, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Browse moods")
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
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        ArtworkMosaic(selected.songs, Modifier.fillMaxWidth().height(188.dp))
                                        Text(selected.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                        Text(selected.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${selected.songs.size} tracks · Saved on this device", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        Button(onClick = { onPlayPlaylist(selected.title, selected.songs, 0) }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Rounded.PlayArrow, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Play all · ${selected.songs.size} tracks")
                                        }
                                    }
                                }
                            }
                            items(selected.songs.size, key = { index -> "saved-${selected.title}-${songIdentity(selected.songs[index])}-$index" }) { index ->
                                val song = selected.songs[index]
                                TrackRow(song, onClick = { onPlayPlaylist(selected.title, selected.songs, index) })
                            }
                        } else {
                            val visiblePlaylists = savedPlaylists
                                .filter { playlist ->
                                    playlist.title.contains(playlistQuery, ignoreCase = true) ||
                                        playlist.reason.contains(playlistQuery, ignoreCase = true) ||
                                        playlist.songs.any { it.title.contains(playlistQuery, ignoreCase = true) || it.artist.contains(playlistQuery, ignoreCase = true) }
                                }
                                .let { matches -> if (playlistSortNewest) matches else matches.sortedBy { it.title.lowercase() } }
                            item("playlists-heading") {
                                PageHeading("Your collections", "${savedPlaylists.size} saved mixes · Made by you, kept on this device")
                            }
                            if (savedPlaylists.isNotEmpty()) {
                                item("playlists-search") {
                                    OutlinedTextField(
                                        value = playlistQuery,
                                        onValueChange = { playlistQuery = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text("Search mixes or tracks") },
                                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                        trailingIcon = {
                                            if (playlistQuery.isNotEmpty()) IconButton(onClick = { playlistQuery = "" }) {
                                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                }
                                item("playlists-sort") {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Rounded.Sort, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        FilterChip(selected = playlistSortNewest, onClick = { playlistSortNewest = true }, label = { Text("Recently saved") })
                                        FilterChip(selected = !playlistSortNewest, onClick = { playlistSortNewest = false }, label = { Text("Name") })
                                    }
                                }
                            }
                            if (savedPlaylists.isEmpty()) {
                                item("playlists-empty") {
                                    EmptyPanel("Your library is ready", "Create a mix in Generate and save it here. Your saved playlists stay separate from listening history.")
                                }
                            } else if (visiblePlaylists.isEmpty()) {
                                item("playlists-no-results") {
                                    EmptyPanel("No matches", "Try another playlist, artist or track name.")
                                }
                            } else {
                                items(visiblePlaylists, key = { "saved-${it.title}" }) { playlist ->
                                    var menuExpanded by remember(playlist.title) { mutableStateOf(false) }
                                    val sharedArtworkState = rememberSharedContentState(playlistSharedElementKey(playlist.songs))
                                    val playlistCardShape = RoundedCornerShape(24.dp)
                                    Card(
                                        modifier = Modifier.fillMaxWidth().border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                                            shape = playlistCardShape,
                                        ),
                                        shape = playlistCardShape,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                                        .clickable(role = Role.Button) { openedPlaylist = playlist.title },
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    ArtworkMosaic(
                                                        playlist.songs,
                                                        Modifier.size(96.dp)
                                                            .then(if (reduceMotion) Modifier else Modifier.sharedElement(
                                                                sharedContentState = sharedArtworkState,
                                                                animatedVisibilityScope = pageVisibilityScope,
                                                            ))
                                                            .clip(RoundedCornerShape(18.dp)),
                                                    )
                                                    Spacer(Modifier.width(13.dp))
                                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text(playlist.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text("${playlist.songs.size} tracks", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                                        Text(playlist.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                                Box {
                                                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More playlist actions") }
                                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                                        DropdownMenuItem(text = { Text("Open") }, onClick = { menuExpanded = false; openedPlaylist = playlist.title })
                                                        DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) }, onClick = { menuExpanded = false; pendingRemovePlaylist = playlist.title })
                                                    }
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(onClick = { onPlayPlaylist(playlist.title, playlist.songs, 0) }, modifier = Modifier.weight(1f)) {
                                                    Icon(Icons.Rounded.PlayArrow, null)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Play")
                                                }
                                                OutlinedButton(onClick = { openedPlaylist = playlist.title }, modifier = Modifier.weight(1f)) { Text("View tracks") }
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
}

@Composable
private fun HourlyActivityChart(hours: List<Long>, reduceMotion: Boolean) {
    val peak = hours.maxOrNull()?.takeIf { it > 0L } ?: return
    val dayParts = remember(hours) { buildListeningDayParts(hours) }
    val totalDayPartMs = remember(dayParts) { dayParts.sumOf { it.listeningMs } }
    if (totalDayPartMs <= 0L) return
    var barsVisible by remember(hours) { mutableStateOf(false) }
    LaunchedEffect(hours, reduceMotion) {
        barsVisible = false
        if (reduceMotion) {
            barsVisible = true
        } else {
            delay(40)
            barsVisible = true
        }
    }
    val dayPartBarProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (barsVisible) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "daypart-split-bar",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Listening by time of day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Share of recorded listening time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(Modifier.fillMaxWidth(dayPartBarProgress).fillMaxHeight()) {
                        dayParts.forEachIndexed { index, part ->
                            if (part.listeningMs > 0L) {
                                val fraction = part.listeningMs.toFloat() / totalDayPartMs.toFloat()
                                val alpha = when (index) {
                                    0 -> 1f
                                    1 -> 0.78f
                                    2 -> 0.56f
                                    else -> 0.34f
                                }
                                Box(
                                    Modifier.weight(fraction).fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayParts.forEach { part ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${part.percentageOf(totalDayPartMs)}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(part.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(part.timeRange, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f))
                        }
                    }
                }
            }
            Text("Hourly activity", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().height(76.dp).semantics {
                    contentDescription = "Hourly listening activity: " + hours.withIndex()
                        .filter { it.value > 0L }
                        .joinToString { (hour, value) -> "%02d:00 %s".format(hour, formatListeningTime(value)) }
                },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                hours.forEachIndexed { hour, value ->
                    val targetFraction = if (value <= 0L) 0f else (value.toFloat() / peak.toFloat()).coerceIn(0f, 1f)
                    val fraction by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (barsVisible) targetFraction else 0f,
                        animationSpec = if (reduceMotion) snap() else tween(
                            durationMillis = 520,
                            delayMillis = hour * 12,
                            easing = FastOutSlowInEasing,
                        ),
                        label = "hourly-bar-$hour",
                    )
                    val barHeight = if (value <= 0L) 3.dp else (3f + 63f * fraction).dp
                    Box(
                        modifier = Modifier.weight(1f).height(76.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier.width(6.dp).height(barHeight)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (value == peak) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("00", "06", "12", "18", "23").forEach { hour ->
                    Text(hour, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


@Composable
private fun StatsOverviewCard(
    summary: ReplaySummary,
    topTracks: List<Song>,
    reduceMotion: Boolean,
) {
    val shape = RoundedCornerShape(28.dp)
    val listeningTimeStyle = if (formatListeningTime(summary.totalMs).length > 7) {
        MaterialTheme.typography.headlineMedium
    } else {
        MaterialTheme.typography.displaySmall
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.26f),
                shape = shape,
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "LISTENING TIME",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.05.sp,
                    )
                }
                AnimatedValueText(
                    value = summary.totalMs / 60_000L,
                    reduceMotion = reduceMotion,
                    style = listeningTimeStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    formatter = { formatListeningTime(it * 60_000L) },
                )
                Text(
                    summary.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ArtworkMosaic(
                songs = topTracks.take(4),
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(20.dp),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatsMetricTile(
                value = summary.totalPlays.toLong(),
                label = "PLAYS",
                reduceMotion = reduceMotion,
                modifier = Modifier.weight(1f),
            )
            StatsMetricTile(
                value = summary.distinctSongs.toLong(),
                label = "TRACKS",
                reduceMotion = reduceMotion,
                modifier = Modifier.weight(1f),
            )
            StatsMetricTile(
                value = summary.distinctArtists.toLong(),
                label = "ARTISTS",
                reduceMotion = reduceMotion,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatsMetricTile(
    value: Long,
    label: String,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(17.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.46f))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AnimatedValueText(
            value = value,
            reduceMotion = reduceMotion,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.55.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatsInsightTile(
    title: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f))
            .padding(horizontal = 14.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatsSectionHeading(title: String, supporting: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            supporting,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsRankedTrack(
    rank: Int,
    song: Song,
    supporting: String,
    onClick: () -> Unit,
) {
    val isTopTrack = rank == 1
    val rowShape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(
                if (isTopTrack) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (isTopTrack) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                rank.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelSmall,
                color = if (isTopTrack) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
        ArtworkMosaic(
            songs = listOf(song),
            modifier = Modifier.size(50.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                supporting.ifBlank { song.artist },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isTopTrack) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = if (isTopTrack) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun StatsEntityRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (artworkUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String, eyebrow: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (eyebrow) {
            Text(
                "SCROBBLE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MetricCard(value: Long, label: String, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedValueText(
                value = value,
                reduceMotion = reduceMotion,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun InfoPanel(title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
        )
    }
}

@Composable
private fun EmptyPanel(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            }
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkMosaic(
            songs = listOf(song),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(supporting.ifBlank { song.artist }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun AnimatedValueText(
    value: Long,
    reduceMotion: Boolean,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
    formatter: (Long) -> String = { it.toString() },
) {
    val animatedValue = remember { Animatable(0f) }
    LaunchedEffect(value, reduceMotion) {
        val target = value.coerceAtLeast(0L).toFloat()
        if (reduceMotion) {
            animatedValue.snapTo(target)
        } else {
            animatedValue.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            )
        }
    }
    Text(
        text = formatter(animatedValue.value.roundToLong()),
        style = style,
        color = color,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun playlistSharedElementKey(songs: List<Song>): String =
    "scrobble-playlist-art-${songs.joinToString(separator = "|") { songIdentity(it) }}"

@Composable
private fun ArtworkMosaic(songs: List<Song>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    val covers = songs.take(4)
    when (covers.size) {
        0 -> Box(
            modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        1 -> ArtworkTile(covers.first(), modifier.clip(shape))
        2 -> Row(
            modifier.clip(shape),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            covers.forEach { song ->
                ArtworkTile(song, Modifier.weight(1f).fillMaxHeight())
            }
        }
        else -> Row(
            modifier.clip(shape),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOf(covers.take(2), covers.drop(2)).forEach { columnSongs ->
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    columnSongs.forEach { song ->
                        ArtworkTile(song, Modifier.weight(1f).fillMaxWidth())
                    }
                    if (columnSongs.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ArtworkTile(song: Song, modifier: Modifier = Modifier) {
    val artwork = song.thumbnailUrl?.takeIf(String::isNotBlank)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(22.dp),
        )
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun formatStatsDay(day: String): String = runCatching {
    java.time.LocalDate.parse(day).format(
        java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", java.util.Locale.getDefault()),
    )
}.getOrDefault(day)
private fun formatListeningTime(totalMs: Long): String {
    val totalMinutes = totalMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

