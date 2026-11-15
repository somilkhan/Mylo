package com.music.bitchord.ui.screens.scrobble

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class ScrobbleHubTab(val label: String) {
    STATS("Stats"), GENERATE("Generate"), PLAYLISTS("Playlists"),
}

@Composable
internal fun ScrobbleHubScreen(
    modifier: Modifier = Modifier,
    onPlay: (List<Song>, Int) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ScrobbleHubTab.STATS.name) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { ScrobblePlaylistStore.init(context) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScrobbleHubTab.entries.forEach { item ->
                FilterChip(
                    selected = tab == item.name,
                    onClick = { tab = item.name },
                    label = { Text(item.label) },
                )
            }
        }
        when (tab) {
            ScrobbleHubTab.GENERATE.name -> ScrobbleGeneratorScreen(onPlay = onPlay)
            ScrobbleHubTab.PLAYLISTS.name -> ScrobblePlaylistsScreen(onPlay = onPlay)
            else -> ScrobbleStatsScreen(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScrobbleGeneratorScreen(
    onPlay: (List<Song>, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val savedPlaylists by ScrobblePlaylistStore.playlists.collectAsState()
    var mode by rememberSaveable { mutableStateOf(ScrobbleGenerateMode.RECOMMENDATIONS.name) }
    var count by rememberSaveable { mutableStateOf(20f) }
    var artist by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf("") }
    var seedTrack by remember { mutableStateOf<Song?>(null) }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    val selectedMode = ScrobbleGenerateMode.valueOf(mode)
    val needsArtist = selectedMode == ScrobbleGenerateMode.SIMILAR_ARTISTS
    val needsTag = selectedMode == ScrobbleGenerateMode.TAG
    val needsSeed = selectedMode == ScrobbleGenerateMode.SIMILAR_TRACKS

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Build a playlist around your taste", style = MaterialTheme.typography.titleLarge)
            Text(
                "Discovery uses your listening history and, when connected, Last.fm recommendations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text("DISCOVERY MODE", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            val modes = listOf(
                ScrobbleGenerateMode.RECOMMENDATIONS,
                ScrobbleGenerateMode.NEVER_HEARD,
                ScrobbleGenerateMode.MIX,
                ScrobbleGenerateMode.TOP,
                ScrobbleGenerateMode.LIBRARY,
                ScrobbleGenerateMode.RECENT,
                ScrobbleGenerateMode.SIMILAR_TRACKS,
                ScrobbleGenerateMode.SIMILAR_ARTISTS,
                ScrobbleGenerateMode.TAG,
            )
            modes.chunked(2).forEach { group ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.forEach { option ->
                        FilterChip(
                            selected = mode == option.name,
                            onClick = { mode = option.name; error = null },
                            label = { Text(option.name.replace('_', ' ').lowercase()
                                .replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        }
        if (needsArtist) item {
            OutlinedTextField(
                value = artist, onValueChange = { artist = it },
                label = { Text("Artist name") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        if (needsTag) item {
            OutlinedTextField(
                value = tag, onValueChange = { tag = it },
                label = { Text("Genre or tag") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        if (needsSeed) item {
            Text(
                seedTrack?.let { "Seed: ${it.title} — ${it.artist}" }
                    ?: "Generate a playlist first, then choose a result as the seed track.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (seedTrack != null) TextButton(onClick = { seedTrack = null }) { Text("Clear seed") }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Tracks")
                Text(count.toInt().toString())
            }
            Slider(value = count, onValueChange = { count = it }, valueRange = 5f..35f, steps = 5)
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        savedMessage = null
                        results = emptyList()
                        try {
                            results = ScrobbleGeneratorRepository.generate(
                                mode = selectedMode,
                                count = count.toInt(),
                                seedTrack = seedTrack,
                                seedArtist = artist,
                                tagInput = tag,
                                onProgress = { progress = it },
                            )
                            if (results.isEmpty()) error = "No playable tracks found. Try another mode or connect Last.fm."
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message ?: "Generation failed"
                        } finally {
                            loading = false
                            progress = ""
                        }
                    }
                },
                enabled = !loading && (!needsArtist || artist.isNotBlank()) &&
                    (!needsTag || tag.isNotBlank()) && (!needsSeed || seedTrack != null),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) CircularProgressIndicator()
                else Text("Generate playlist")
            }
            if (loading) Text(progress, style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (results.isNotEmpty()) {
            item {
                Text("${results.size} tracks found", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPlay(results, 0) }) { Text("Play all") }
                    OutlinedButton(onClick = {
                        seedTrack = results.firstOrNull()
                        mode = ScrobbleGenerateMode.SIMILAR_TRACKS.name
                    }) { Text("Similar to first") }
                }
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val saved = ScrobblePlaylistStore.create(
                                    title = playlistName,
                                    subtitle = "Generated with ${selectedMode.name.lowercase().replace('_', ' ')}",
                                    tracks = results,
                                )
                                savedMessage = "Saved “${saved.title}” to Playlists"
                                playlistName = ""
                            } catch (failure: Exception) {
                                error = failure.message ?: "Couldn't save playlist"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save to Playlists") }
                savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                if (savedPlaylists.isNotEmpty()) {
                    Text("Add to an existing playlist", style = MaterialTheme.typography.labelMedium)
                    savedPlaylists.forEach { playlist ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        ScrobblePlaylistStore.addTracks(playlist.id, results)
                                        savedMessage = "Added tracks to “${playlist.title}”"
                                    } catch (failure: Exception) {
                                        error = failure.message ?: "Couldn't update playlist"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(playlist.title) }
                    }
                }
            }
            items(results, key = { it.videoId }) { song ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onPlay(results, results.indexOf(song)) }) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(song.title, style = MaterialTheme.typography.bodyLarge)
                        Text(song.artist, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrobblePlaylistsScreen(
    onPlay: (List<Song>, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val playlists by ScrobblePlaylistStore.playlists.collectAsState()
    var renameTarget by remember { mutableStateOf<SavedScrobblePlaylist?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(
                    value = renameText, onValueChange = { renameText = it },
                    singleLine = true, label = { Text("Playlist name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget ?: return@TextButton
                    scope.launch {
                        try {
                            ScrobblePlaylistStore.rename(target.id, renameText)
                            message = "Playlist renamed"
                            renameTarget = null
                        } catch (failure: Exception) {
                            message = failure.message
                        }
                    }
                }, enabled = renameText.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    if (playlists.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("No saved playlists yet", style = MaterialTheme.typography.titleLarge)
            Text("Generate a mix and save it here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { message?.let { Text(it, color = MaterialTheme.colorScheme.primary) } }
        items(playlists, key = { it.id }) { playlist ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(playlist.title, style = MaterialTheme.typography.titleMedium)
                    Text("${playlist.tracks.size} tracks · ${playlist.subtitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var coverUri by remember(playlist.id) {
                        mutableStateOf(playlist.customCoverUri.orEmpty())
                    }
                    OutlinedTextField(
                        value = coverUri,
                        onValueChange = { coverUri = it },
                        label = { Text("Custom cover URI (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    ScrobblePlaylistStore.setCustomCover(
                                        playlist.id, coverUri.trim().takeIf { it.isNotEmpty() },
                                    )
                                    message = "Cover updated"
                                } catch (failure: Exception) { message = failure.message }
                            }
                        },
                    ) { Text("Save cover") }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onPlay(playlist.tracks, 0) },
                                enabled = playlist.tracks.isNotEmpty(),
                            ) { Text("Play") }
                            TextButton(onClick = {
                                renameText = playlist.title
                                renameTarget = playlist
                            }) { Text("Rename") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        ScrobblePlaylistStore.setPinned(playlist.id, !playlist.pinned)
                                    } catch (failure: Exception) { message = failure.message }
                                }
                            }) { Text(if (playlist.pinned) "Unpin" else "Pin") }
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        ScrobblePlaylistStore.delete(playlist.id)
                                        message = "Playlist deleted"
                                    } catch (failure: Exception) { message = failure.message }
                                }
                            }) { Text("Delete") }
                        }
                    }
                    playlist.tracks.take(5).forEachIndexed { index, song ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).clickable { onPlay(playlist.tracks, index) }) {
                                Text(song.title, style = MaterialTheme.typography.bodyMedium)
                                Text(song.artist, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(
                                enabled = index > 0,
                                onClick = {
                                    scope.launch {
                                        try { ScrobblePlaylistStore.moveTrack(playlist.id, index, index - 1) }
                                        catch (failure: Exception) { message = failure.message }
                                    }
                                },
                            ) { Text("↑") }
                            TextButton(
                                enabled = index < playlist.tracks.lastIndex,
                                onClick = {
                                    scope.launch {
                                        try { ScrobblePlaylistStore.moveTrack(playlist.id, index, index + 1) }
                                        catch (failure: Exception) { message = failure.message }
                                    }
                                },
                            ) { Text("↓") }
                            TextButton(onClick = {
                                scope.launch {
                                    try { ScrobblePlaylistStore.removeTrack(playlist.id, song.videoId) }
                                    catch (failure: Exception) { message = failure.message }
                                }
                            }) { Text("Remove") }
                        }
                    }
                    if (playlist.tracks.size > 5) {
                        Text("+ ${playlist.tracks.size - 5} more tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
