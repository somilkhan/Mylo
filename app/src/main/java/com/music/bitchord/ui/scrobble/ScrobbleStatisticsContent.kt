package com.music.bitchord.ui.scrobble

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistExport
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.ui.replay.ReplayScreen
import com.music.bitchord.ui.replay.ReplayState
import com.music.bitchord.ui.replay.ReplayStoryPage
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private enum class ScrobbleStatsSource { MYLO_REPLAY, LAST_FM }

private enum class LastFmStatsPeriod(val apiValue: String, val label: String) {
    ALL_TIME("overall", "All time"),
    WEEK("7day", "7 days"),
    MONTH("1month", "1 month"),
    THREE_MONTHS("3month", "3 months"),
    SIX_MONTHS("6month", "6 months"),
    YEAR("12month", "12 months"),
}

@Composable
fun ScrobbleStatisticsContent(
    replayState: ReplayState,
    holder: String,
    onPeriodChange: (ReplayPeriod) -> Unit,
    onOpenStory: (ReplayStoryPage) -> Unit,
    onPlaySong: (Song) -> Unit,
    onOpenArtist: (String?, String) -> Unit,
    onOpenAlbum: (String?, String, String?, String?) -> Unit,
    onShare: () -> Unit,
    contentPadding: PaddingValues,
    replayListState: LazyListState,
) {
    var source by rememberSaveable { mutableStateOf(ScrobbleStatsSource.MYLO_REPLAY) }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = contentPadding.calculateTopPadding(),
                    bottom = 10.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = source == ScrobbleStatsSource.MYLO_REPLAY,
                onClick = { source = ScrobbleStatsSource.MYLO_REPLAY },
                label = { Text("Mylo Replay") },
            )
            FilterChip(
                selected = source == ScrobbleStatsSource.LAST_FM,
                onClick = { source = ScrobbleStatsSource.LAST_FM },
                label = { Text("Last.fm") },
            )
        }
        when (source) {
            ScrobbleStatsSource.MYLO_REPLAY -> ReplayScreen(
                state = replayState,
                holder = holder,
                onPeriodChange = onPeriodChange,
                onOpenStory = onOpenStory,
                onPlaySong = onPlaySong,
                onOpenArtist = onOpenArtist,
                onOpenAlbum = onOpenAlbum,
                onShare = onShare,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                modifier = Modifier.weight(1f),
                listState = replayListState,
                heading = "Statistics",
            )
            ScrobbleStatsSource.LAST_FM -> LastFmRemoteStatistics(
                modifier = Modifier.weight(1f),
                bottomPadding = contentPadding.calculateBottomPadding(),
            )
        }
    }
}

