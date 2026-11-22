package com.music.bitchord.ui.scrobble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.bitchord.data.model.Song
import dev.chrisbanes.haze.HazeState

/**
 * The Scrobble page's Generate and Playlists tabs.
 *
 * The Stats tab is Mylo's own Replay screen, which the caller renders, and
 * the tab bar is the app's navigation bar, swapped to the Scrobble tabs while
 * this page is open. [tab] is 1 for Generate and 2 for Playlists.
 *
 * Leaving the page cancels any running generation.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScrobbleHost(
    tab: Int,
    currentSong: Song?,
    isPlaying: Boolean,
    reduceMotion: Boolean,
    contentPadding: PaddingValues,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onTabChange: (Int) -> Unit,
) {
    val generateViewModel: ScrobbleGenerateViewModel = viewModel()
    val hazeState = remember { HazeState() }

    DisposableEffect(Unit) {
        onDispose { generateViewModel.cancelGeneration() }
    }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = true) {
            when (tab) {
                1 -> ScrobbleGenerateContent(
                    viewModel = generateViewModel,
                    currentSong = currentSong,
                    contentPadding = contentPadding,
                    highlightedPlaylistId = null,
                    sharedHandoffFromGenerate = false,
                    reduceMotion = reduceMotion,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedVisibility,
                    onOpenPlaylists = { onTabChange(2) },
                )
                2 -> ScrobblePlaylistsContent(
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
                    onOpenGenerator = { onTabChange(1) },
                )
            }
        }
    }
}
