package com.music.bitchord.ui.scrobble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.bitchord.data.model.Song
import dev.chrisbanes.haze.HazeState

/**
 * The Scrobble page: Stats, Generate and Playlists, under one tab row.
 *
 * Opened as [com.music.bitchord.ui.navigation.AppRoute.Scrobble] from the
 * Scrobble FAB on Home, so back and the back stack are handled by the
 * navigator. Leaving the page cancels any running generation.
 */
@OptIn(ExperimentalSharedTransitionApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScrobbleHost(
    currentSong: Song?,
    isPlaying: Boolean,
    holder: String,
    reduceMotion: Boolean,
    contentPadding: PaddingValues,
    onPlaySong: (Song) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOpenReplay: () -> Unit,
    onOpenArtist: (String?, String) -> Unit,
    onOpenAlbum: (String?, String, String?, String?) -> Unit,
) {
    val generateViewModel: ScrobbleGenerateViewModel = viewModel()
    val statsViewModel: ScrobbleStatisticsViewModel = viewModel()
    val hazeState = remember { HazeState() }
    val pullState = rememberPullToRefreshState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { generateViewModel.cancelGeneration() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf("Stats", "Generate", "Playlists").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                )
            }
        }
        SharedTransitionLayout {
            AnimatedVisibility(visible = true) {
                when (tab) {
                    0 -> ScrobbleStatisticsContent(
                        viewModel = statsViewModel,
                        reduceMotion = reduceMotion,
                        onPlaySong = onPlaySong,
                        onOpenSettings = {},
                        holder = holder,
                        memberSince = null,
                        onOpenReplay = { _, _ -> onOpenReplay() },
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                        contentPadding = contentPadding,
                        refreshing = false,
                        onRefresh = {},
                        pullState = pullState,
                    )
                    1 -> ScrobbleGenerateContent(
                        viewModel = generateViewModel,
                        currentSong = currentSong,
                        contentPadding = contentPadding,
                        highlightedPlaylistId = null,
                        sharedHandoffFromGenerate = false,
                        reduceMotion = reduceMotion,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedVisibility,
                        onOpenPlaylists = { tab = 2 },
                    )
                    else -> ScrobblePlaylistsContent(
                        viewModel = generateViewModel,
                        hazeState = hazeState,
                        contentPadding = contentPadding,
                        highlightedPlaylistId = null,
                        sharedHandoffFromGenerate = false,
                        reduceMotion = reduceMotion,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedVisibility,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onPlaySongs = onPlaySongs,
                        onOpenGenerator = { tab = 1 },
                    )
                }
            }
        }
    }
}
