package com.music.bitchord.ui.scrobble

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.ScrobbleEmptyState
import com.music.bitchord.ui.components.ScrobbleErrorState
import com.music.bitchord.ui.replay.ReplayStoryPage
import com.music.bitchord.ui.scrobble.stats.StatsHero
import com.music.bitchord.ui.scrobble.stats.StatsRankingsSkeleton
import com.music.bitchord.ui.scrobble.stats.StatsReplayEntry
import com.music.bitchord.ui.scrobble.stats.statsArtistItems
import com.music.bitchord.ui.scrobble.stats.statsPatternItems
import com.music.bitchord.ui.scrobble.stats.statsTrackItems

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScrobbleStatisticsContent(
    viewModel: ScrobbleStatisticsViewModel,
    reduceMotion: Boolean,
    onPlaySong: (Song) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReplay: (ReplayStoryPage, ReplayPeriod) -> Unit,
    onOpenArtist: (String?, String) -> Unit,
    contentPadding: PaddingValues,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
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
        PullToRefresh(
            refreshing = refreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "statistics-header") {
                    Text(
                        stringResource(R.string.scrobble_title_stats),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = contentPadding.calculateTopPadding() + 8.dp,
                                bottom = 4.dp,
                            )
                            .semantics { heading() },
                        style = MaterialTheme.typography.displayLarge,
                    )
                }

                item(key = "statistics-hero") {
                    StatsHero(
                        period = state.period,
                        onPeriodSelect = viewModel::setPeriod,
                        summary = state.localSummary,
                        plays = state.localSummary?.totalPlays
                            ?: state.tracks.sumOf { it.plays }.toInt(),
                        loading = state.isLoading,
                        reduceMotion = reduceMotion,
                    )
                }

                if (state.loadedPeriod != null && state.loadedPeriod != state.period) {
                    item(key = "statistics-period-loading") {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            color = scrobbleAccentColor(),
                        )
                    }
                }

                if (state.error != null) {
                    item(key = "statistics-error") {
                        StatisticsRetryNotice(
                            message = state.error.orEmpty(),
                            onRetry = viewModel::refresh,
                        )
                    }
                }

                if (state.isLoading) {
                    item(key = "statistics-skeleton") {
                        StatsRankingsSkeleton(reduceMotion = reduceMotion)
                    }
                } else if (state.tracks.isEmpty()) {
                    // A failure is shown as the error notice above, never as "no history".
                    if (state.error == null) {
                        item(key = "empty") {
                            StatisticsEmptyState(
                                lastFmConnected = state.lastFmConnected,
                                onOpenSettings = onOpenSettings,
                            )
                        }
                    }
                } else {
                    val localSummary = state.localSummary
                    if (localSummary != null && !localSummary.isEmpty) {
                        item(key = "replay-entry") {
                            StatsReplayEntry(
                                onClick = {
                                    onOpenReplay(ReplayStoryPage.INTRO, state.loadedPeriod ?: state.period)
                                },
                            )
                        }
                    }
                    statsArtistItems(
                        artists = localSummary?.artists.orEmpty().take(9),
                        onArtistClick = { artist -> onOpenArtist(artist.browseId, artist.title) },
                    )
                    statsTrackItems(
                        tracks = state.tracks,
                        onPlaySong = onPlaySong,
                    )
                    if (localSummary != null && !localSummary.isEmpty) {
                        statsPatternItems(localSummary)
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
    ScrobbleErrorState(
        message = message,
        onRetry = onRetry,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun StatisticsEmptyState(
    lastFmConnected: Boolean,
    onOpenSettings: () -> Unit,
) {
    ScrobbleEmptyState(
        title = stringResource(
            if (lastFmConnected) R.string.scrobble_stats_empty_title_period else R.string.scrobble_stats_empty_title,
        ),
        message = stringResource(
            if (lastFmConnected) R.string.scrobble_stats_empty_body_period else R.string.scrobble_stats_empty_body,
        ),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 48.dp),
        action = if (!lastFmConnected) {
            {
                Button(onClick = onOpenSettings, colors = scrobblePrimaryButtonColors()) {
                    Text(stringResource(R.string.scrobble_open_settings))
                }
            }
        } else {
            null
        },
    )
}