@Composable
private fun LastFmRemoteStatistics(
    modifier: Modifier = Modifier,
    bottomPadding: Dp,
) {
    val username by AppSettings.lastfmUsername.collectAsStateWithLifecycle()
    val apiKey by AppSettings.lastfmApiKey.collectAsStateWithLifecycle()
    val secret by AppSettings.lastfmSecret.collectAsStateWithLifecycle()
    val endpoint by AppSettings.lastfmEndpoint.collectAsStateWithLifecycle()
    val sessionKey by AppSettings.lastfmSessionKey.collectAsStateWithLifecycle()
    val configured = username.isNotBlank() && LastFM.isInitialized()

    var period by rememberSaveable { mutableStateOf(LastFmStatsPeriod.ALL_TIME) }
    var retryKey by rememberSaveable { mutableIntStateOf(0) }
    var profile by remember { mutableStateOf<LastFM.UserStats?>(null) }
    var tracks by remember { mutableStateOf<List<LastFM.RankedTrack>>(emptyList()) }
    var artists by remember { mutableStateOf<List<LastFM.RankedArtist>>(emptyList()) }
    var albums by remember { mutableStateOf<List<LastFM.RankedAlbum>>(emptyList()) }
    var profileLoading by remember { mutableStateOf(false) }
    var chartsLoading by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var chartsError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(username, apiKey, secret, endpoint, sessionKey, configured, retryKey) {
        if (!configured) {
            profile = null
            profileError = null
            profileLoading = false
            return@LaunchedEffect
        }
        profileLoading = true
        val result = LastFM.getUserStats(username)
        profile = result.getOrNull()
        profileError = result.exceptionOrNull()?.message
            ?: if (result.isFailure) "Unable to load the Last.fm profile" else null
        profileLoading = false
    }

    LaunchedEffect(username, apiKey, secret, endpoint, sessionKey, configured, period, retryKey) {
        if (!configured) {
            tracks = emptyList()
            artists = emptyList()
            albums = emptyList()
            chartsError = null
            chartsLoading = false
            return@LaunchedEffect
        }
        chartsLoading = true
        val results = coroutineScope {
            val trackRequest = async { LastFM.getTopTracks(username, period.apiValue, 10) }
            val artistRequest = async { LastFM.getTopArtists(username, period.apiValue, 10) }
            val albumRequest = async { LastFM.getTopAlbums(username, period.apiValue, 10) }
            Triple(trackRequest.await(), artistRequest.await(), albumRequest.await())
        }
        tracks = results.first.getOrNull().orEmpty()
        artists = results.second.getOrNull().orEmpty()
        albums = results.third.getOrNull().orEmpty()
        val chartMessages = listOfNotNull(
            results.first.exceptionOrNull()?.message,
            results.second.exceptionOrNull()?.message,
            results.third.exceptionOrNull()?.message,
        ).distinct()
        chartsError = if (
            results.first.isFailure || results.second.isFailure || results.third.isFailure
        ) {
            chartMessages.joinToString(" · ").ifBlank { "Unable to load one or more Last.fm charts" }
        } else {
            null
        }
        chartsLoading = false
    }

    if (!configured) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Last.fm is not connected", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Connect a Last.fm account and add its username in Settings. These figures are fetched from Last.fm and stay separate from Mylo Replay.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = profile?.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Last.fm profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("@$username · Remote statistics", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (profileLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                profileError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { retryKey++ }) { Text("Retry") }
                }
                profile?.let { stats ->
                    Text("All-time totals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LastFmMetric("Plays", stats.playCount, Modifier.weight(1f))
                        LastFmMetric("Tracks", stats.trackCount, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LastFmMetric("Artists", stats.artistCount, Modifier.weight(1f))
                        LastFmMetric("Albums", stats.albumCount, Modifier.weight(1f))
                    }
                }
                Text("Top charts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LastFmStatsPeriod.entries.forEach { option ->
                        FilterChip(
                            selected = period == option,
                            onClick = { period = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                if (chartsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                chartsError?.let { error ->
                    Text("Some Last.fm charts could not be loaded: $error", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { retryKey++ }) { Text("Retry charts") }
                }
            }
        }
        if (tracks.isNotEmpty()) {
            item { Text("Top tracks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            itemsIndexed(tracks, key = { index, item -> "track-$index-${item.name}-${item.artist}" }) { index, item ->
                LastFmChartRow(index + 1, item.name, item.artist, item.playCount, item.artworkUrl)
            }
        }
        if (artists.isNotEmpty()) {
            item { Text("Top artists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            itemsIndexed(artists, key = { index, item -> "artist-$index-${item.name}" }) { index, item ->
                LastFmChartRow(index + 1, item.name, "Artist", item.playCount, item.artworkUrl)
            }
        }
        if (albums.isNotEmpty()) {
            item { Text("Top albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            itemsIndexed(albums, key = { index, item -> "album-$index-${item.name}-${item.artist}" }) { index, item ->
                LastFmChartRow(index + 1, item.name, item.artist, item.playCount, item.artworkUrl)
            }
        }
        if (!chartsLoading && tracks.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
            item {
                Text(
                    "No Last.fm chart data for this period yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun LastFmMetric(label: String, value: Long, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LastFmChartRow(
    rank: Int,
    title: String,
    subtitle: String,
    plays: Long,
    artworkUrl: String?,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rank.toString(), modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("$plays", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@Composable
internal fun ScrobblePlaylistExportDialog(
    playlist: LocalPlaylist,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val csvSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(LocalPlaylistExport.csv(playlist).toByteArray(Charsets.UTF_8))
                } ?: error("Couldn't open the selected file")
            }.onSuccess { onMessage("CSV saved") }
                .onFailure { onMessage(it.message ?: "Couldn't save CSV") }
        }
        onDismiss()
    }
    val m3uSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(LocalPlaylistExport.m3u(playlist).toByteArray(Charsets.UTF_8))
                } ?: error("Couldn't open the selected file")
            }.onSuccess { onMessage("M3U saved") }
                .onFailure { onMessage(it.message ?: "Couldn't save M3U") }
        }
        onDismiss()
    }

    fun share(csv: Boolean) {
        val content = if (csv) LocalPlaylistExport.csv(playlist) else LocalPlaylistExport.m3u(playlist)
        val extension = if (csv) "csv" else "m3u"
        val mimeType = if (csv) "text/csv" else "audio/x-mpegurl"
        runCatching {
            val directory = File(context.cacheDir, "shared/playlist_exports")
            if (!directory.exists() && !directory.mkdirs()) error("Couldn't prepare the export file")
            val file = File(directory, "${playlist.id}-${LocalPlaylistExport.fileName(playlist.title, extension)}")
            file.writeText(content, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Playlist export", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share playlist"))
        }.onSuccess { onMessage("Ready to share") }
            .onFailure { onMessage(it.message ?: "Couldn't share playlist") }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose a format for ${playlist.title}.")
                TextButton(onClick = { csvSaveLauncher.launch(LocalPlaylistExport.fileName(playlist.title, "csv")) }) {
                    Text("Save CSV")
                }
                TextButton(onClick = { share(true); onDismiss() }) { Text("Share CSV") }
                TextButton(onClick = { m3uSaveLauncher.launch(LocalPlaylistExport.fileName(playlist.title, "m3u")) }) {
                    Text("Save M3U")
                }
                TextButton(onClick = { share(false); onDismiss() }) { Text("Share M3U") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
