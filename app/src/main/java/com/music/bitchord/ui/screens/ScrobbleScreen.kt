package com.music.bitchord.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.ListeningHistory
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.RankedSong
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

private enum class ScrobbleMixMode(val title: String, val description: String) {
    FAVOURITES("Your favourites", "Most-played tracks from synced history and Mylo listens"),
    RECENT("Recently played", "Your latest unique listens"),
    TASTE_MIX("Taste mix", "A balanced blend of favourites, recent listens and fresh picks"),
    RADIO("Song radio", "Build a radio mix from a track you choose"),
    ARTIST_MIX("Artist mix", "Explore more tracks from an artist you already enjoy"),
    SIMILAR_ARTISTS("Similar artists", "Discover tracks from artists related to your chosen artist"),
    NEVER_HEARD("Never heard", "Find catalogue picks missing from your listening history"),
    DISCOVER("Discover", "Fresh YouTube Music picks outside your listening history"),
    TAG("By genre", "Build a mix from a genre or tag"),
    LIBRARY("Your library", "Tracks saved to your YouTube Music library"),
    REDISCOVER("Rediscover", "Tracks played 3+ times on this device and quiet for 60 days"),
}

private val SCROBBLE_GENRE_TAGS = listOf("pop", "rock", "hip-hop", "electronic", "jazz", "lofi", "metal", "indie", "classical", "r&b", "ambient", "punk")

private enum class ScrobblePlaylistSortMode { RECENT, OLDEST, NAME, TRACK_COUNT }

private enum class StatsView(val label: String, val period: ReplayPeriod) {
    RECENT("Recent", ReplayPeriod.LAST_30_DAYS),
    MOST_PLAYED("Most Played", ReplayPeriod.ALL_TIME),
    LAST_7_DAYS("Last 7 Days", ReplayPeriod.LAST_7_DAYS),
    LAST_30_DAYS("Last 30 Days", ReplayPeriod.LAST_30_DAYS),
    TOP_ARTISTS("Top Artists", ReplayPeriod.ALL_TIME),
    TOP_ALBUMS("Top Albums", ReplayPeriod.ALL_TIME),
}

private enum class ScrobbleStatsSource { DEVICE, YOUTUBE_MUSIC, LAST_FM }


private enum class ScrobbleTopPeriod(val label: String, val period: ReplayPeriod) {
    ALL_TIME("All time", ReplayPeriod.ALL_TIME),
    LAST_12_MONTHS("12 months", ReplayPeriod.LAST_12_MONTHS),
    LAST_6_MONTHS("6 months", ReplayPeriod.LAST_6_MONTHS),
    LAST_3_MONTHS("3 months", ReplayPeriod.LAST_3_MONTHS),
    LAST_30_DAYS("30 days", ReplayPeriod.LAST_30_DAYS),
    LAST_7_DAYS("7 days", ReplayPeriod.LAST_7_DAYS),
}

