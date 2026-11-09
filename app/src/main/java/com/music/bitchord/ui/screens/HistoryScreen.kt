package com.music.bitchord.ui.screens

import com.music.bitchord.R
import com.music.bitchord.data.ListeningHistory

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import com.music.bitchord.data.innertube.InnertubeParser.HistorySection
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.playingRowMatcher
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PlayingAccent
import com.music.bitchord.ui.components.ROW_DIVIDER_INSET
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.songListSkeleton

/**
 * What the account has been listening to, most recent first.
 *
 * A plain list rather than a page with a header: no single cover stands for a
 * history, and borrowing one — the newest track's, say — would claim the page
 * belonged to it.
 *
 * Tapping a row plays it with the rest of the history behind it, so the list
 * doubles as a queue that has already been approved once. That is the useful
 * reading of a history: not a receipt, but everything worth playing again.
 */
@Composable
fun HistoryScreen(
    state: UiState<List<Song>>,
    sections: List<HistorySection>,
    isLocal: Boolean,
    onSelectLocal: () -> Unit,
    onSelectRemote: () -> Unit,
    onClearLocalHistory: () -> Unit,
    listState: LazyListState,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadMoreFailed: Boolean,
    contentPadding: PaddingValues,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val songs = (state as? UiState.Success)?.data.orEmpty()
    val recordingEnabled by ListeningHistory.recordingEnabled.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(listState, songs.size, hasMore, loadMoreFailed, state is UiState.Success) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                if (state is UiState.Success && hasMore && !loadMoreFailed && songs.isNotEmpty() && lastVisible >= songs.lastIndex - 4) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "history:source-switch") {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onSelectLocal) {
                    androidx.compose.material3.Text(if (isLocal) "✓ On this device" else "On this device")
                }
                TextButton(onClick = onSelectRemote) {
                    androidx.compose.material3.Text(if (!isLocal) "✓ YouTube Music" else "YouTube Music")
                }
                if (isLocal) {
                    TextButton(onClick = { ListeningHistory.setRecordingEnabled(!recordingEnabled) }) {
                        androidx.compose.material3.Text(if (recordingEnabled) "Pause recording" else "Resume recording")
                    }
                    if (songs.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            androidx.compose.material3.Text("Clear")
                        }
                    }
                }
            }
        }
        if (state is UiState.Success && songs.isNotEmpty()) {
            item(key = "history:search") {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    singleLine = true,
                    placeholder = { androidx.compose.material3.Text("Search history") },
                    trailingIcon = {
                        if (query.isNotEmpty()) TextButton(onClick = { query = "" }) {
                            androidx.compose.material3.Text("Clear")
                        }
                    },
                )
            }
        }
        when (state) {
            is UiState.Loading -> songListSkeleton(count = 10, keyPrefix = "skeleton:history")

            is UiState.Error -> item(key = "history:message") {
                MessageState(
                    message = state.message,
                    actionLabel = stringResource(R.string.try_again),
                    onAction = onRetry,
                )
            }

            is UiState.Success -> {
                val allSongs = state.data
                val sourceSections = sections.ifEmpty { listOf(HistorySection("", allSongs)) }
                val displaySections = if (query.isBlank()) sourceSections else sourceSections.mapNotNull { section ->
                    val matches = section.songs.filter { song ->
                        song.title.contains(query, ignoreCase = true) ||
                            song.artist.contains(query, ignoreCase = true)
                    }
                    matches.takeIf { it.isNotEmpty() }?.let { section.copy(songs = it) }
                }
                val visibleSongs = displaySections.flatMap { it.songs }
                val isCurrentRow = playingRowMatcher(visibleSongs, currentSong)
                // The videoId alone is not a key here: the feed is deduplicated
                // on it, but a list keyed on something that could repeat is one
                // bad response away from a crash. The index makes it total.
                var offset = 0
                displaySections.forEachIndexed { sectionIndex, section ->
                    if (section.title.isNotBlank()) {
                        item(key = "history:section:$sectionIndex") {
                            androidx.compose.material3.Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                    val sectionOffset = offset
                    itemsIndexed(section.songs, keyPrefix = "history:$sectionIndex:") { index, song ->
                        val absoluteIndex = sectionOffset + index
                        SongRow(
                            song = song,
                            onClick = { onSongClick(visibleSongs, absoluteIndex) },
                            onLongPress = { onSongLongPress(song) },
                            onSwipeToQueue = { onSongSwipe(song) },
                            isCurrent = isCurrentRow(song),
                            isPlaying = isCurrentRow(song) && isPlaying,
                            searchPlayingStyle = true,
                            activeTint = PlayingAccent,
                        )
                        if (absoluteIndex < visibleSongs.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                    offset += section.songs.size
                }
                if (hasMore || loadingMore) {
                    item(key = "history:load-more") {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            when {
                                loadingMore -> CircularProgressIndicator()
                                loadMoreFailed -> TextButton(onClick = onLoadMore) {
                                    androidx.compose.material3.Text(stringResource(R.string.try_again))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { androidx.compose.material3.Text("Clear device history?") },
            text = { androidx.compose.material3.Text("This removes listening history stored on this device. Your YouTube Music history is not changed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearLocalHistory()
                }) { androidx.compose.material3.Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            },
        )
    }
}

/** Keyed on both, for the reason given at the call site. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    songs: List<Song>,
    keyPrefix: String = "",
    row: @Composable (Int, Song) -> Unit,
) = items(
    count = songs.size,
    key = { "$keyPrefix${songs[it].videoId}:$it" },
) { index -> row(index, songs[index]) }
