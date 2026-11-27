package com.music.bitchord.ui.scrobble

import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.scrobbling.LastFmStatsPeriod
import com.music.bitchord.data.scrobbling.LastFmStatsSnapshot
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PillSegmentedControl
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.icons.SfIcons
import com.music.bitchord.ui.scrobble.stats.StatsAlbumItem
import com.music.bitchord.ui.scrobble.stats.StatsAlbumShelf
import com.music.bitchord.ui.scrobble.stats.StatsAvatar
import com.music.bitchord.ui.scrobble.stats.StatsDashboard
import com.music.bitchord.ui.scrobble.stats.StatsMessage
import com.music.bitchord.ui.scrobble.stats.StatsRankedRow
import com.music.bitchord.ui.scrobble.stats.StatsRecentRow
import com.music.bitchord.ui.scrobble.stats.StatsSectionHeader
import com.music.bitchord.ui.scrobble.stats.StatsSkeleton
import com.music.bitchord.ui.scrobble.stats.StatsStaleNotice
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** What a tap on the Stats tab asks the app to do; none of it is the tab's own business. */
internal class StatsActions(
    val onPeriodSelect: (LastFmStatsPeriod) -> Unit,
    val onRetry: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenProfile: (String) -> Unit,
    val onOpenArtist: (String) -> Unit,
    val onOpenAlbum: (title: String, artist: String) -> Unit,
    /** Finds the track on YouTube Music and plays it; returns once it has. */
    val onPlayTrack: suspend (title: String, artist: String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScrobbleStatisticsContent(
    viewModel: ScrobbleStatisticsViewModel,
    actions: StatsActions,
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
    val reduceMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()

    LaunchedEffect(lastFmEnabled, lastFmUsername, lastFmApiKey, lastFmSecret, lastFmEndpoint) {
        viewModel.onScreenOpened()
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.onScreenClosed() }
    }

    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
    ) {
        ScrobbleStatisticsScreen(
            state = state,
            actions = actions,
            contentPadding = contentPadding,
            reduceMotion = reduceMotion,
        )
    }
}

/**
 * The Stats tab: the account, a period switcher, a dashboard card with the
 * period's scrobbles, their change and an activity chart, then the charts — artists and tracks as
 * ranked lists, albums as a shelf of covers — and the latest scrobbles.
 *
 * Stateless, so every state it can be in can be drawn on its own.
 */
@Composable
internal fun ScrobbleStatisticsScreen(
    state: ScrobbleStatisticsUiState,
    actions: StatsActions,
    contentPadding: PaddingValues,
    nowMillis: Long = System.currentTimeMillis(),
    reduceMotion: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var resolving by remember { mutableStateOf<String?>(null) }
    val play: (String, String) -> Unit = { title, artist ->
        val key = "$title\u0000$artist"
        if (resolving == null) {
            resolving = key
            scope.launch {
                try {
                    actions.onPlayTrack(title, artist)
                } finally {
                    resolving = null
                }
            }
        }
    }
    val snapshot = state.snapshot
    val contentAlpha by animateFloatAsState(
        targetValue = if (state.isSwitchingPeriod) 0.45f else 1f,
        label = "stats period switch",
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 32.dp),
        ) {
            item(key = "header") {
                StatsHeader(
                    topPadding = contentPadding.calculateTopPadding(),
                    state = state,
                    onOpenProfile = actions.onOpenProfile,
                )
            }

            if (!state.connected) {
                item(key = "connect") {
                    StatsMessage(
                        icon = SfIcons.BarChart,
                        title = stringResource(R.string.scrobble_stats_connect_title),
                        body = stringResource(R.string.scrobble_stats_connect_body),
                        action = stringResource(R.string.scrobble_open_settings),
                        onAction = actions.onOpenSettings,
                    )
                }
                return@LazyColumn
            }

            item(key = "periods") {
                PeriodSwitcher(state.period, actions.onPeriodSelect)
            }

            if (state.failed && snapshot != null) {
                item(key = "stale") { StatsStaleNotice(onRetry = actions.onRetry) }
            }

            when {
                snapshot == null && state.failed -> item(key = "failed") {
                    StatsMessage(
                        icon = SfIcons.ErrorOutline,
                        title = stringResource(R.string.scrobble_stats_failed_title),
                        body = stringResource(R.string.scrobble_stats_failed_body),
                        action = stringResource(R.string.retry),
                        onAction = actions.onRetry,
                    )
                }
                snapshot == null -> item(key = "skeleton") { StatsSkeleton() }
                else -> statsBody(
                    snapshot = snapshot,
                    period = state.snapshotPeriod ?: state.period,
                    alpha = contentAlpha,
                    nowMillis = nowMillis,
                    resolving = resolving,
                    onPlay = play,
                    actions = actions,
                    reduceMotion = reduceMotion,
                )
            }
        }
    }
}

