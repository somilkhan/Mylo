package com.music.bitchord.ui.scrobble

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistExport
import com.music.bitchord.data.stats.ReplayPeriod
import java.io.File
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScrobbleStatisticsContent(
    viewModel: ScrobbleStatisticsViewModel,
    reduceMotion: Boolean,
    onPlaySong: (Song) -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastFmEnabled by AppSettings.lastfmEnabled.collectAsStateWithLifecycle()
    val lastFmUsername by AppSettings.lastfmUsername.collectAsStateWithLifecycle()
    val lastFmApiKey by AppSettings.lastfmApiKey.collectAsStateWithLifecycle()
    val lastFmSecret by AppSettings.lastfmSecret.collectAsStateWithLifecycle()
    val lastFmEndpoint by AppSettings.lastfmEndpoint.collectAsStateWithLifecycle()

    LaunchedEffect(lastFmEnabled, lastFmUsername, lastFmApiKey, lastFmSecret, lastFmEndpoint) {
        viewModel.onScreenOpened()
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.onScreenClosed() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 20.dp,
                        end = 12.dp,
                        top = contentPadding.calculateTopPadding() + 8.dp,
                        bottom = 4.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.period.chip,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Statistics", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50),
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReplayPeriod.entries.forEach { period ->
                    val selected = state.period == period
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { viewModel.setPeriod(period) },
                            )
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = period.chip,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onTertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 12.dp,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.error != null && state.lastFmConnected) {
                        item(key = "lastfm-retry") {
                            StatisticsRetryNotice(
                                message = state.error.orEmpty(),
                                onRetry = viewModel::refresh,
                            )
                        }
                    }

                    if (state.isLoading) {
                        items(5, key = { "skeleton-$it" }) {
                            StatisticsTrackSkeletonRow()
                        }
                    } else if (state.tracks.isEmpty()) {
                        item(key = "empty") {
                            StatisticsEmptyState(
                                lastFmConnected = state.lastFmConnected,
                                onOpenSettings = onOpenSettings,
                            )
                        }
                    } else {
                        item(key = "concentration") {
                            TrackConcentrationCard(
                                trackPlays = state.tracks.map { it.plays },
                                localListeningMs = state.localSummary?.totalMs ?: 0L,
                                reduceMotion = reduceMotion,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        item(key = "tracks-heading") {
                            Text(
                                "Your tracks",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(state.tracks, key = { it.identity }) { track ->
                            MergedStatisticsTrackRow(
                                track = track,
                                onPlaySong = onPlaySong,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsRetryNotice(
    message: String,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) {
                Text("Retry", color = colors.onErrorContainer)
            }
        }
    }
}

@Composable
private fun StatisticsEmptyState(
    lastFmConnected: Boolean,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("No listening yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (lastFmConnected) {
                "No listening history for this period."
            } else {
                "Play a few tracks, or connect Last.fm in Settings to bring in your history."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!lastFmConnected) {
            Button(onClick = onOpenSettings) {
                Text("Open Settings")
            }
        }
    }
}

@Composable
private fun StatisticsTrackSkeletonRow() {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerHighest),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.62f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.surfaceContainerHighest),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.38f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.surfaceContainerHighest),
                )
            }
            Box(
                Modifier
                    .width(30.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.surfaceContainerHighest),
            )
        }
    }
}

@Composable
private fun MergedStatisticsTrackRow(
    track: MergedStatisticsTrack,
    onPlaySong: (Song) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val song = track.song
    val interaction = if (song != null) {
        Modifier.clickable(role = Role.Button) { onPlaySong(song) }
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .then(interaction),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceVariant,
        contentColor = colors.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (!track.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text("♫", color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    track.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Text(
                NumberFormat.getIntegerInstance().format(track.plays),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
        }
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
