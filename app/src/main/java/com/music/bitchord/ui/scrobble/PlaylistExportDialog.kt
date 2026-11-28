package com.music.bitchord.ui.scrobble

import android.content.ClipData
import android.content.Intent
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
import java.io.File

/** Export one playlist as CSV or M3U: the format as a segmented choice, then Save, Share or Cancel. */
@Composable
@OptIn(ExperimentalHazeMaterialsApi::class)
internal fun ScrobblePlaylistExportDialog(
    playlist: LocalPlaylist,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var csvSelected by rememberSaveable { mutableStateOf(true) }
    val csvSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(LocalPlaylistExport.csv(playlist).toByteArray(Charsets.UTF_8))
                } ?: error(context.getString(R.string.scrobble_export_file_failed))
            }.onSuccess { onMessage(context.getString(R.string.scrobble_export_csv_saved)) }
                .onFailure { onMessage(context.getString(R.string.scrobble_export_csv_failed)) }
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
                } ?: error(context.getString(R.string.scrobble_export_file_failed))
            }.onSuccess { onMessage(context.getString(R.string.scrobble_export_m3u_saved)) }
                .onFailure { onMessage(context.getString(R.string.scrobble_export_m3u_failed)) }
        }
        onDismiss()
    }

    fun share(csv: Boolean) {
        val content = if (csv) LocalPlaylistExport.csv(playlist) else LocalPlaylistExport.m3u(playlist)
        val extension = if (csv) "csv" else "m3u"
        val mimeType = if (csv) "text/csv" else "audio/x-mpegurl"
        runCatching {
            val directory = File(context.cacheDir, "shared/playlist_exports")
            if (!directory.exists() && !directory.mkdirs()) error(context.getString(R.string.scrobble_export_prepare_failed))
            val file = File(directory, "${playlist.id}-${LocalPlaylistExport.fileName(playlist.title, extension)}")
            file.writeText(content, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(playlist.title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.scrobble_share_chooser_title)))
        }.onSuccess { onMessage(context.getString(R.string.scrobble_export_ready)) }
            .onFailure { onMessage(context.getString(R.string.scrobble_export_share_failed)) }
    }

    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
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
                onSelect = { csvSelected = it == 0 },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.save),
            emphasised = true,
            contentColor = MaterialTheme.colorScheme.primary,
            onClick = {
                if (csvSelected) {
                    csvSaveLauncher.launch(LocalPlaylistExport.fileName(playlist.title, "csv"))
                } else {
                    m3uSaveLauncher.launch(LocalPlaylistExport.fileName(playlist.title, "m3u"))
                }
            },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.share),
            emphasised = false,
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = { share(csvSelected); onDismiss() },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = onDismiss,
        )
    }
}
