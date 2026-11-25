package com.music.bitchord.ui.scrobble

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistExport
import com.music.bitchord.ui.components.AlertAction
import com.music.bitchord.ui.components.AlertRule
import com.music.bitchord.ui.components.AlertScaffold
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.ScrobbleEmptyState
import com.music.bitchord.ui.components.ScrobbleErrorState
import com.music.bitchord.ui.components.ScrobbleLoadingRow
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import com.music.bitchord.data.stats.RankedEntry
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.ui.replay.ReplayCardRow
import com.music.bitchord.ui.replay.ReplayStoryPage
import com.music.bitchord.ui.replay.cards
import java.io.File
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScrobbleStatisticsContent(
    viewModel: ScrobbleStatisticsViewModel,
    reduceMotion: Boolean,
    onPlaySong: (Song) -> Unit,
    onOpenSettings: () -> Unit,
    holder: String,
    memberSince: String?,
    onOpenReplay: (ReplayStoryPage, ReplayPeriod) -> Unit,
    onOpenArtist: (String?, String) -> Unit,
    onOpenAlbum: (String?, String, String?, String?) -> Unit,
    contentPadding: PaddingValues,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
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

                item(key = "statistics-periods") {
                    // At large font scales the labels stop fitting in five equal slots,
                    // so the control scrolls sideways instead of clipping them.
                    val scrollPeriods = LocalDensity.current.fontScale >= 1.3f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(50),
                            )
                            .padding(4.dp)
                            .then(if (scrollPeriods) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                            .semantics { contentDescription = "Time period" }
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReplayPeriod.entries.forEach { period ->
                            val selected = state.period == period
                            Box(
                                modifier = Modifier
                                    .then(if (scrollPeriods) Modifier.defaultMinSize(minWidth = 96.dp) else Modifier.weight(1f))
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    )
                                    .semantics(mergeDescendants = true) {}
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { viewModel.setPeriod(period) },
                                    )
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = period.chip,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onTertiary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                if (state.loadedPeriod != null && state.loadedPeriod != state.period) {
                    item(key = "statistics-period-loading") {
                        androidx.compose.material3.LinearProgressIndicator(
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
                    items(5, key = { "skeleton-$it" }) {
                        StatisticsTrackSkeletonRow()
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
                    if (localSummary?.isEmpty == false) {
                        item(key = "replay-cards-heading") {
                            StatisticsSectionHeading(stringResource(R.string.your_replay))
                        }
                        item(key = "replay-cards") {
                            ReplayCardRow(
                                cards = localSummary.cards(context),
                                holder = holder,
                                memberSince = memberSince,
                                onCardClick = { page ->
                                    onOpenReplay(page, state.loadedPeriod ?: state.period)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                animateSummary = localSummary,
                                reduceMotion = reduceMotion,
                            )
                        }
                        item(key = "replay-action") {
                            TextButton(
                                onClick = {
                                    onOpenReplay(ReplayStoryPage.INTRO, state.loadedPeriod ?: state.period)
                                },
                                colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .heightIn(min = 48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.play_your_replay))
                            }
                        }
                    }

                    item(key = "concentration") {
                        TrackConcentrationCard(
                            trackPlays = state.tracks.map { it.plays },
                            localListeningMs = localSummary?.totalMs ?: 0L,
                            reduceMotion = reduceMotion,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    localSummary?.artists?.take(10)?.forEachIndexed { index, artist ->
                        if (index == 0) {
                            item(key = "top-artists-heading") {
                                StatisticsSectionHeading(stringResource(R.string.top_artists))
                            }
                        }
                        item(key = "top-artist-${artist.title.lowercase()}-${artist.browseId.orEmpty()}") {
                            ReplayInsightRow(
                                rank = index + 1,
                                entry = artist,
                                circular = true,
                                onClick = { onOpenArtist(artist.browseId, artist.title) },
                            )
                        }
                    }
                    localSummary?.albums?.take(10)?.forEachIndexed { index, album ->
                        if (index == 0) {
                            item(key = "top-albums-heading") {
                                StatisticsSectionHeading(stringResource(R.string.top_albums))
                            }
                        }
                        item(key = "top-album-${album.title.lowercase()}-${album.subtitle.orEmpty().lowercase()}-${album.browseId.orEmpty()}") {
                            ReplayInsightRow(
                                rank = index + 1,
                                entry = album,
                                circular = false,
                                onClick = { onOpenAlbum(album.browseId, album.title, album.subtitle, album.artworkUrl) },
                            )
                        }
                    }
                    item(key = "tracks-heading") {
                        StatisticsSectionHeading("Your tracks")
                    }
                    items(state.tracks, key = { it.identity }) { track ->
                        MergedStatisticsTrackRow(
                            track = track,
                            onPlaySong = onPlaySong,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsSectionHeading(text: String) {
    Text(
        text,
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun ReplayInsightRow(
    rank: Int,
    entry: RankedEntry,
    circular: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = if (circular) CircleShape else RoundedCornerShape(10.dp)
    val minutes = (entry.ms / 60_000L).coerceAtLeast(0L)
    val duration = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    val plays = stringResource(R.string.scrobble_plays_count, entry.plays)
    val summary = listOfNotNull(entry.subtitle, duration, plays).joinToString(" · ")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$rank. ${entry.title}, $summary" }
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceVariant,
        contentColor = colors.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                rank.toString(),
                modifier = Modifier.width(20.dp),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(shape)
                    .background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (!entry.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = entry.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        entry.title.take(1).uppercase(),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    entry.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Text(
                    summary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
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
        title = if (lastFmConnected) "No listening history" else "No listening history yet",
        message = if (lastFmConnected) {
            "No listening history for this period."
        } else {
            "Play a few tracks, or connect Last.fm in Settings to bring in your history."
        },
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

@Composable
private fun StatisticsTrackSkeletonRow() {
    ScrobbleLoadingRow(modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun MergedStatisticsTrackRow(
    track: MergedStatisticsTrack,
    onPlaySong: (Song) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val song = track.song
    val plays = stringResource(R.string.scrobble_plays_count, NumberFormat.getIntegerInstance().format(track.plays))
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .semantics(mergeDescendants = true) { contentDescription = "${track.title}, ${track.artist}, $plays" }

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (!track.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text("♫", color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    track.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Text(
                NumberFormat.getIntegerInstance().format(track.plays),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
        }
    }

    // Only tappable rows are cards. A track without a song is plain text.
    if (song != null) {
        Surface(
            modifier = rowModifier.clickable(role = Role.Button) { onPlaySong(song) },
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceVariant,
            contentColor = colors.onSurface,
        ) {
            content()
        }
    } else {
        Box(modifier = rowModifier) {
            content()
        }
    }
}