@Composable
private fun StatsHeader(
    topPadding: androidx.compose.ui.unit.Dp,
    state: ScrobbleStatisticsUiState,
    onOpenProfile: (String) -> Unit,
) {
    val profile = state.snapshot?.profile
    val name = profile?.username ?: state.username
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = PAGE_GUTTER, top = topPadding + 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.scrobble_title_stats),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            if (state.connected && name.isNotBlank()) {
                val since = profile?.registeredAtEpochSeconds?.takeIf { it > 0L }?.let(::yearOf)
                Text(
                    text = listOf(
                        name,
                        since?.let { stringResource(R.string.scrobble_stats_profile_since, it) }
                            ?: stringResource(R.string.scrobble_stats_profile),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state.connected && name.isNotBlank()) {
            val description = stringResource(R.string.scrobble_stats_open_profile)
            StatsAvatar(
                url = profile?.avatarUrl,
                name = name,
                size = 40.dp,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onOpenProfile(name) }
                    .padding(4.dp)
                    .semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun PeriodSwitcher(selected: LastFmStatsPeriod, onSelect: (LastFmStatsPeriod) -> Unit) {
    val periods = LastFmStatsPeriod.entries
    val labels = periods.map { stringResource(it.shortLabel) }
    PillSegmentedControl(
        options = labels,
        selectedIndex = periods.indexOf(selected),
        onSelect = { onSelect(periods[it]) },
        modifier = Modifier.padding(horizontal = PAGE_GUTTER).padding(top = 18.dp),
    )
}

private fun LazyListScope.statsBody(
    snapshot: LastFmStatsSnapshot,
    period: LastFmStatsPeriod,
    alpha: Float,
    nowMillis: Long,
    resolving: String?,
    onPlay: (String, String) -> Unit,
    actions: StatsActions,
    reduceMotion: Boolean,
) {
    item(key = "dashboard") {
        val registered = snapshot.profile?.registeredAtEpochSeconds?.takeIf { it > 0L }
        val joined = registered?.let(::yearOf)
        val days = period.days?.toDouble()
            ?: registered?.let { ((nowMillis / 1_000L - it) / 86_400.0).coerceAtLeast(1.0) }
        StatsDashboard(
            period = period,
            scrobbles = snapshot.scrobbles,
            window = when {
                period != LastFmStatsPeriod.OVERALL -> stringResource(period.window)
                joined != null -> stringResource(R.string.scrobble_stats_window_overall_since, joined)
                else -> stringResource(R.string.scrobble_stats_window_overall)
            },
            previousScrobbles = snapshot.previousScrobbles,
            activity = snapshot.activity,
            perDay = days?.let { snapshot.scrobbles / it },
            artists = snapshot.artistCount,
            albums = snapshot.albumCount,
            tracks = snapshot.trackCount,
            reduceMotion = reduceMotion,
            modifier = Modifier.alpha(alpha),
        )
    }

    if (snapshot.isEmpty) {
        item(key = "empty") {
            StatsMessage(
                icon = SfIcons.GraphicEq,
                title = stringResource(R.string.scrobble_stats_period_empty_title),
                body = stringResource(R.string.scrobble_stats_period_empty_body),
                modifier = Modifier.alpha(alpha),
            )
        }
    } else {
        if (snapshot.topArtists.isNotEmpty()) {
            item(key = "artists") {
                ExpandableChart(
                    title = stringResource(R.string.scrobble_stats_top_artists),
                    period = period,
                    count = snapshot.topArtists.size,
                    modifier = Modifier.alpha(alpha),
                ) { shown ->
                    val leader = snapshot.topArtists.first().playCount.coerceAtLeast(1L)
                    snapshot.topArtists.take(shown).forEachIndexed { index, artist ->
                        StatsRankedRow(
                            rank = index + 1,
                            title = artist.name,
                            subtitle = null,
                            plays = artist.playCount,
                            fraction = artist.playCount.toFloat() / leader,
                            onClick = { actions.onOpenArtist(artist.name) },
                        )
                    }
                }
            }
        }

        if (snapshot.topTracks.isNotEmpty()) {
            item(key = "tracks") {
                ExpandableChart(
                    title = stringResource(R.string.scrobble_stats_top_tracks),
                    period = period,
                    count = snapshot.topTracks.size,
                    modifier = Modifier.alpha(alpha),
                ) { shown ->
                    val leader = snapshot.topTracks.first().playCount.coerceAtLeast(1L)
                    snapshot.topTracks.take(shown).forEachIndexed { index, track ->
                        StatsRankedRow(
                            rank = index + 1,
                            title = track.name,
                            subtitle = track.artist,
                            plays = track.playCount,
                            fraction = track.playCount.toFloat() / leader,
                            onClick = { onPlay(track.name, track.artist) },
                            busy = resolving == "${track.name}\u0000${track.artist}",
                        )
                    }
                }
            }
        }

        if (snapshot.topAlbums.isNotEmpty()) {
            item(key = "albums") {
                Column(Modifier.alpha(alpha)) {
                    StatsSectionHeader(stringResource(R.string.scrobble_stats_top_albums))
                    Spacer(Modifier.height(4.dp))
                    StatsAlbumShelf(
                        albums = snapshot.topAlbums.take(ALBUM_SHELF_SIZE).map {
                            StatsAlbumItem(it.name, it.artist, it.playCount, it.artworkUrl)
                        },
                        onClick = { actions.onOpenAlbum(it.title, it.artist) },
                    )
                }
            }
        }
    }

    if (snapshot.recent.isNotEmpty()) {
        item(key = "recent-header") {
            StatsSectionHeader(stringResource(R.string.scrobble_stats_recent))
        }
        itemsIndexed(
            snapshot.recent,
            key = { index, track -> "recent:$index:${track.timestamp}:${track.name}" },
        ) { _, track ->
            StatsRecentRow(
                title = track.name,
                artist = track.artist,
                artworkUrl = track.artworkUrl,
                trailing = if (track.nowPlaying) {
                    stringResource(R.string.scrobble_stats_now_playing)
                } else {
                    relativeTime(track.timestamp * 1_000L, nowMillis)
                },
                nowPlaying = track.nowPlaying,
                onClick = { onPlay(track.name, track.artist) },
                busy = resolving == "${track.name}\u0000${track.artist}",
            )
        }
    }
}

/**
 * A chart that shows its top [COLLAPSED_ROWS] and opens to everything Last.fm
 * returned. Folds again when the period changes.
 */
@Composable
private fun ExpandableChart(
    title: String,
    period: LastFmStatsPeriod,
    count: Int,
    modifier: Modifier = Modifier,
    rows: @Composable (shown: Int) -> Unit,
) {
    var expanded by rememberSaveable(period) { mutableStateOf(false) }
    val expandable = count > COLLAPSED_ROWS
    Column(modifier) {
        StatsSectionHeader(
            title = title,
            action = if (!expandable) {
                null
            } else if (expanded) {
                stringResource(R.string.scrobble_stats_show_less)
            } else {
                stringResource(R.string.show_all)
            },
            onAction = if (expandable) ({ expanded = !expanded }) else null,
        )
        rows(if (expanded) count else minOf(count, COLLAPSED_ROWS))
    }
}

private val LastFmStatsPeriod.shortLabel: Int
    get() = when (this) {
        LastFmStatsPeriod.WEEK -> R.string.scrobble_stats_period_week
        LastFmStatsPeriod.MONTH -> R.string.scrobble_stats_period_month
        LastFmStatsPeriod.QUARTER -> R.string.scrobble_stats_period_quarter
        LastFmStatsPeriod.HALF_YEAR -> R.string.scrobble_stats_period_half_year
        LastFmStatsPeriod.YEAR -> R.string.scrobble_stats_period_year
        LastFmStatsPeriod.OVERALL -> R.string.scrobble_stats_period_overall
    }

private val LastFmStatsPeriod.window: Int
    get() = when (this) {
        LastFmStatsPeriod.WEEK -> R.string.scrobble_stats_window_week
        LastFmStatsPeriod.MONTH -> R.string.scrobble_stats_window_month
        LastFmStatsPeriod.QUARTER -> R.string.scrobble_stats_window_quarter
        LastFmStatsPeriod.HALF_YEAR -> R.string.scrobble_stats_window_half_year
        LastFmStatsPeriod.YEAR -> R.string.scrobble_stats_window_year
        LastFmStatsPeriod.OVERALL -> R.string.scrobble_stats_window_overall
    }

private fun yearOf(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).year.toString()

/** "5 min. ago", "Yesterday", "12 Nov" — the platform's own wording, in the user's language. */
private fun relativeTime(atMillis: Long, nowMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        atMillis,
        nowMillis,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE or DateUtils.FORMAT_ABBREV_MONTH,
    ).toString()

private const val COLLAPSED_ROWS = 5
private const val ALBUM_SHELF_SIZE = 20
