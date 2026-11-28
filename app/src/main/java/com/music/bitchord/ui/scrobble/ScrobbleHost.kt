package com.music.bitchord.ui.scrobble

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.components.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState

/** How long a tab switch takes to cross-fade, and how far it drifts as it does. */
private const val ScrobbleTabFadeMillis = 220
private const val ScrobbleTabDrift = 0.06f

/**
 * The Scrobble page's Generate and Playlists tabs.
 *
 * The Stats tab is the Last.fm account's statistics, which the caller renders, and
 * the tab bar is the app's navigation bar, swapped to the Scrobble tabs while
 * this page is open. [tab] is 1 for Generate and 2 for Playlists.
 *
 * Closing the page cancels any running generation, in the caller.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScrobbleHost(
    tab: Int,
    highlightedPlaylistId: String?,
    sharedHandoffFromGenerate: Boolean,
    currentSong: Song?,
    isPlaying: Boolean,
    reduceMotion: Boolean,
    contentPadding: PaddingValues,
    pageBackdrop: Backdrop? = null,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onTabChange: (Int) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val generateViewModel: ScrobbleGenerateViewModel = viewModel()
    val hazeState = remember { HazeState() }
    val effectiveReduceMotion = scrobbleReduceMotion(reduceMotion)
    // Each tab keeps its own state while it is off screen: its scroll position,
    // its search query, its sort. Swapping the `when` below disposes one tab,
    // which would otherwise drop all of that and land the other at the top.
    val tabStates = rememberSaveableStateHolder()

    CompositionLocalProvider(LocalScrobbleReduceMotion provides effectiveReduceMotion) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            ScrobbleTabSwitcher(
                tab = tab,
                reduceMotion = effectiveReduceMotion,
                stateHolder = tabStates,
            ) { current ->
                when (current) {
                    1 -> ScrobbleGenerateContent(
                        viewModel = generateViewModel,
                        currentSong = currentSong,
                        contentPadding = contentPadding,
                        highlightedPlaylistId = highlightedPlaylistId,
                        sharedHandoffFromGenerate = sharedHandoffFromGenerate,
                        reduceMotion = effectiveReduceMotion,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        onOpenPlaylists = { onTabChange(2) },
                        onOpenSettings = onOpenSettings,
                    )
                    2 -> ScrobblePlaylistsContent(
                        viewModel = generateViewModel,
                        hazeState = hazeState,
                        pageBackdrop = pageBackdrop,
                        contentPadding = contentPadding,
                        highlightedPlaylistId = highlightedPlaylistId,
                        sharedHandoffFromGenerate = sharedHandoffFromGenerate,
                        reduceMotion = effectiveReduceMotion,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onPlaySongs = onPlaySongs,
                        onOpenGenerator = { onTabChange(1) },
                    )
                }
            }
        }
    }
}

/**
 * Swaps [tab]'s content with a cross-fade and a short drift in the direction of
 * the move, and keeps each tab's saved state across the swap.
 *
 * The content receives an [AnimatedContentScope], which is an
 * [androidx.compose.animation.AnimatedVisibilityScope]: the shared element the
 * Generate result hands to Playlists needs one, and this is now a real
 * transition rather than a wrapper around a constant. Because both tabs stay
 * composed for its duration, that hand-off has a source to fly from.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ScrobbleTabSwitcher(
    tab: Int,
    reduceMotion: Boolean,
    stateHolder: SaveableStateHolder,
    content: @Composable AnimatedContentScope.(Int) -> Unit,
) {
    AnimatedContent(
        targetState = tab,
        transitionSpec = {
            if (reduceMotion) {
                // A short fade, not a cut: the app's "Reduce animation" keeps
                // movement out but leaves the change readable.
                fadeIn(tween(ScrobbleTabFadeMillis / 3)) togetherWith
                    fadeOut(tween(ScrobbleTabFadeMillis / 3))
            } else {
                val forward = targetState > initialState
                (
                    fadeIn(tween(ScrobbleTabFadeMillis)) +
                        slideInHorizontally(tween(ScrobbleTabFadeMillis)) { width ->
                            (width * ScrobbleTabDrift * if (forward) 1 else -1).toInt()
                        }
                    ) togetherWith fadeOut(tween(ScrobbleTabFadeMillis / 2))
            }
        },
        label = "Scrobble tabs",
    ) { current ->
        stateHolder.SaveableStateProvider(current) {
            content(current)
        }
    }
}
