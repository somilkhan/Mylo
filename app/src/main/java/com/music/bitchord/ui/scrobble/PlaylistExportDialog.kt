package com.music.bitchord.ui.scrobble

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.music.bitchord.R
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistExport
import com.music.bitchord.ui.components.AlertAction
import com.music.bitchord.ui.components.AlertRule
import com.music.bitchord.ui.components.AlertScaffold
import com.music.bitchord.ui.components.PillSegmentedControl
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Export one playlist as CSV or M3U using the app's shared alert and segmented controls. */
@Composable
@OptIn(ExperimentalHazeMaterialsApi::class)
internal fun ScrobblePlaylistExportDialog(
    playlist: LocalPlaylist,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit = onMessage,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var csvSelected by rememberSaveable(playlist.id) { mutableStateOf(true) }
    var busy by remember(playlist.id) { mutableStateOf(false) }
    var pendingPlaylist by remember(playlist.id) { mutableStateOf<LocalPlaylist?>(null) }

    fun save(uri: Uri?, csv: Boolean) {
        if (uri == null) {
            busy = false
            pendingPlaylist = null
            onDismiss()
            return
        }
        val snapshot = pendingPlaylist ?: playlist
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val content = if (csv) LocalPlaylistExport.csv(snapshot) else LocalPlaylistExport.m3u(snapshot)
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("No output stream")
                }
                onMessage(context.getString(if (csv) R.string.scrobble_export_csv_saved else R.string.scrobble_export_m3u_saved))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onError(context.getString(if (csv) R.string.scrobble_export_csv_failed else R.string.scrobble_export_m3u_failed))
            } finally {
                busy = false
                pendingPlaylist = null
                onDismiss()
            }
        }
    }

    val csvSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        save(uri, csv = true)
    }
    val m3uSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        save(uri, csv = false)
    }

    fun share(csv: Boolean) {
        if (busy) return
        busy = true
        val snapshot = playlist
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    val content = if (csv) LocalPlaylistExport.csv(snapshot) else LocalPlaylistExport.m3u(snapshot)
                    val extension = if (csv) "csv" else "m3u"
                    val directory = File(context.cacheDir, "shared/playlist_exports")
                    check(directory.isDirectory || directory.mkdirs()) { "Couldn't prepare export directory" }
                    // Playlist IDs can come from imported data: never use them as path components.
                    val safeId = UUID.nameUUIDFromBytes(snapshot.id.toByteArray(Charsets.UTF_8))
                    val file = File(directory, "$safeId-${LocalPlaylistExport.fileName(snapshot.title, extension)}")
                    file.writeText(content, Charsets.UTF_8)
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = if (csv) "text/csv" else "audio/x-mpegurl"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(snapshot.title, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.scrobble_share_chooser_title)))
                onMessage(context.getString(R.string.scrobble_export_ready))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onError(context.getString(R.string.scrobble_export_share_failed))
            } finally {
                busy = false
                onDismiss()
            }
        }
    }

    AlertScaffold(hazeState = hazeState, onDismiss = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.scrobble_export_playlist),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.scrobble_choose_format, playlist.title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            PillSegmentedControl(
                options = listOf(
                    stringResource(R.string.scrobble_export_format_csv),
                    stringResource(R.string.scrobble_export_format_m3u),
                ),
                selectedIndex = if (csvSelected) 0 else 1,
                onSelect = { if (!busy) csvSelected = it == 0 },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.save),
            emphasised = true,
            contentColor = MaterialTheme.colorScheme.primary,
            enabled = !busy,
            onClick = {
                if (!busy) {
                    busy = true
                    pendingPlaylist = playlist
                    try {
                        if (csvSelected) {
                            csvSaveLauncher.launch(LocalPlaylistExport.fileName(playlist.title, "csv"))
                        } else {
                            m3uSaveLauncher.launch(LocalPlaylistExport.fileName(playlist.title, "m3u"))
                        }
                    } catch (_: Exception) {
                        busy = false
                        pendingPlaylist = null
                        onError(context.getString(if (csvSelected) R.string.scrobble_export_csv_failed else R.string.scrobble_export_m3u_failed))
                        onDismiss()
                    }
                }
            },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.share),
            emphasised = false,
            contentColor = MaterialTheme.colorScheme.onSurface,
            enabled = !busy,
            onClick = { share(csvSelected) },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            contentColor = MaterialTheme.colorScheme.onSurface,
            enabled = !busy,
            onClick = onDismiss,
        )
    }
}