private const val SCROBBLE_GENERATION_TIMEOUT_MS = 60_000L

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
    workScope: kotlinx.coroutines.CoroutineScope,
    syncedHistory: List<Song> = emptyList(),
    syncingYouTubeHistory: Boolean = false,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val context = LocalContext.current
    val lastFmUsername by AppSettings.lastfmUsername.collectAsStateWithLifecycle()
    val lastFmApiKey by AppSettings.lastfmApiKey.collectAsStateWithLifecycle()
    val lastFmSecret by AppSettings.lastfmSecret.collectAsStateWithLifecycle()
    val lastFmSessionKey by AppSettings.lastfmSessionKey.collectAsStateWithLifecycle()
    val lastFmEndpoint by AppSettings.lastfmEndpoint.collectAsStateWithLifecycle()
    val historyRevision by ListeningHistory.revision.collectAsStateWithLifecycle()
    var statsView by remember { mutableStateOf(StatsView.RECENT) }
    var statsSource by remember { mutableStateOf(ScrobbleStatsSource.DEVICE) }
    var statsSourceManuallySelected by remember { mutableStateOf(false) }
    var syncedStats by remember { mutableStateOf(buildSyncedHistorySummary(emptyList())) }
    var loadingSyncedStats by remember { mutableStateOf(syncedHistory.isNotEmpty()) }
    var syncedStatsFailed by remember { mutableStateOf(false) }
    LaunchedEffect(syncedHistory) {
        loadingSyncedStats = true
        syncedStatsFailed = false
        try {
            syncedStats = withContext(Dispatchers.Default) {
                buildSyncedHistorySummary(syncedHistory)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            syncedStatsFailed = true
        } finally {
            loadingSyncedStats = false
        }
    }
    LaunchedEffect(syncedHistory.isNotEmpty(), syncingYouTubeHistory, statsSourceManuallySelected) {
        if (!statsSourceManuallySelected) {
            // Follow the available source automatically in both directions.
            // If a previous account sync is cleared or disabled, don't leave
            // Stats stranded on an empty YouTube Music source.
            statsSource = if (syncedHistory.isNotEmpty() || syncingYouTubeHistory) {
                ScrobbleStatsSource.YOUTUBE_MUSIC
            } else {
                ScrobbleStatsSource.DEVICE
            }
        }
    }
    var statsMenuExpanded by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<ReplaySummary?>(null) }
    var allTimeSummary by remember { mutableStateOf<ReplaySummary?>(null) }
    var loadingAllTimeStats by remember { mutableStateOf(true) }
    var allTimeStatsFailed by remember { mutableStateOf(false) }
    var loadingStats by remember { mutableStateOf(true) }
    var statsFailed by remember { mutableStateOf(false) }
    var lastFmProfile by remember { mutableStateOf<LastFM.UserStats?>(null) }
    var lastFmTopTracks by remember { mutableStateOf<List<LastFM.RankedTrack>>(emptyList()) }
    var lastFmTopArtists by remember { mutableStateOf<List<LastFM.RankedArtist>>(emptyList()) }
    var lastFmTopAlbums by remember { mutableStateOf<List<LastFM.RankedAlbum>>(emptyList()) }
    var loadingLastFmStats by remember { mutableStateOf(false) }
    var lastFmStatsFailed by remember { mutableStateOf(false) }
    var generationTopPeriod by remember { mutableStateOf(ScrobbleTopPeriod.ALL_TIME) }
    var generationTopTracks by remember { mutableStateOf<List<Song>>(emptyList()) }
    var generationTopRankedTracks by remember { mutableStateOf<List<RankedSong>>(emptyList()) }
    var loadingGenerationTopTracks by remember { mutableStateOf(true) }
    var generationTopTracksFailed by remember { mutableStateOf(false) }
    val favouriteTracks = remember(generationTopRankedTracks, syncedStats, generationTopPeriod) {
        buildCombinedFavouriteTracks(
            local = generationTopRankedTracks,
            // The account snapshot has no per-play timestamps, so it can only
            // participate in an honest all-time ranking.
            synced = if (generationTopPeriod == ScrobbleTopPeriod.ALL_TIME) syncedStats.topTracks else emptyList(),
            limit = 25,
        )
    }
    var listens by remember { mutableStateOf<List<ListeningHistory.Entry>>(emptyList()) }
    var loadingHistory by remember { mutableStateOf(true) }
    var historyFailed by remember { mutableStateOf(false) }
    var recommendations by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loadingRecommendations by remember { mutableStateOf(false) }
    var recommendationsFailed by remember { mutableStateOf(false) }
    var lastFmDiscoveryAvailable by remember { mutableStateOf(false) }
    var recommendationRequest by remember { mutableIntStateOf(0) }
    var mixMode by remember { mutableStateOf(ScrobbleMixMode.FAVOURITES) }
    var selectedSeed by remember { mutableStateOf<Song?>(null) }
    var seedSearchQuery by remember { mutableStateOf("") }
    var seedSearchResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var seedSearchLoading by remember { mutableStateOf(false) }
    var seedSearchError by remember { mutableStateOf<String?>(null) }
    var seedSearchRequest by remember { mutableIntStateOf(0) }
    var tagQuery by remember { mutableStateOf("") }
    var similarArtistQuery by remember { mutableStateOf("") }
    var mixTrackCount by remember { mutableIntStateOf(20) }
    var generationRequest by remember { mutableIntStateOf(0) }
    var generatingMix by remember { mutableStateOf(false) }
    var generationError by remember { mutableStateOf<String?>(null) }
    var generatedMix by remember { mutableStateOf<List<Song>>(emptyList()) }
    var generatedTitle by remember { mutableStateOf("") }
    var mixSaved by remember { mutableStateOf(false) }
    var pendingRemovePlaylist by remember { mutableStateOf<String?>(null) }
    var pendingRenamePlaylist by remember { mutableStateOf<String?>(null) }
    var renamePlaylistDraft by remember { mutableStateOf("") }
    var renamePlaylistError by remember { mutableStateOf<String?>(null) }
    var openedPlaylist by remember { mutableStateOf<String?>(null) }
    var playlistQuery by remember { mutableStateOf("") }
    var playlistSortMode by remember { mutableStateOf(ScrobblePlaylistSortMode.RECENT) }
    var playlistSelectionMode by remember { mutableStateOf(false) }
    var selectedPlaylistTitles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkRemoveConfirm by remember { mutableStateOf(false) }
    var createPlaylistDialog by remember { mutableStateOf(false) }
    var createPlaylistTitle by remember { mutableStateOf("") }
    var createPlaylistError by remember { mutableStateOf<String?>(null) }
    var addingTracksPlaylist by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }
    var coverEditorPlaylist by remember { mutableStateOf<String?>(null) }
    var coverPickerPlaylist by remember { mutableStateOf<String?>(null) }
    var generatingSimilarPlaylist by remember { mutableStateOf<String?>(null) }
    val exportScope = rememberCoroutineScope()
    fun releaseCoverPermission(uri: String?) {
        if (!uri.isNullOrBlank()) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    android.net.Uri.parse(uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val title = coverPickerPlaylist
        coverPickerPlaylist = null
        if (uri != null && title != null) {
            val permissionGranted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (!permissionGranted) {
                Toast.makeText(context, "Couldn't keep access to that image. Choose another one.", Toast.LENGTH_LONG).show()
            } else if (ScrobblePlaylistStore.playlists.any { it.title == title }) {
                val previous = ScrobblePlaylistStore.setCover(title, uri.toString())
                if (previous != uri.toString()) releaseCoverPermission(previous)
                Toast.makeText(context, "Playlist cover updated", Toast.LENGTH_SHORT).show()
            } else {
                releaseCoverPermission(uri.toString())
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val destination = result.data?.data
        val payload = pendingExport
        pendingExport = null
        if (result.resultCode == Activity.RESULT_OK && destination != null && payload != null) {
            exportScope.launch {
                val saved = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination)?.use { output ->
                            output.write(payload.second.toByteArray(Charsets.UTF_8))
                        } ?: error("Couldn't open the selected destination")
                    }
                }.isSuccess
                Toast.makeText(
                    context,
                    if (saved) "Saved ${payload.first}" else "Couldn't save ${payload.first}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val statsListState = rememberLazyListState()
    val generateListState = rememberLazyListState()
    val playlistsListState = rememberLazyListState()

    BackHandler(enabled = selectedSection == SCROBBLE_TAB_PLAYLISTS && openedPlaylist != null) {
        openedPlaylist = null
    }

    LaunchedEffect(context, lastFmApiKey, lastFmSecret, lastFmSessionKey, lastFmEndpoint) {
        ScrobblePlaylistStore.init(context)
        val apiKey = lastFmApiKey
        val secret = lastFmSecret
        if (apiKey.isNotBlank() && secret.isNotBlank()) {
            val sessionKey = lastFmSessionKey.takeIf(String::isNotBlank) ?: LastFM.sessionKey
            val endpoint = lastFmEndpoint.ifBlank { LastFM.DEFAULT_API_ENDPOINT }
            runCatching {
                LastFM.configure(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    secret = secret,
                    sessionKey = sessionKey,
                )
            }.onFailure {
                // A stale custom endpoint must not take down Scrobble; retain
                // the public Last.fm default for discovery if the saved URL is invalid.
                LastFM.configure(
                    endpoint = LastFM.DEFAULT_API_ENDPOINT,
                    apiKey = apiKey,
                    secret = secret,
                    sessionKey = sessionKey,
                )
            }
        }
        lastFmDiscoveryAvailable = apiKey.isNotBlank() && secret.isNotBlank() && LastFM.isInitialized()
    }

    LaunchedEffect(statsSource, lastFmUsername, lastFmDiscoveryAvailable) {
        if (statsSource != ScrobbleStatsSource.LAST_FM) return@LaunchedEffect
        if (!lastFmDiscoveryAvailable || lastFmUsername.isBlank()) {
            lastFmProfile = null
            lastFmTopTracks = emptyList()
            lastFmTopArtists = emptyList()
            lastFmTopAlbums = emptyList()
            lastFmStatsFailed = true
            loadingLastFmStats = false
            return@LaunchedEffect
        }
        loadingLastFmStats = true
        lastFmStatsFailed = false
        try {
            val username = lastFmUsername.trim()
            val (profile, charts) = coroutineScope {
                val profileCall = async { LastFM.getUserStats(username).getOrThrow() }
                val tracksCall = async { LastFM.getTopTracks(username, period = "overall", limit = 12).getOrThrow() }
                val artistsCall = async { LastFM.getTopArtists(username, period = "overall", limit = 12).getOrThrow() }
                val albumsCall = async { LastFM.getTopAlbums(username, period = "overall", limit = 12).getOrThrow() }
                profileCall.await() to Triple(tracksCall.await(), artistsCall.await(), albumsCall.await())
            }
            lastFmProfile = profile
            lastFmTopTracks = charts.first
            lastFmTopArtists = charts.second
            lastFmTopAlbums = charts.third
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            lastFmStatsFailed = true
        } finally {
            loadingLastFmStats = false
        }
    }

    LaunchedEffect(lastFmUsername, lastFmDiscoveryAvailable) {
        if (statsSource == ScrobbleStatsSource.LAST_FM &&
            (!lastFmDiscoveryAvailable || lastFmUsername.isBlank())
        ) statsSource = ScrobbleStatsSource.DEVICE
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

    LaunchedEffect(statsView, historyRevision) {
        loadingStats = true
        statsFailed = false
        try {
            summary = ListeningStats.summary(statsView.period)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            statsFailed = true
        } finally {
            loadingStats = false
        }
    }

    // Generator period is independent of the Statistics dropdown. Changing a
    // chart view must never silently change which tracks "Your favourites" uses.
    LaunchedEffect(generationTopPeriod, historyRevision) {
        loadingGenerationTopTracks = true
        generationTopTracksFailed = false
        try {
            val periodSummary = ListeningStats.summary(generationTopPeriod.period)
            generationTopRankedTracks = periodSummary.songs
                .sortedWith(compareByDescending<RankedSong> { it.plays }.thenByDescending { it.ms })
                .distinctBy { songIdentity(it.song) }
                .take(25)
            generationTopTracks = generationTopRankedTracks.map { it.song }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            generationTopTracks = emptyList()
            generationTopRankedTracks = emptyList()
            generationTopTracksFailed = true
        } finally {
            loadingGenerationTopTracks = false
        }
    }
    val recentTracks = sequence {
        yieldAll(syncedHistory)
        yieldAll(listens.sortedByDescending { it.startedAt }.map { it.song })
    }.filter { it.videoId.isNotBlank() || !it.localUri.isNullOrBlank() || !it.localPath.isNullOrBlank() }
        .distinctBy(::songIdentity)
        .take(25)
        .toList()
    val allTimePlayCounts = allTimeSummary?.songs.orEmpty().associate { songIdentity(it.song) to it.plays }
    val rediscoverTracks = buildRediscoverMix(listens, allTimePlayCounts, System.currentTimeMillis())
    val savedPlaylists = ScrobblePlaylistStore.playlists.toList()
    val addableHistoryTracks = remember(syncedHistory, listens, allTimeSummary) {
        sequence {
            yieldAll(syncedHistory)
            yieldAll(listens.sortedByDescending { it.startedAt }.map { it.song })
            yieldAll(allTimeSummary?.songs.orEmpty().map { it.song })
        }.filter { it.videoId.isNotBlank() || !it.localUri.isNullOrBlank() || !it.localPath.isNullOrBlank() }
            .distinctBy(::songIdentity)
            .take(40)
            .toList()
    }
    val savePlaylistExport: (SavedScrobblePlaylist, ScrobblePlaylistExportFormat) -> Unit = { playlist, format ->
        val filename = scrobblePlaylistExportFilename(playlist.title, format)
        pendingExport = filename to exportScrobblePlaylist(playlist, format)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = format.mimeType
            putExtra(Intent.EXTRA_TITLE, filename)
        }
        runCatching { exportLauncher.launch(intent) }.onFailure {
            pendingExport = null
            Toast.makeText(context, "Couldn't open the file picker", Toast.LENGTH_SHORT).show()
        }
    }
    val sharePlaylistExport: (SavedScrobblePlaylist, ScrobblePlaylistExportFormat) -> Unit = { playlist, format ->
        val filename = scrobblePlaylistExportFilename(playlist.title, format)
        val content = exportScrobblePlaylist(playlist, format)
        exportScope.launch {
            val shareUri = runCatching {
                withContext(Dispatchers.IO) {
                    val exportDirectory = File(context.cacheDir, "shared/scrobble-playlists/" + System.currentTimeMillis())
                    if (!exportDirectory.mkdirs() && !exportDirectory.isDirectory) {
                        error("Couldn't create the playlist export cache")
                    }
                    val exportFile = File(exportDirectory, filename)
                    exportFile.writeText(content, Charsets.UTF_8)
                    FileProvider.getUriForFile(context, context.packageName + ".fileprovider", exportFile)
                }
            }
            shareUri.onSuccess { uri ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = format.mimeType
                    putExtra(Intent.EXTRA_SUBJECT, filename)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, filename, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(Intent.createChooser(intent, "Share playlist")) }
                    .onFailure { Toast.makeText(context, "Couldn't share this playlist", Toast.LENGTH_SHORT).show() }
            }.onFailure {
                Toast.makeText(context, "Couldn't prepare this playlist for sharing", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val generateSimilarPlaylist: (SavedScrobblePlaylist) -> Unit = { playlist ->
        val seeds = playlist.songs.filter(::isYouTubeCatalogTrack).distinctBy(::songIdentity).take(3)
        if (generatingSimilarPlaylist != null) {
            Toast.makeText(context, "Another similar mix is already being built", Toast.LENGTH_SHORT).show()
        } else if (seeds.isEmpty()) {
            Toast.makeText(context, "This playlist has no playable YouTube Music seed tracks", Toast.LENGTH_SHORT).show()
        } else {
            workScope.launch {
                generatingSimilarPlaylist = playlist.title
                try {
                    val generated = withTimeout(SCROBBLE_GENERATION_TIMEOUT_MS) {
                        val excluded = playlist.songs.mapTo(mutableSetOf(), ::songIdentity)
                        val radioTracks = coroutineScope {
                            seeds.map { seed ->
                                async {
                                    try {
                                        YtMusicRepository.radio(seed.videoId).getOrThrow()
                                            .filter { isYouTubeCatalogTrack(it) && songIdentity(it) !in excluded }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        emptyList()
                                    }
                                }
                            }.awaitAll().flatten()
                        }.distinctBy(::songIdentity)
                        val lastFmArtists = if (lastFmDiscoveryAvailable) {
                            coroutineScope {
                                seeds.map { it.artist }.distinct().take(2).map { artist ->
                                    async {
                                        try {
                                            LastFM.getSimilarArtists(artist, limit = 4).getOrThrow()
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            emptyList()
                                        }
                                    }
                                }.awaitAll().flatten().distinct().take(6)
                            }
                        } else {
                            emptyList()
                        }
                        val lastFmTracks = coroutineScope {
                            lastFmArtists.map { artist ->
                                async {
                                    try {
                                        buildTagMix(
                                            YtMusicRepository.search("$artist songs", SearchFilter.SONGS).getOrThrow(),
                                            limit = 4,
                                        ).filter { songIdentity(it) !in excluded }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        emptyList()
                                    }
                                }
                            }.awaitAll().flatten()
                        }
                        buildYouTubeFirstMix(radioTracks, lastFmTracks, limit = 35)
                    }
                    when {
                        generated.isEmpty() -> Toast.makeText(context, "No similar tracks were found. Try another playlist.", Toast.LENGTH_LONG).show()
                        !ScrobblePlaylistStore.save("${playlist.title} Mix", "Inspired by ${playlist.title}", generated) ->
                            Toast.makeText(context, "You have 20 saved mixes. Remove one before saving another.", Toast.LENGTH_LONG).show()
                        else -> Toast.makeText(context, "Created a new mix inspired by ${playlist.title}", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: TimeoutCancellationException) {
                    Toast.makeText(context, "Similar mix generation timed out. Check your connection and try again.", Toast.LENGTH_LONG).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Toast.makeText(context, error.message ?: "Couldn't generate a similar mix", Toast.LENGTH_LONG).show()
                } finally {
                    generatingSimilarPlaylist = null
                }
            }
        }
    }
    val savedPlaylistForGeneratedMix = savedPlaylists.firstOrNull { playlist ->
        playlist.songs.map(::songIdentity) == generatedMix.map(::songIdentity)
    }

    LaunchedEffect(recentTracks, generationTopTracks) {
        if (selectedSeed == null) selectedSeed = (recentTracks + generationTopTracks).firstOrNull(::isYouTubeCatalogTrack)
    }

    LaunchedEffect(seedSearchRequest) {
        if (seedSearchRequest == 0 || seedSearchQuery.isBlank()) return@LaunchedEffect
        val query = seedSearchQuery.trim()
        seedSearchLoading = true
        seedSearchError = null
        seedSearchResults = emptyList()
        try {
            val results = withTimeout(SCROBBLE_GENERATION_TIMEOUT_MS) {
                YtMusicRepository.search(query, SearchFilter.SONGS).getOrThrow()
            }
            val tracks = buildTagMix(results, limit = 12)
            if (seedSearchQuery.trim() == query) {
                seedSearchResults = tracks
                if (tracks.isEmpty()) seedSearchError = "No playable tracks found. Try another search."
            }
        } catch (_: TimeoutCancellationException) {
            if (seedSearchQuery.trim() == query) seedSearchError = "Search timed out. Check your connection and try again."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (seedSearchQuery.trim() == query) seedSearchError = error.message ?: "Couldn't search YouTube Music."
        } finally {
            seedSearchLoading = false
        }
    }

    LaunchedEffect(generationRequest) {
        if (generationRequest == 0) return@LaunchedEffect
        generatingMix = true
        generationError = null
        mixSaved = false
        generatedMix = emptyList()
        try {
            val generated = withTimeout(SCROBBLE_GENERATION_TIMEOUT_MS) {
                when (mixMode) {
                    ScrobbleMixMode.FAVOURITES -> favouriteTracks.take(mixTrackCount)
                    ScrobbleMixMode.RECENT -> recentTracks.take(mixTrackCount)
                    ScrobbleMixMode.TASTE_MIX -> {
                        val excluded = (syncedHistory.map { it.videoId } + listens.map { it.song.videoId }).filter(String::isNotBlank).toSet()
                        val fresh = recommendations.ifEmpty {
                            try {
                                YtMusicRepository.quickPicks(excluded).getOrThrow()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                        buildTasteMix(
                            favourites = favouriteTracks.take(12),
                            recent = recentTracks.take(12),
                            fresh = fresh.filter { it.videoId.isNotBlank() && it.videoId !in excluded }.take(25),
                            limit = mixTrackCount,
                        )
                    }
                    ScrobbleMixMode.TAG -> {
                        val youtubeTracks = buildTagMix(
                            YtMusicRepository.search("${tagQuery.trim()} music", SearchFilter.SONGS).getOrThrow(),
                            limit = mixTrackCount * 2,
                        )
                        val lastFmTracks = if (lastFmDiscoveryAvailable) {
                            val tagTracks = try {
                                LastFM.getTagTopTracks(tagQuery, limit = minOf(8, mixTrackCount)).getOrThrow()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                emptyList()
                            }
                            coroutineScope {
                                tagTracks.map { candidate ->
                                    async {
                                        try {
                                            buildTagMix(
                                                YtMusicRepository.search(
                                                    "${candidate.name} ${candidate.artist}",
                                                    SearchFilter.SONGS,
                                                ).getOrThrow(),
                                                limit = 1,
                                            ).firstOrNull()
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                }.awaitAll().filterNotNull()
                            }
                        } else {
                            emptyList()
                        }
                        buildYouTubeFirstMix(youtubeTracks, lastFmTracks, mixTrackCount)
                    }
                    ScrobbleMixMode.LIBRARY -> buildLibraryMix(
                        YtMusicRepository.library().getOrThrow(),
                        mixTrackCount,
                    )
                    ScrobbleMixMode.REDISCOVER -> rediscoverTracks.take(mixTrackCount)
                    ScrobbleMixMode.NEVER_HEARD -> {
                        val heard = (syncedHistory.map { it.videoId } + listens.map { it.song.videoId } +
                            generationTopTracks.map { it.videoId } + recentTracks.map { it.videoId })
                            .filter(String::isNotBlank).toSet()
                        val heardSongs = (syncedHistory + listens.map { it.song } +
                            generationTopTracks + recentTracks)
                            .distinctBy(::recommendationIdentity)
                        buildNeverHeardMix(
                            candidates = YtMusicRepository.quickPicks(heard).getOrThrow(),
                            heardVideoIds = heard,
                            heardSongs = heardSongs,
                            limit = mixTrackCount,
                        )
                    }
                    // Recommendation refresh already ran LastWave's source scoring,
                    // strict heard exclusion and progressive diversity selection.
                    ScrobbleMixMode.DISCOVER -> distinctRecommendationSongs(recommendations).take(mixTrackCount)
                    ScrobbleMixMode.RADIO -> {
                        val seed = selectedSeed?.takeIf(::isYouTubeCatalogTrack)
                            ?: throw IllegalStateException("Choose a YouTube Music track for Song radio")
                        // Keep seed-specific radio as the primary source, but do
                        // not fail or return a tiny mix when YouTube's radio is
                        // temporarily empty. Mirror LastWave's layered approach:
                        // YouTube radio first, Last.fm similar tracks second, then
                        // personal favourites/recent listens and discovery.
                        val radioTracks = try {
                            YtMusicRepository.radio(seed.videoId).getOrThrow()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            emptyList()
                        }
                        val radioOnly = buildRadioMix(
                            radio = radioTracks,
                            similar = emptyList(),
                            favourites = emptyList(),
                            recent = emptyList(),
                            discovery = emptyList(),
                            seed = seed,
                            limit = mixTrackCount,
                        )
                        val similarTracks = if (lastFmDiscoveryAvailable && radioOnly.size < mixTrackCount) {
                            val similarCandidates = try {
                                LastFM.getSimilarTracks(
                                    track = seed.title,
                                    artist = seed.artist,
                                    limit = minOf(30, mixTrackCount * 2),
                                ).getOrThrow()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                emptyList()
                            }
                            coroutineScope {
                                similarCandidates.take(minOf(12, mixTrackCount)).map { candidate ->
                                    async {
                                        try {
                                            buildTagMix(
                                                YtMusicRepository.search(
                                                    "${candidate.name} ${candidate.artist}",
                                                    SearchFilter.SONGS,
                                                ).getOrThrow(),
                                                limit = 1,
                                            ).firstOrNull()
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                }.awaitAll().filterNotNull()
                            }
                        } else {
                            emptyList()
                        }
                        val initial = buildRadioMix(
                            radio = radioTracks,
                            similar = similarTracks,
                            favourites = favouriteTracks,
                            recent = recentTracks,
                            discovery = recommendations,
                            seed = seed,
                            limit = mixTrackCount,
                        )
                        if (initial.size >= mixTrackCount) {
                            initial
                        } else {
                            val quickPicks = try {
                                YtMusicRepository.quickPicks(setOf(seed.videoId)).getOrThrow()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                emptyList()
                            }
                            buildRadioMix(
                                radio = radioTracks,
                                similar = similarTracks,
                                favourites = favouriteTracks,
                                recent = recentTracks,
                                discovery = recommendations + quickPicks,
                                seed = seed,
                                limit = mixTrackCount,
                            )
                        }
                    }
                    ScrobbleMixMode.ARTIST_MIX -> {
                        val seed = selectedSeed?.takeIf(::isYouTubeCatalogTrack)
                            ?: throw IllegalStateException("Choose a track by the artist you want to explore")
                        val artistBrowseId = seed.artistId
                            ?: YtMusicRepository.findArtistPageId(seed.artist).getOrThrow()
                            ?: throw IllegalStateException("Couldn't find an exact artist match for ${seed.artist}")
                        buildArtistCappedMix(
                            candidates = YtMusicRepository.artistPage(artistBrowseId).getOrThrow().songs
                                .filter {
                                    it.videoId.isNotBlank() && it.videoId != seed.videoId &&
                                        !isSameSongOrEdit(it, seed)
                                },
                            seedArtist = seed.artist,
                            targetSize = mixTrackCount,
                        )
                    }
                    ScrobbleMixMode.SIMILAR_ARTISTS -> {
                        val artistName = similarArtistQuery.trim().ifBlank {
                            selectedSeed?.takeIf(::isYouTubeCatalogTrack)?.artist.orEmpty()
                        }.ifBlank { throw IllegalStateException("Enter an artist to find similar artists") }
                        // YouTube radio is the primary source, so this mode still
                        // works for users who have not configured Last.fm.
                        val youtubeRadio = try {
                            val seed = buildTagMix(
                                YtMusicRepository.search("$artistName songs", SearchFilter.SONGS).getOrThrow(),
                                limit = 3,
                            ).firstOrNull()
                            if (seed == null) {
                                emptyList()
                            } else {
                                YtMusicRepository.radio(seed.videoId).getOrThrow()
                                    .filter { it.videoId.isNotBlank() && it.videoId != seed.videoId }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            emptyList()
                        }
                        // Last.fm expands the artist graph when configured; every
                        // result is resolved through real YouTube Music search.
                        val lastFmTracks = if (lastFmDiscoveryAvailable) {
                            val similarArtists = try {
                                LastFM.getSimilarArtists(artistName, limit = 8).getOrThrow()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                emptyList()
                            }
                            coroutineScope {
                                // Bound the fan-out: four artists × five chart tracks keeps this fallback responsive.
                            similarArtists.take(4).map { artist ->
                                    async {
                                        try {
                                            // LastWave srcArtistTopTracks: use each related artist's
                                            // actual chart instead of a broad artist-name search.
                                            val chart = LastFM.getArtistTopTracks(artist, limit = 5).getOrThrow()
                                            coroutineScope {
                                                chart.map { candidate ->
                                                    async {
                                                        try {
                                                            buildTagMix(
                                                                YtMusicRepository.search(
                                                                    "${candidate.name} ${candidate.artist}",
                                                                    SearchFilter.SONGS,
                                                                ).getOrThrow(),
                                                                limit = 1,
                                                            ).firstOrNull()
                                                        } catch (cancelled: CancellationException) {
                                                            throw cancelled
                                                        } catch (_: Exception) {
                                                            null
                                                        }
                                                    }
                                                }.awaitAll().filterNotNull()
                                            }.filterNot { it.artist.equals(artistName, ignoreCase = true) }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            emptyList()
                                        }
                                    }
                                }.awaitAll().flatten()
                            }
                        } else {
                            emptyList()
                        }
                        buildSimilarArtistMix(youtubeRadio, lastFmTracks, artistName, mixTrackCount)
                    }
                }
            }
            if (generated.isEmpty()) throw IllegalStateException("No tracks were available for this mix. Try another mode or refresh your listening data.")
            generatedMix = generated
            // LastWave uses a global unique-name bank for generated playlists;
            // keep the generation mode in the descriptive subtitle instead.
            generatedTitle = ScrobblePlaylistNamer.generateUniqueName(
                ScrobblePlaylistStore.playlists.map { it.title },
            )
        } catch (_: TimeoutCancellationException) {
            generationError = "Playlist generation timed out. Check your connection and try again."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            generationError = error.message ?: "Couldn't generate this mix. Try again."
        } finally {
            generatingMix = false
        }
    }

    LaunchedEffect(
        selectedSection,
        syncedHistory,
        listens,
        loadingHistory,
        generationTopTracks,
        recommendationRequest,
        mixMode,
        lastFmDiscoveryAvailable,
        lastFmUsername,
        favouriteTracks,
        recentTracks,
    ) {
        if (selectedSection != SCROBBLE_TAB_GENERATE || loadingHistory ||
            mixMode !in setOf(ScrobbleMixMode.TASTE_MIX, ScrobbleMixMode.DISCOVER)
        ) return@LaunchedEffect
        // Do not leave picks from an older history/profile visible while this
        // request is refreshing. Generate is gated below until this request settles.
        recommendations = emptyList()
        loadingRecommendations = true
        recommendationsFailed = false
        try {
            val excluded = (syncedHistory.map { it.videoId } + listens.map { it.song.videoId } + generationTopTracks.map { it.videoId })
                .filter(String::isNotBlank).toSet()
            val result = withTimeout(SCROBBLE_GENERATION_TIMEOUT_MS) {
                val youtubePicks = YtMusicRepository.quickPicks(excluded).getOrThrow()
                if (lastFmDiscoveryAvailable) {
                    ScrobbleRecommendationEngine.discover(
                        seedTracks = recentTracks.take(3) + favouriteTracks.take(5),
                        seedArtists = favouriteTracks.map {
                            ListeningStats.primaryArtist(it.artist) ?: it.artist
                        },
                        lastFmUsername = lastFmUsername,
                        artistAffinity = run {
                            val counts = generationTopRankedTracks.groupBy {
                                ListeningStats.primaryArtist(it.song.artist)?.lowercase() ?: it.song.artist.lowercase()
                            }.mapValues { (_, rows) -> rows.sumOf { it.plays }.toDouble() }
                            val max = counts.values.maxOrNull() ?: 0.0
                            if (max <= 0.0) emptyMap() else counts.mapValues { (_, value) -> value / max }
                        },
                        youtubeCandidates = youtubePicks,
                        heardVideoIds = excluded,
                        heardSongs = syncedHistory + listens.map { it.song } + generationTopTracks,
                        limit = maxOf(20, mixTrackCount),
                    )
                } else {
                    buildDiscoverMix(
                        candidates = youtubePicks,
                        heardVideoIds = excluded,
                        heardSongs = syncedHistory + listens.map { it.song } + generationTopTracks,
                        limit = maxOf(20, mixTrackCount),
                    )
                }
            }
            recommendations = result
                .filter { it.videoId.isNotBlank() && it.videoId !in excluded }
                .distinctBy(::songIdentity)
            recommendationsFailed = false
        } catch (_: TimeoutCancellationException) {
            recommendationsFailed = true
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
        pendingRenamePlaylist?.let { oldTitle ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingRenamePlaylist = null; renamePlaylistError = null },
                title = { Text("Rename playlist") },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = renamePlaylistDraft,
                        onValueChange = { renamePlaylistDraft = it; renamePlaylistError = null },
                        singleLine = true,
                        label = { Text("Playlist name") },
                        isError = renamePlaylistError != null,
                        supportingText = { renamePlaylistError?.let { Text(it) } },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newTitle = renamePlaylistDraft.trim()
                        if (ScrobblePlaylistStore.rename(oldTitle, newTitle)) {
                            if (openedPlaylist == oldTitle) openedPlaylist = newTitle
                            pendingRenamePlaylist = null
                            renamePlaylistError = null
                        } else {
                            renamePlaylistError = when {
                                newTitle.isBlank() -> "Enter a playlist name"
                                ScrobblePlaylistStore.playlists.none { it.title == oldTitle } -> "This playlist is no longer available"
                                else -> "A playlist with that name already exists"
                            }
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { pendingRenamePlaylist = null; renamePlaylistError = null }) { Text("Cancel") } },
            )
        }
        if (createPlaylistDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    createPlaylistDialog = false
                    createPlaylistError = null
                },
                title = { Text("Create playlist") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = createPlaylistTitle,
                            onValueChange = {
                                createPlaylistTitle = it
                                createPlaylistError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Playlist name") },
                            placeholder = { Text("e.g. Late night drive") },
                            shape = RoundedCornerShape(14.dp),
                        )
                        createPlaylistError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val title = createPlaylistTitle.trim()
                        if (ScrobblePlaylistStore.create(title)) {
                            createPlaylistDialog = false
                            createPlaylistTitle = ""
                            createPlaylistError = null
                            playlistQuery = ""
                            playlistSortMode = ScrobblePlaylistSortMode.RECENT
                            openedPlaylist = title
                        } else {
                            createPlaylistError = when {
                                title.isBlank() -> "Enter a playlist name."
                                savedPlaylists.size >= 20 -> "You can save up to 20 playlists on this device."
                                else -> "A playlist with that name already exists."
                            }
                        }
                    }) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        createPlaylistDialog = false
                        createPlaylistError = null
                    }) { Text("Cancel") }
                },
            )
        }
        if (showBulkRemoveConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showBulkRemoveConfirm = false },
                title = { Text("Remove selected playlists?") },
                text = {
                    Text("${selectedPlaylistTitles.size} playlists will be removed from this device. Listening history and playback data will remain untouched.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        selectedPlaylistTitles.forEach { title ->
                            releaseCoverPermission(ScrobblePlaylistStore.remove(title))
                            if (openedPlaylist == title) openedPlaylist = null
                            if (coverEditorPlaylist == title) coverEditorPlaylist = null
                        }
                        selectedPlaylistTitles = emptySet()
                        playlistSelectionMode = false
                        showBulkRemoveConfirm = false
                    }) { Text("Remove") }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkRemoveConfirm = false }) { Text("Cancel") }
                },
            )
        }
        pendingRemovePlaylist?.let { title ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingRemovePlaylist = null },
                title = { Text("Remove saved mix?") },
                text = { Text("‘$title’ will be removed from this device. Your listening history will stay untouched.") },
                confirmButton = {
                    TextButton(onClick = {
                        releaseCoverPermission(ScrobblePlaylistStore.remove(title))
                        if (openedPlaylist == title) openedPlaylist = null
                        if (coverEditorPlaylist == title) coverEditorPlaylist = null
                        pendingRemovePlaylist = null
                    }) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { pendingRemovePlaylist = null }) { Text("Cancel") } },
            )
        }
        coverEditorPlaylist?.let { title ->
            savedPlaylists.firstOrNull { it.title == title }?.let { playlist ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { coverEditorPlaylist = null },
                    title = { Text("Playlist cover") },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PlaylistArtwork(playlist.songs, playlist.coverUri, Modifier.size(144.dp))
                            Text(
                                if (playlist.coverUri.isNullOrBlank()) "Using the playlist's automatic artwork."
                                else "Your selected image is saved with this playlist on this device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                coverPickerPlaylist = title
                                coverEditorPlaylist = null
                                runCatching { coverPicker.launch(arrayOf("image/*")) }.onFailure {
                                    coverPickerPlaylist = null
                                    Toast.makeText(context, "Couldn't open the image picker", Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) { Text(if (playlist.coverUri.isNullOrBlank()) "Choose image" else "Change image") }
                    },
                    dismissButton = {
                        Row {
                            if (!playlist.coverUri.isNullOrBlank()) {
                                TextButton(
                                    onClick = {
                                        releaseCoverPermission(ScrobblePlaylistStore.setCover(title, null))
                                        coverEditorPlaylist = null
                                    },
                                ) { Text("Use automatic") }
                            }
                            TextButton(onClick = { coverEditorPlaylist = null }) { Text("Done") }
                        }
                    },
                )
            }
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
                        item("stats-view-selector") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilterChip(
                                        selected = statsSource == ScrobbleStatsSource.DEVICE,
                                        onClick = {
                                            statsSourceManuallySelected = true
                                            statsSource = ScrobbleStatsSource.DEVICE
                                        },
                                        label = { Text("Mylo") },
                                    )
                                    FilterChip(
                                        selected = statsSource == ScrobbleStatsSource.YOUTUBE_MUSIC,
                                        onClick = {
                                            statsSourceManuallySelected = true
                                            statsSource = ScrobbleStatsSource.YOUTUBE_MUSIC
                                        },
                                        label = { Text("YouTube Music") },
                                    )
                                    if (lastFmDiscoveryAvailable && lastFmUsername.isNotBlank()) {
                                        FilterChip(
                                            selected = statsSource == ScrobbleStatsSource.LAST_FM,
                                            onClick = {
                                                statsSourceManuallySelected = true
                                                statsSource = ScrobbleStatsSource.LAST_FM
                                            },
                                            label = { Text("Last.fm") },
                                        )
                                    }
                                }
                                if (statsSource == ScrobbleStatsSource.DEVICE) {
                                    Box {
                                        OutlinedButton(
                                            onClick = { statsMenuExpanded = true },
                                            shape = RoundedCornerShape(14.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        ) {
                                            Text(statsView.label)
                                            Spacer(Modifier.width(8.dp))
                                            Icon(Icons.Rounded.Sort, contentDescription = null)
                                        }
                                        DropdownMenu(
                                            expanded = statsMenuExpanded,
                                            onDismissRequest = { statsMenuExpanded = false },
                                        ) {
                                            StatsView.entries.forEach { option ->
                                                DropdownMenuItem(
                                                    text = { Text(option.label) },
                                                    trailingIcon = {
                                                        if (statsView == option) {
                                                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected")
                                                        }
                                                    },
                                                    onClick = {
                                                        statsView = option
                                                        statsMenuExpanded = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        summary?.label ?: statsView.period.chip,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.End,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else if (statsSource == ScrobbleStatsSource.YOUTUBE_MUSIC) {
                                    Text(
                                        if (syncingYouTubeHistory) "UPDATING HISTORY" else "ALL SYNCED HISTORY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.65.sp,
                                    )
                                    Text(
                                        "${syncedStats.totalPlays} plays · ${syncedStats.distinctTracks} tracks",
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.End,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Text(
                                        "LAST.FM ACCOUNT · @" + lastFmUsername.trim(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.45.sp,
                                    )
                                    Text(
                                        "ALL-TIME SCROBBLE CHARTS",
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.End,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        when {
                            statsSource == ScrobbleStatsSource.LAST_FM && loadingLastFmStats -> {
                                item("stats-lastfm-loading") { LoadingLine("Loading your Last.fm profile and charts") }
                            }
                            statsSource == ScrobbleStatsSource.LAST_FM && lastFmStatsFailed -> {
                                item("stats-lastfm-error") {
                                    EmptyPanel("Last.fm statistics unavailable", "Check the connected username and API settings, then reopen Statistics to retry.")
                                }
                            }
                            statsSource == ScrobbleStatsSource.LAST_FM && lastFmProfile != null -> {
                                item("stats-lastfm-overview") {
                                    InfoPanel(
                                        "Last.fm profile",
                                        lastFmProfile!!.playCount.toString() + " scrobbles · " +
                                            lastFmProfile!!.trackCount + " tracks · " +
                                            lastFmProfile!!.artistCount + " artists · " +
                                            lastFmProfile!!.albumCount + " albums",
                                    )
                                }
                                if (lastFmTopTracks.isNotEmpty()) {
                                    item("stats-lastfm-tracks-heading") { StatsSectionHeading("Top tracks", "All-time Last.fm chart") }
                                    items(lastFmTopTracks, key = { "lastfm-track-" + it.artist.lowercase() + "-" + it.name.lowercase() }) { track ->
                                        StatsEntityRow(track.name, track.artist + " · " + track.playCount + " plays", track.artworkUrl)
                                    }
                                }
                                if (lastFmTopArtists.isNotEmpty()) {
                                    item("stats-lastfm-artists-heading") { StatsSectionHeading("Top artists", "All-time Last.fm chart") }
                                    items(lastFmTopArtists, key = { "lastfm-artist-" + it.name.lowercase() }) { artist ->
                                        StatsEntityRow(artist.name, artist.playCount.toString() + " plays", artist.artworkUrl)
                                    }
                                }
                                if (lastFmTopAlbums.isNotEmpty()) {
                                    item("stats-lastfm-albums-heading") { StatsSectionHeading("Top albums", "All-time Last.fm chart") }
                                    items(lastFmTopAlbums, key = { "lastfm-album-" + it.artist.lowercase() + "-" + it.name.lowercase() }) { album ->
                                        StatsEntityRow(album.name, album.artist + " · " + album.playCount + " plays", album.artworkUrl)
                                    }
                                }
                            }
                            statsSource == ScrobbleStatsSource.LAST_FM && !loadingLastFmStats -> {
                                item("stats-lastfm-empty") { EmptyPanel("No Last.fm scrobbles yet", "This account has no listening charts to show.") }
                            }
                            statsSource == ScrobbleStatsSource.YOUTUBE_MUSIC && syncedStats.totalPlays == 0 &&
                                (syncingYouTubeHistory || loadingSyncedStats) -> {
                                item("stats-youtube-syncing") {
                                    LoadingLine(if (syncingYouTubeHistory) "Syncing YouTube Music listening history" else "Preparing synced listening statistics")
                                }
                            }
                            statsSource == ScrobbleStatsSource.YOUTUBE_MUSIC && syncedStats.totalPlays == 0 && syncedStatsFailed -> {
                                item("stats-youtube-error") {
                                    EmptyPanel(
                                        "Couldn't prepare synced statistics",
                                        "Your account history is still saved. Reopen Statistics to try processing it again.",
                                    )
                                }
                            }
                            statsSource == ScrobbleStatsSource.YOUTUBE_MUSIC && syncedStats.totalPlays == 0 -> {
                                item("stats-youtube-empty") {
                                    EmptyPanel(
                                        "No synced listening history",
                                        "Run account sync with Listening history enabled. YouTube Music history is separate from plays recorded by Mylo.",
                                    )
                                }
                            }
                            statsSource == ScrobbleStatsSource.YOUTUBE_MUSIC -> {
                                item("stats-youtube-overview") {
                                    SyncedHistoryOverviewCard(
                                        summary = syncedStats,
                                        reduceMotion = reduceMotion,
                                    )
                                }
                                item("stats-youtube-top-tracks-heading") {
                                    StatsSectionHeading("Most played", "${syncedStats.distinctTracks} tracks")
                                }
                                itemsIndexed(
                                    syncedStats.topTracks.take(12),
                                    key = { _, track -> "yt-history-${songIdentity(track.song)}" },
                                ) { index, track ->
                                    StatsRankedTrack(
                                        rank = index + 1,
                                        song = track.song,
                                        supporting = "${track.plays} ${if (track.plays == 1) "play" else "plays"} · YouTube Music",
                                        onClick = {
                                            val songs = syncedStats.topTracks.map { it.song }
                                            onPlayPlaylist("YouTube Music history", songs, index)
                                        },
                                    )
                                }
                                if (syncedStats.topArtists.isNotEmpty()) {
                                    item("stats-youtube-top-artists-heading") {
                                        StatsSectionHeading("Top artists", syncedStats.distinctArtists.toString() + " artists")
                                    }
                                    items(
                                        syncedStats.topArtists.take(12),
                                        key = { "yt-artist-" + it.name.lowercase() },
                                    ) { artist ->
                                        StatsEntityRow(
                                            title = artist.name,
                                            subtitle = artist.plays.toString() + " plays",
                                            artworkUrl = artist.artworkUrl,
                                        )
                                    }
                                }
                                if (syncedStats.topAlbums.isNotEmpty()) {
                                    item("stats-youtube-top-albums-heading") {
                                        StatsSectionHeading("Top albums", "${syncedStats.distinctAlbums} albums")
                                    }
                                    items(
                                        syncedStats.topAlbums.take(12),
                                        key = { "yt-album-${it.artist.lowercase()}-${it.name.lowercase()}" },
                                    ) { album ->
                                        StatsEntityRow(
                                            title = album.name,
                                            subtitle = "${album.artist} · ${album.plays} ${if (album.plays == 1) "play" else "plays"}",
                                            artworkUrl = album.artworkUrl,
                                        )
                                    }
                                }
                            }
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
                                        reduceMotion = reduceMotion,
                                    )
                                }
                                when (statsView) {
                                    StatsView.RECENT -> {
                                        val recentCutoff = java.time.LocalDate.now().minusDays(29)
                                            .atStartOfDay(java.time.ZoneId.systemDefault())
                                            .toInstant().toEpochMilli()
                                        val recentListens = listens.asSequence()
                                            .filter { it.startedAt >= recentCutoff }
                                            .sortedByDescending { it.startedAt }
                                            .take(12)
                                            .toList()
                                        if (recentListens.isEmpty()) {
                                            item("stats-recent-empty") {
                                                EmptyPanel("No recent plays", "Your latest device listens will appear here.")
                                            }
                                        } else {
                                            item("stats-recent-heading") {
                                                StatsSectionHeading("Recently played", "Latest listens on this device")
                                            }
                                            val recentSongs = recentListens.map { it.song }
                                            itemsIndexed(
                                                recentListens,
                                                key = { index, entry -> "recent-$index-${entry.startedAt}-${songIdentity(entry.song)}" },
                                            ) { index, entry ->
                                                StatsRankedTrack(
                                                    rank = index + 1,
                                                    song = entry.song,
                                                    supporting = formatStatsTimestamp(entry.startedAt),
                                                    onClick = { onPlayPlaylist("Recently played", recentSongs, index) },
                                                )
                                            }
                                        }
                                    }
                                    StatsView.MOST_PLAYED, StatsView.LAST_7_DAYS, StatsView.LAST_30_DAYS -> {
                                        val rankedTracks = summary!!.songs
                                            .sortedWith(compareByDescending<RankedSong> { it.plays }.thenByDescending { it.ms })
                                            .take(12)
                                        if (rankedTracks.isNotEmpty()) {
                                            item("stats-top-heading") {
                                                StatsSectionHeading(
                                                    "Most played",
                                                    "${summary!!.songs.size} tracks",
                                                )
                                            }
                                            itemsIndexed(
                                                rankedTracks,
                                                key = { _, ranked -> "stat-${songIdentity(ranked.song)}" },
                                            ) { index, ranked ->
                                                StatsRankedTrack(
                                                    rank = index + 1,
                                                    song = ranked.song,
                                                    supporting = "${ranked.plays} ${if (ranked.plays == 1) "play" else "plays"} · ${formatListeningTime(ranked.ms)}",
                                                    onClick = { onPlayPlaylist("Most played", rankedTracks.map { it.song }, index) },
                                                )
                                            }
                                        }
                                        if (statsView == StatsView.MOST_PLAYED && summary!!.genres.isNotEmpty()) {
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
                                    StatsView.TOP_ARTISTS -> {
                                        item("stats-artists-heading") {
                                            StatsSectionHeading("Top artists", "Your most-played")
                                        }
                                        items(
                                            summary!!.artists.take(12),
                                            key = { "artist-${it.title.lowercase()}" },
                                        ) { artist ->
                                            StatsEntityRow(
                                                title = artist.title,
                                                subtitle = "${artist.plays} plays · ${formatListeningTime(artist.ms)}",
                                                artworkUrl = artist.artworkUrl,
                                            )
                                        }
                                    }
                                    StatsView.TOP_ALBUMS -> {
                                        item("stats-albums-heading") {
                                            StatsSectionHeading("Top albums", "By listening time")
                                        }
                                        items(
                                            summary!!.albums.take(12),
                                            key = { "album-${it.title}-${it.subtitle.orEmpty()}" },
                                        ) { album ->
                                            StatsEntityRow(
                                                title = album.title,
                                                subtitle = listOfNotNull(album.subtitle, "${album.plays} plays").joinToString(" · "),
                                                artworkUrl = album.artworkUrl,
                                            )
                                        }
                                    }
                                }
                            }
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
                        if (mixMode == ScrobbleMixMode.SIMILAR_ARTISTS && !lastFmDiscoveryAvailable) {
                            item("generate-similar-artists-warning") {
                                InfoPanel(
                                    "YouTube Music discovery",
                                    "Last.fm enrichment is unavailable, so this mix will use YouTube Music radio only.",
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
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("Choose your sound", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "${ScrobbleMixMode.entries.size} MODES",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 1.sp,
                                    )
                                }
                                ScrobbleMixMode.entries.forEach { mode ->
                                    val selectedMode = mixMode == mode
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(
                                                if (selectedMode) MaterialTheme.colorScheme.surfaceVariant
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                                            )
                                            .border(
                                                width = if (selectedMode) 1.25.dp else 1.dp,
                                                color = if (selectedMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.26f),
                                                shape = RoundedCornerShape(18.dp),
                                            )
                                            .semantics { selected = selectedMode }
                                            .clickable(enabled = !generatingMix, role = Role.RadioButton) {
                                                if (mixMode != mode) {
                                                    mixMode = mode
                                                    generatedMix = emptyList()
                                                    generationError = null
                                                    mixSaved = false
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (selectedMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                    else MaterialTheme.colorScheme.surfaceVariant,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = when (mode) {
                                                    ScrobbleMixMode.FAVOURITES -> Icons.Rounded.History
                                                    ScrobbleMixMode.RECENT -> Icons.Rounded.Refresh
                                                    ScrobbleMixMode.TASTE_MIX -> Icons.Rounded.GraphicEq
                                                    ScrobbleMixMode.RADIO -> Icons.Rounded.PlayArrow
                                                    ScrobbleMixMode.ARTIST_MIX -> Icons.Rounded.Explore
                                                    ScrobbleMixMode.SIMILAR_ARTISTS -> Icons.Rounded.Explore
                                                    ScrobbleMixMode.NEVER_HEARD -> Icons.Rounded.Search
                                                    ScrobbleMixMode.DISCOVER -> Icons.Rounded.Explore
                                                    ScrobbleMixMode.TAG -> Icons.Rounded.Search
                                                    ScrobbleMixMode.LIBRARY -> Icons.Rounded.GraphicEq
                                                    ScrobbleMixMode.REDISCOVER -> Icons.Rounded.History
                                                },
                                                contentDescription = null,
                                                tint = if (selectedMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                mode.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                mode.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (selectedMode) {
                                            Icon(
                                                Icons.Rounded.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (mixMode == ScrobbleMixMode.RADIO || mixMode == ScrobbleMixMode.ARTIST_MIX) {
                            item("generate-seed") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(if (mixMode == ScrobbleMixMode.RADIO) "Choose a starting track" else "Choose an artist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    val seeds = (recentTracks + generationTopTracks).filter(::isYouTubeCatalogTrack).distinctBy(::songIdentity).take(12)
                                    selectedSeed?.takeIf(::isYouTubeCatalogTrack)?.let { seed ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text("Selected seed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                Text("${seed.title} · ${seed.artist}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            TextButton(onClick = {
                                                selectedSeed = null
                                                generatedMix = emptyList()
                                                generationError = null
                                            }) { Text("Clear") }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = seedSearchQuery,
                                        onValueChange = {
                                            seedSearchQuery = it
                                            seedSearchResults = emptyList()
                                            seedSearchError = null
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = !generatingMix && !seedSearchLoading,
                                        label = { Text(if (mixMode == ScrobbleMixMode.RADIO) "Search tracks" else "Search an artist's tracks") },
                                        placeholder = { Text("Track or artist name") },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { seedSearchRequest++ },
                                                enabled = seedSearchQuery.isNotBlank() && !generatingMix && !seedSearchLoading,
                                            ) { Icon(Icons.Rounded.Search, contentDescription = "Search YouTube Music") }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    if (seedSearchLoading) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Text("Searching YouTube Music…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    seedSearchError?.let { message ->
                                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                    seedSearchResults.filterNot { result -> seeds.any { songIdentity(it) == songIdentity(result) } }.forEach { song ->
                                        val seedSelected = selectedSeed?.videoId == song.videoId
                                        Row(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                                .background(if (seedSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
                                                .border(1.dp, if (seedSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.62f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
                                                .semantics { selected = seedSelected }
                                                .clickable(enabled = !generatingMix, role = Role.RadioButton) {
                                                    selectedSeed = song
                                                    generatedMix = emptyList()
                                                    generationError = null
                                                    mixSaved = false
                                                }
                                                .padding(8.dp),
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
                                    if (seeds.isEmpty() && seedSearchResults.isEmpty()) {
                                        if (mixMode == ScrobbleMixMode.RADIO) {
                                            EmptyPanel("No radio seed yet", "Search YouTube Music above to choose a track, even if your listening history is empty.")
                                        } else {
                                            EmptyPanel("No artist seed yet", "Search for a track by the artist you want to explore, then select it above.")
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
                        if (mixMode == ScrobbleMixMode.SIMILAR_ARTISTS) {
                            item("generate-similar-artist-seed") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Choose an artist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    OutlinedTextField(
                                        value = similarArtistQuery,
                                        onValueChange = {
                                            similarArtistQuery = it
                                            generatedMix = emptyList()
                                            generationError = null
                                            mixSaved = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = !generatingMix,
                                        label = { Text("Artist name") },
                                        placeholder = { Text("e.g. Radiohead") },
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    Text(
                                        "Mylo uses Last.fm to find related artists, then searches YouTube Music for playable tracks.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
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
                                        if (lastFmDiscoveryAvailable) "Blends YouTube Music search results with Last.fm's top tracks for this tag."
                                        else "Searches YouTube Music for real tracks matching this tag.",
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
                        if (mixMode == ScrobbleMixMode.FAVOURITES || mixMode == ScrobbleMixMode.TASTE_MIX) {
                            item("generate-top-period") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Listening period", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        ScrobbleTopPeriod.values().forEach { period ->
                                            FilterChip(
                                                selected = generationTopPeriod == period,
                                                onClick = {
                                                    if (generationTopPeriod != period) {
                                                        generationTopPeriod = period
                                                        generatedMix = emptyList()
                                                        generationError = null
                                                        mixSaved = false
                                                    }
                                                },
                                                enabled = !generatingMix,
                                                label = { Text(period.label) },
                                            )
                                        }
                                    }
                                    if (generationTopPeriod != ScrobbleTopPeriod.ALL_TIME && syncedStats.totalPlays > 0) {
                                        Text(
                                            "Date filters use Mylo listens; synced YouTube Music history is included in All time only.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    when {
                                        loadingGenerationTopTracks -> LoadingLine("Loading your top tracks")
                                        generationTopPeriod == ScrobbleTopPeriod.ALL_TIME &&
                                            generationTopTracks.isEmpty() && syncedStats.topTracks.isNotEmpty() -> InfoPanel(
                                                "Using synced YouTube Music history",
                                                "Your account listening history is supplying this all-time favourites mix.",
                                            )
                                        generationTopTracksFailed && syncedStats.topTracks.isEmpty() -> EmptyPanel(
                                            "Couldn't load top tracks",
                                            "Check your listening statistics and try again.",
                                        )
                                        generationTopTracks.isEmpty() && syncedStats.topTracks.isEmpty() -> EmptyPanel(
                                            "No top tracks yet",
                                            "Listen to a few tracks in Mylo or sync YouTube Music listening history, then build a favourites mix.",
                                        )
                                    }
                                }
                            }
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
                                    (mixMode !in setOf(ScrobbleMixMode.FAVOURITES, ScrobbleMixMode.TASTE_MIX, ScrobbleMixMode.DISCOVER, ScrobbleMixMode.NEVER_HEARD) || !loadingGenerationTopTracks) &&
                                    (mixMode !in setOf(ScrobbleMixMode.FAVOURITES, ScrobbleMixMode.TASTE_MIX) || !loadingSyncedStats) &&
                                    (mixMode != ScrobbleMixMode.FAVOURITES || favouriteTracks.isNotEmpty()) &&
                                    (mixMode != ScrobbleMixMode.TAG || tagQuery.isNotBlank()) &&
                                    (mixMode != ScrobbleMixMode.REDISCOVER || !loadingAllTimeStats) &&
                                    (mixMode != ScrobbleMixMode.REDISCOVER || !allTimeStatsFailed) &&
                                    (mixMode !in setOf(ScrobbleMixMode.REDISCOVER, ScrobbleMixMode.NEVER_HEARD) || !historyFailed) &&
                                    (mixMode !in setOf(ScrobbleMixMode.RADIO, ScrobbleMixMode.ARTIST_MIX) || selectedSeed?.let(::isYouTubeCatalogTrack) == true) &&
                                    (mixMode != ScrobbleMixMode.SIMILAR_ARTISTS || similarArtistQuery.isNotBlank()),
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
                                                playlistSortMode = ScrobblePlaylistSortMode.RECENT
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
                                TextButton(onClick = { openedPlaylist = null; addingTracksPlaylist = null }) {
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
                                        PlaylistArtwork(selected.songs, selected.coverUri, Modifier.fillMaxWidth().height(188.dp))
                                        Text(selected.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                        Text(selected.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${selected.songs.size} tracks · Saved on this device", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        Button(
                                            onClick = { onPlayPlaylist(selected.title, selected.songs, 0) },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = selected.songs.isNotEmpty(),
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Play all · ${selected.songs.size} tracks")
                                        }
                                    }
                                }
                            }
                            item("playlist-add-tracks") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (selected.songs.isEmpty()) {
                                        EmptyPanel(
                                            "Your playlist is empty",
                                            "Add tracks from your synced YouTube Music history or your recent Mylo listens.",
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            addingTracksPlaylist = if (addingTracksPlaylist == selected.title) null else selected.title
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(if (addingTracksPlaylist == selected.title) "Done adding tracks" else "Add tracks from history")
                                    }
                                }
                            }
                            if (addingTracksPlaylist == selected.title) {
                                if (addableHistoryTracks.isEmpty()) {
                                    item("playlist-add-empty") {
                                        EmptyPanel("No tracks to add", "Sync YouTube Music listening history or play a few tracks in Mylo first.")
                                    }
                                } else {
                                    item("playlist-add-heading") {
                                        StatsSectionHeading("Available tracks", "Recent and most-played listens")
                                    }
                                    itemsIndexed(
                                        addableHistoryTracks,
                                        key = { _, song -> "add-${songIdentity(song)}" },
                                    ) { _, song ->
                                        val alreadyAdded = selected.songs.any { songIdentity(it) == songIdentity(song) }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            AsyncImage(
                                                model = song.thumbnailUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                                            )
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            TextButton(
                                                enabled = !alreadyAdded && selected.songs.size < 50,
                                                onClick = {
                                                    val added = ScrobblePlaylistStore.addSongs(selected.title, listOf(song))
                                                    if (added > 0) {
                                                        Toast.makeText(context, "Added to ${selected.title}", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                            ) {
                                                Text(if (alreadyAdded) "Added" else "Add")
                                            }
                                        }
                                    }
                                }
                            }
                            items(selected.songs.size, key = { index -> "saved-" + selected.title + "-" + songIdentity(selected.songs[index]) + "-" + index }) { index ->
                                val song = selected.songs[index]
                                var trackMenuExpanded by remember(selected.title, index) { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(Modifier.weight(1f)) {
                                        TrackRow(song, onClick = { onPlayPlaylist(selected.title, selected.songs, index) })
                                    }
                                    Box {
                                        IconButton(onClick = { trackMenuExpanded = true }) {
                                            Icon(Icons.Rounded.MoreVert, contentDescription = "Track actions")
                                        }
                                        DropdownMenu(
                                            expanded = trackMenuExpanded,
                                            onDismissRequest = { trackMenuExpanded = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Move up") },
                                                enabled = index > 0,
                                                onClick = {
                                                    ScrobblePlaylistStore.moveSong(selected.title, index, index - 1)
                                                    trackMenuExpanded = false
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Move down") },
                                                enabled = index < selected.songs.lastIndex,
                                                onClick = {
                                                    ScrobblePlaylistStore.moveSong(selected.title, index, index + 1)
                                                    trackMenuExpanded = false
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Remove from playlist") },
                                                onClick = {
                                                    if (ScrobblePlaylistStore.removeSong(selected.title, index)) {
                                                        Toast.makeText(context, "Removed from " + selected.title, Toast.LENGTH_SHORT).show()
                                                    }
                                                    trackMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            val visiblePlaylists = savedPlaylists
                                .filter { playlist ->
                                    playlist.title.contains(playlistQuery, ignoreCase = true) ||
                                        playlist.reason.contains(playlistQuery, ignoreCase = true) ||
                                        playlist.songs.any { it.title.contains(playlistQuery, ignoreCase = true) || it.artist.contains(playlistQuery, ignoreCase = true) }
                                }
                                .let { matches -> when (playlistSortMode) {
                                    ScrobblePlaylistSortMode.RECENT -> matches
                                    ScrobblePlaylistSortMode.OLDEST -> matches.reversed()
                                    ScrobblePlaylistSortMode.NAME -> matches.sortedBy { it.title.lowercase() }
                                    ScrobblePlaylistSortMode.TRACK_COUNT -> matches.sortedWith(
                                        compareByDescending<SavedScrobblePlaylist> { it.songs.size }
                                            .thenBy { it.title.lowercase() },
                                    )
                                } }
                            item("playlists-heading") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Your collections", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                "${savedPlaylists.size} saved mixes · Made by you, kept on this device",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (!playlistSelectionMode) {
                                            TextButton(
                                                onClick = {
                                                    createPlaylistTitle = ""
                                                    createPlaylistError = null
                                                    createPlaylistDialog = true
                                                },
                                            ) { Text("New") }
                                        }
                                        TextButton(
                                            onClick = {
                                                playlistSelectionMode = !playlistSelectionMode
                                                selectedPlaylistTitles = emptySet()
                                            },
                                        ) {
                                            Text(if (playlistSelectionMode) "Cancel" else "Select")
                                        }
                                    }
                                    if (playlistSelectionMode) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                "${selectedPlaylistTitles.size} selected",
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            TextButton(
                                                onClick = {
                                                    selectedPlaylistTitles = if (selectedPlaylistTitles.size == visiblePlaylists.size) {
                                                        emptySet()
                                                    } else {
                                                        visiblePlaylists.mapTo(linkedSetOf()) { it.title }
                                                    }
                                                },
                                                enabled = visiblePlaylists.isNotEmpty(),
                                            ) {
                                                Text(if (selectedPlaylistTitles.size == visiblePlaylists.size) "Deselect all" else "Select all")
                                            }
                                            Button(
                                                onClick = { showBulkRemoveConfirm = true },
                                                enabled = selectedPlaylistTitles.isNotEmpty(),
                                            ) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                                                Spacer(Modifier.width(5.dp))
                                                Text("Remove")
                                            }
                                        }
                                    }
                                }
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Rounded.Sort, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        FilterChip(selected = playlistSortMode == ScrobblePlaylistSortMode.RECENT, onClick = { playlistSortMode = ScrobblePlaylistSortMode.RECENT }, label = { Text("Recently saved") })
                                        FilterChip(selected = playlistSortMode == ScrobblePlaylistSortMode.OLDEST, onClick = { playlistSortMode = ScrobblePlaylistSortMode.OLDEST }, label = { Text("Oldest") })
                                        FilterChip(selected = playlistSortMode == ScrobblePlaylistSortMode.NAME, onClick = { playlistSortMode = ScrobblePlaylistSortMode.NAME }, label = { Text("Name") })
                                        FilterChip(selected = playlistSortMode == ScrobblePlaylistSortMode.TRACK_COUNT, onClick = { playlistSortMode = ScrobblePlaylistSortMode.TRACK_COUNT }, label = { Text("Most tracks") })
                                    }
                                }
                            }
                            if (savedPlaylists.isEmpty()) {
                                item("playlists-empty") {
                                    EmptyPanel("Your library is ready", "Create a playlist here or build a mix in Generate. Saved playlists stay separate from listening history.")
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
                                                        .semantics { this.selected = playlist.title in selectedPlaylistTitles }
                                                        .clickable(role = if (playlistSelectionMode) Role.Checkbox else Role.Button) {
                                                            if (playlistSelectionMode) {
                                                                selectedPlaylistTitles = if (playlist.title in selectedPlaylistTitles) {
                                                                    selectedPlaylistTitles - playlist.title
                                                                } else {
                                                                    selectedPlaylistTitles + playlist.title
                                                                }
                                                            } else {
                                                                openedPlaylist = playlist.title
                                                            }
                                                        },
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    PlaylistArtwork(
                                                        playlist.songs,
                                                        playlist.coverUri,
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
                                                    if (playlistSelectionMode) {
                                                        IconButton(
                                                            onClick = {
                                                                selectedPlaylistTitles = if (playlist.title in selectedPlaylistTitles) {
                                                                    selectedPlaylistTitles - playlist.title
                                                                } else {
                                                                    selectedPlaylistTitles + playlist.title
                                                                }
                                                            },
                                                        ) {
                                                            Icon(
                                                                if (playlist.title in selectedPlaylistTitles) Icons.Rounded.CheckCircle else Icons.Rounded.MoreVert,
                                                                contentDescription = if (playlist.title in selectedPlaylistTitles) "Deselect playlist" else "Select playlist",
                                                                tint = if (playlist.title in selectedPlaylistTitles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                    } else {
                                                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More playlist actions") }
                                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                                        DropdownMenuItem(text = { Text("Open") }, onClick = { menuExpanded = false; openedPlaylist = playlist.title })
                                                        DropdownMenuItem(
                                                            text = { Text(if (generatingSimilarPlaylist == playlist.title) "Generating similar…" else "Generate similar") },
                                                            enabled = generatingSimilarPlaylist == null && playlist.songs.any(::isYouTubeCatalogTrack),
                                                            onClick = { menuExpanded = false; generateSimilarPlaylist(playlist) },
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Change cover") },
                                                            onClick = { menuExpanded = false; coverEditorPlaylist = playlist.title },
                                                        )
                                                        if (!playlist.coverUri.isNullOrBlank()) {
                                                            DropdownMenuItem(
                                                                text = { Text("Use automatic cover") },
                                                                onClick = {
                                                                    menuExpanded = false
                                                                    releaseCoverPermission(ScrobblePlaylistStore.setCover(playlist.title, null))
                                                                },
                                                            )
                                                        }
                                                        DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                                            menuExpanded = false
                                                            renamePlaylistDraft = playlist.title
                                                            renamePlaylistError = null
                                                            pendingRenamePlaylist = playlist.title
                                                        })
                                                        DropdownMenuItem(text = { Text("Save as CSV") }, onClick = {
                                                            menuExpanded = false
                                                            savePlaylistExport(playlist, ScrobblePlaylistExportFormat.CSV)
                                                        })
                                                        DropdownMenuItem(text = { Text("Save as M3U") }, onClick = {
                                                            menuExpanded = false
                                                            savePlaylistExport(playlist, ScrobblePlaylistExportFormat.M3U)
                                                        })
                                                        DropdownMenuItem(text = { Text("Share CSV") }, onClick = {
                                                            menuExpanded = false
                                                            sharePlaylistExport(playlist, ScrobblePlaylistExportFormat.CSV)
                                                        })
                                                        DropdownMenuItem(text = { Text("Share M3U") }, onClick = {
                                                            menuExpanded = false
                                                            sharePlaylistExport(playlist, ScrobblePlaylistExportFormat.M3U)
                                                        })
                                                        DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) }, onClick = { menuExpanded = false; pendingRemovePlaylist = playlist.title })
                                                    }
                                                    }
                                                }
                                            }
                                            if (!playlistSelectionMode) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun StatsOverviewCard(
    summary: ReplaySummary,
    reduceMotion: Boolean,
) {
    val listeningTimeStyle = if (formatListeningTime(summary.totalMs).length > 7) {
        MaterialTheme.typography.titleLarge
    } else {
        MaterialTheme.typography.headlineSmall
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(0.95f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "LISTENING TIME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.65.sp,
                )
                AnimatedValueText(
                    value = summary.totalMs / 60_000L,
                    reduceMotion = reduceMotion,
                    style = listeningTimeStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    formatter = { formatListeningTime(it * 60_000L) },
                )
            }
            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
            )
            StatsMetricLine("PLAYS", summary.totalPlays.toLong(), reduceMotion)
            StatsMetricLine("TRACKS", summary.distinctSongs.toLong(), reduceMotion)
            StatsMetricLine("ARTISTS", summary.distinctArtists.toLong(), reduceMotion)
        }
        Column(
            modifier = Modifier.weight(1.05f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "ACTIVITY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.65.sp,
            )
            HourlyActivityMiniChart(summary.hourOfDay, reduceMotion)
        }
    }
}

@Composable
private fun SyncedHistoryOverviewCard(
    summary: SyncedHistorySummary,
    reduceMotion: Boolean,
) {
    val maxArtistPlays = summary.topArtists.firstOrNull()?.plays?.coerceAtLeast(1) ?: 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(0.95f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "YOUTUBE MUSIC",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.65.sp,
            )
            StatsMetricLine("PLAYS", summary.totalPlays.toLong(), reduceMotion)
            StatsMetricLine("TRACKS", summary.distinctTracks.toLong(), reduceMotion)
            StatsMetricLine("ARTISTS", summary.distinctArtists.toLong(), reduceMotion)
            StatsMetricLine("ALBUMS", summary.distinctAlbums.toLong(), reduceMotion)
        }
        Column(
            modifier = Modifier.weight(1.05f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "TOP ARTISTS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.65.sp,
            )
            summary.topArtists.take(4).forEach { artist ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            artist.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            artist.plays.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth().height(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        Box(
                            Modifier.fillMaxWidth((artist.plays.toFloat() / maxArtistPlays).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsMetricLine(
    label: String,
    value: Long,
    reduceMotion: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.35.sp,
        )
        AnimatedValueText(
            value = value,
            reduceMotion = reduceMotion,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HourlyActivityMiniChart(
    hours: List<Long>,
    reduceMotion: Boolean,
) {
    val peak = hours.maxOrNull()?.takeIf { it > 0L } ?: 0L
    val description = hours.withIndex()
        .filter { it.value > 0L }
        .joinToString { (hour, value) -> "%02d:00 %s".format(hour, formatListeningTime(value)) }
    var visible by remember(hours) { mutableStateOf(false) }
    LaunchedEffect(hours, reduceMotion) {
        visible = false
        if (reduceMotion) {
            visible = true
        } else {
            delay(35)
            visible = true
        }
    }
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(480, easing = FastOutSlowInEasing),
        label = "compact-hourly-activity",
    )
    val dayParts = remember(hours) { buildListeningDayParts(hours) }
    val totalDayPartMs = dayParts.sumOf { it.listeningMs }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.Start,
        ) {
            if (totalDayPartMs > 0L) {
                dayParts.forEachIndexed { index, part ->
                    if (part.listeningMs > 0L) {
                        val alpha = when (index) {
                            0 -> 1f
                            1 -> 0.78f
                            2 -> 0.56f
                            else -> 0.34f
                        }
                        Box(
                            Modifier.weight(part.listeningMs.toFloat() / totalDayPartMs.toFloat())
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(112.dp).semantics {
                contentDescription = if (description.isBlank()) "No hourly listening activity recorded"
                else "Hourly listening activity: $description"
            },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            hours.forEachIndexed { hour, value ->
                val base = if (peak == 0L) 0.035f else (value.toFloat() / peak.toFloat()).coerceAtLeast(0.035f)
                val height = if (peak == 0L) base else (base * progress).coerceAtLeast(0.035f)
                Box(
                    modifier = Modifier.weight(1f)
                        .fillMaxHeight(height)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = if (value > 0L) 0.42f + (value.toFloat() / peak.toFloat()) * 0.58f else 0.18f,
                            ),
                        ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("00", "06", "12", "18", "23").forEach { hour ->
                Text(
                    hour,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
private fun StatsSectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
private fun PlaylistArtwork(
    songs: List<Song>,
    coverUri: String?,
    modifier: Modifier = Modifier,
) {
    if (coverUri.isNullOrBlank()) {
        ArtworkMosaic(songs, modifier)
    } else {
        AsyncImage(
            model = coverUri,
            contentDescription = "Playlist cover",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(18.dp)),
        )
    }
}

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

private fun formatStatsTimestamp(timestamp: Long): String = runCatching {
    java.time.Instant.ofEpochMilli(timestamp)
        .atZone(java.time.ZoneId.systemDefault())
        .format(
            java.time.format.DateTimeFormatter.ofPattern(
                "EEE, d MMM · HH:mm",
                java.util.Locale.getDefault(),
            ),
        )
}.getOrDefault("Played recently")

private fun formatListeningTime(totalMs: Long): String {
    val totalMinutes = totalMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

