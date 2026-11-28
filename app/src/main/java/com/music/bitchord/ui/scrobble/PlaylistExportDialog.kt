package com.music.bitchord.ui.scrobble

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.music.bitchord.R
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistExport
import com.music.bitchord.ui.components.AlertAction
import com.music.bitchord.ui.components.AlertRule
import com.music.bitchord.ui.components.AlertScaffold
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import java.io.File

/** Export one playlist as CSV or M3U: one format choice, one filled Save, Share and Cancel as text. */
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormatOption(
                    label = "CSV",
                    selected = csvSelected,
                    onSelect = { csvSelected = true },
                    modifier = Modifier.weight(1f),
                )
                FormatOption(
                    label = "M3U",
                    selected = !csvSelected,
                    onSelect = { csvSelected = false },
                    modifier = Modifier.weight(1f),
                )
            }
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

/** One segment of the format choice. Selection shows a check, not colour alone. */
@Composable
private fun FormatOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.secondaryContainer else colors.surfaceVariant,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
