package com.music.bitchord.ui.scrobble

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.generate.PlaylistGenerationMode
import com.music.bitchord.data.generate.PlaylistGenerationRequest
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.data.stats.ReplayPeriod
import kotlinx.coroutines.delay

@Composable
fun ScrobbleGenerateContent(
    viewModel: ScrobbleGenerateViewModel,
    currentSong: Song?,
    contentPadding: PaddingValues,
    onOpenPlaylists: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(PlaylistGenerationMode.TOP) }
    var count by rememberSaveable { mutableStateOf(25f) }
    var period by rememberSaveable { mutableStateOf(ReplayPeriod.ALL_TIME) }
    var seedQuery by rememberSaveable { mutableStateOf("") }
    var selectedSeed by remember { mutableStateOf<Song?>(null) }
    var artist by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf("") }
    var excludeSaved by rememberSaveable { mutableStateOf(false) }
    var excludeLiked by rememberSaveable { mutableStateOf(false) }
    var excludeLibrary by rememberSaveable { mutableStateOf(false) }
    var seedResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var seedSearching by remember { mutableStateOf(false) }

    LaunchedEffect(seedQuery, mode) {
        if (mode != PlaylistGenerationMode.SIMILAR_TRACKS || seedQuery.trim().length < 2) {
            seedResults = emptyList()
            seedSearching = false
            return@LaunchedEffect
        }
        delay(300)
        seedSearching = true
        seedResults = YtMusicRepository.search(seedQuery.trim(), SearchFilter.SONGS)
            .getOrNull()
            .orEmpty()
            .mapNotNull { result ->
                when (result) {
                    is SearchResult.TopTrack -> result.song
                    is SearchResult.Track -> result.song
                    is SearchResult.Browse -> null
                }
            }
            .distinctBy { it.videoId }
            .take(6)
        seedSearching = false
    }

    val modeDescription = when (mode) {
        PlaylistGenerationMode.TOP -> "Your most-played tracks from Mylo Replay"
        PlaylistGenerationMode.RECENT -> "Your latest tracks recorded on this device"
        PlaylistGenerationMode.SIMILAR_TRACKS -> "YouTube Music radio built from one track"
        PlaylistGenerationMode.SIMILAR_ARTISTS -> "Tracks from the artist you choose"
        PlaylistGenerationMode.TAG -> "A mood or genre from YouTube Music"
        PlaylistGenerationMode.MIX -> "A blend of radio, Quick Picks and Home recommendations"
        PlaylistGenerationMode.RECOMMENDATIONS -> "Your current YouTube Music Quick Picks"
        PlaylistGenerationMode.NEVER_HEARD -> "Recommendations filtered against your actual listening history"
        PlaylistGenerationMode.LIBRARY -> "Tracks saved or liked in your YouTube Music library"
    }
    val canGenerate = !uiState.isGenerating && when (mode) {
        PlaylistGenerationMode.SIMILAR_TRACKS -> selectedSeed?.videoId?.isNotBlank() == true
        PlaylistGenerationMode.SIMILAR_ARTISTS -> artist.isNotBlank()
        PlaylistGenerationMode.TAG -> tag.isNotBlank()
        else -> true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Generate", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Build a playlist from your listening and YouTube Music.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Choose a mix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlaylistGenerationMode.entries.forEach { option ->
                    FilterChip(
                        selected = mode == option,
                        onClick = { mode = option },
                        label = { Text(option.label()) },
                    )
                }
            }
            Text(modeDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (mode == PlaylistGenerationMode.TOP) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Listening period", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(ReplayPeriod.ALL_TIME, ReplayPeriod.THIS_YEAR, ReplayPeriod.THIS_MONTH, ReplayPeriod.THIS_WEEK).forEach { option ->
                        FilterChip(
                            selected = period == option,
                            onClick = { period = option },
                            label = { Text(option.label()) },
                        )
                    }
                }
            }
        }

        if (mode == PlaylistGenerationMode.SIMILAR_TRACKS || mode == PlaylistGenerationMode.MIX) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Seed track", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                currentSong?.let { playing ->
                    OutlinedButton(
                        onClick = { selectedSeed = playing },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (selectedSeed?.videoId == playing.videoId) "Using now playing: ${playing.title}"
                            else "Use now playing: ${playing.title}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (mode == PlaylistGenerationMode.SIMILAR_TRACKS) {
                    OutlinedTextField(
                        value = seedQuery,
                        onValueChange = { seedQuery = it; selectedSeed = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search for a track") },
                    )
                    if (seedSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
                    seedResults.forEach { song ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (selectedSeed?.videoId == song.videoId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .clickable { selectedSeed = song }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(song.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    selectedSeed?.let { Text("Selected: ${it.title} · ${it.artist}", color = MaterialTheme.colorScheme.primary) }
                } else {
                    Text(
                        selectedSeed?.let { "Seed: ${it.title}" } ?: "Optional — a seed adds a radio source to your mix",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (mode == PlaylistGenerationMode.SIMILAR_ARTISTS) {
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Artist name") },
            )
        }
        if (mode == PlaylistGenerationMode.TAG) {
            OutlinedTextField(
                value = tag,
                onValueChange = { tag = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Mood or genre") },
                placeholder = { Text("e.g. indie, jazz, ambient") },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Playlist size", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(count.toInt().toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Slider(value = count, onValueChange = { count = it }, valueRange = 5f..35f, steps = 29)
        }

        if (mode == PlaylistGenerationMode.NEVER_HEARD) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Optional exclusions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ExclusionToggle("Tracks in saved playlists", excludeSaved) { excludeSaved = it }
                ExclusionToggle("Liked tracks", excludeLiked) { excludeLiked = it }
                ExclusionToggle("Library tracks", excludeLibrary) { excludeLibrary = it }
                Text(
                    "By default, only tracks actually heard are excluded. These extra filters are opt-in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (uiState.isGenerating) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(uiState.progress.ifBlank { "Building your playlist…" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = viewModel::cancelGeneration) { Text("Cancel") }
            }
        } else {
            Button(
                onClick = {
                    viewModel.generate(
                        PlaylistGenerationRequest(
                            mode = mode,
                            count = count.toInt(),
                            period = period,
                            seedTrack = selectedSeed,
                            seedArtist = artist,
                            tag = tag,
                            excludeSavedPlaylists = excludeSaved,
                            excludeLiked = excludeLiked,
                            excludeLibrary = excludeLibrary,
                        ),
                    )
                },
                enabled = canGenerate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Generate playlist")
            }
        }

        uiState.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        uiState.savedPlaylist?.let { playlist ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Playlist saved", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${playlist.title} · ${playlist.songs.size} tracks")
                Button(onClick = onOpenPlaylists) { Text("View playlists") }
            }
        }
    }
}

@Composable
private fun ExclusionToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun PlaylistGenerationMode.label(): String = when (this) {
    PlaylistGenerationMode.TOP -> "Top"
    PlaylistGenerationMode.RECENT -> "Recent"
    PlaylistGenerationMode.SIMILAR_TRACKS -> "Similar tracks"
    PlaylistGenerationMode.SIMILAR_ARTISTS -> "Artist mix"
    PlaylistGenerationMode.TAG -> "By tag"
    PlaylistGenerationMode.MIX -> "Taste mix"
    PlaylistGenerationMode.RECOMMENDATIONS -> "For you"
    PlaylistGenerationMode.NEVER_HEARD -> "Never heard"
    PlaylistGenerationMode.LIBRARY -> "Library"
}

private fun ReplayPeriod.label(): String = when (this) {
    ReplayPeriod.ALL_TIME -> "All time"
    ReplayPeriod.THIS_YEAR -> "This year"
    ReplayPeriod.THIS_MONTH -> "This month"
    ReplayPeriod.THIS_WEEK -> "This week"
    else -> name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
}

@Composable
fun ScrobblePlaylistsContent(
    contentPadding: PaddingValues,
    onPlaySongs: (List<Song>, Int) -> Unit,
) {
    val playlists by LocalPlaylistStore.playlists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var coverPickerPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val playlistId = coverPickerPlaylistId
        if (uri != null && playlistId != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (!LocalPlaylistStore.setCustomCover(playlistId, uri.toString())) {
                    context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    Toast.makeText(context, "Playlist no longer exists", Toast.LENGTH_SHORT).show()
                }
            } catch (error: SecurityException) {
                Toast.makeText(context, "Couldn't access that image", Toast.LENGTH_SHORT).show()
            } catch (error: IllegalArgumentException) {
                Toast.makeText(context, "Couldn't access that image", Toast.LENGTH_SHORT).show()
            }
        }
        coverPickerPlaylistId = null
    }
    val openCoverPicker: (String) -> Unit = { playlistId ->
        coverPickerPlaylistId = playlistId
        coverPicker.launch(arrayOf("image/*"))
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<LocalPlaylist?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<LocalPlaylist?>(null) }

    val selected = playlists.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) { selectedId = null }

    if (selected != null) {
        PlaylistDetailContent(
            playlist = selected,
            contentPadding = contentPadding,
            onBack = { selectedId = null },
            onPlaySongs = onPlaySongs,
            onRename = {
                renameText = selected.title
                renameError = null
                renameTarget = selected
            },
            onDelete = { deleteTarget = selected },
            onChangeCover = { openCoverPicker(selected.id) },
        )
    } else {
        val visible = playlists
            .sortedWith(compareByDescending<LocalPlaylist> { it.isPinned }.thenByDescending { it.createdAtEpochMillis })
            .filter { it.title.contains(search, ignoreCase = true) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Playlists", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Your generated and saved mixes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (playlists.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Find a playlist") },
                    )
                }
            }
            if (visible.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(if (playlists.isEmpty()) "No playlists yet" else "No matching playlists", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (playlists.isEmpty()) "Generate a mix and it will be saved here." else "Try another playlist name.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(visible, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onOpen = { selectedId = playlist.id },
                        onPlay = { onPlaySongs(playlist.songs, 0) },
                        onPin = { LocalPlaylistStore.setPinned(playlist.id, !playlist.isPinned) },
                        onRename = {
                            renameText = playlist.title
                            renameError = null
                            renameTarget = playlist
                        },
                        onDelete = { deleteTarget = playlist },
                        onChangeCover = { openCoverPicker(playlist.id) },
                        onResetCover = { LocalPlaylistStore.setCustomCover(playlist.id, null) },
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it; renameError = null }, singleLine = true)
                    renameError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = renameText.trim()
                    val duplicate = playlists.any { it.id != target.id && it.title.equals(clean, ignoreCase = true) }
                    when {
                        clean.isBlank() -> renameError = "Enter a playlist name"
                        duplicate -> renameError = "A playlist with that name already exists"
                        else -> {
                            LocalPlaylistStore.renamePlaylist(target.id, clean)
                            renameTarget = null
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete playlist?") },
            text = { Text("${target.title} will be removed from this device. Listening history is not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    LocalPlaylistStore.deletePlaylist(target.id)
                    if (selectedId == target.id) selectedId = null
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: LocalPlaylist,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onChangeCover: () -> Unit,
    onResetCover: () -> Unit,
) {
    var menuExpanded by remember(playlist.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = playlist.customCoverUri ?: playlist.songs.firstOrNull()?.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen),
        )
        Column(
            Modifier.weight(1f).clickable(onClick = onOpen).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(playlist.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlist.songs.size} tracks${if (playlist.isPinned) " · Pinned" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onPlay, enabled = playlist.songs.isNotEmpty()) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play playlist")
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist actions")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(if (playlist.isPinned) "Unpin" else "Pin") },
                    leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                    onClick = { menuExpanded = false; onPin() },
                )
                DropdownMenuItem(
                    text = { Text("Change cover") },
                    leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
                    onClick = { menuExpanded = false; onChangeCover() },
                )
                if (playlist.customCoverUri != null) {
                    DropdownMenuItem(
                        text = { Text("Reset cover") },
                        onClick = { menuExpanded = false; onResetCover() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                    onClick = { menuExpanded = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun PlaylistDetailContent(
    playlist: LocalPlaylist,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onChangeCover: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val coverModel = playlist.customCoverUri ?: playlist.songs.firstOrNull()?.thumbnailUrl
            if (coverModel != null) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = playlist.title + " cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(18.dp)),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onChangeCover) { Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Change playlist cover") }
                IconButton(onClick = onRename) { Icon(Icons.Rounded.Edit, contentDescription = "Rename playlist") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete playlist") }
            }
            Text(playlist.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("${playlist.songs.size} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPlaySongs(playlist.songs, 0) }, enabled = playlist.songs.isNotEmpty()) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play")
                }
                OutlinedButton(onClick = { onPlaySongs(playlist.songs.shuffled(), 0) }, enabled = playlist.songs.isNotEmpty()) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Shuffle")
                }
            }
        }
        if (playlist.songs.isEmpty()) {
            item { Text("This playlist is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 28.dp)) }
        } else {
            itemsIndexed(playlist.songs, key = { index, song -> "${playlist.id}-${song.videoId}-$index" }) { index, song ->
                var trackMenuExpanded by remember(playlist.id, song.videoId, index) { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { onPlaySongs(playlist.songs, index) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(model = song.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(7.dp)))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                                enabled = index > 0,
                                onClick = {
                                    trackMenuExpanded = false
                                    val updated = playlist.songs.toMutableList().apply { add(index - 1, removeAt(index)) }
                                    LocalPlaylistStore.updatePlaylistSongs(playlist.id, updated)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move down") },
                                leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                                enabled = index < playlist.songs.lastIndex,
                                onClick = {
                                    trackMenuExpanded = false
                                    val updated = playlist.songs.toMutableList().apply { add(index + 1, removeAt(index)) }
                                    LocalPlaylistStore.updatePlaylistSongs(playlist.id, updated)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove from playlist") },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    trackMenuExpanded = false
                                    LocalPlaylistStore.updatePlaylistSongs(
                                        playlist.id,
                                        playlist.songs.filterIndexed { songIndex, _ -> songIndex != index },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
