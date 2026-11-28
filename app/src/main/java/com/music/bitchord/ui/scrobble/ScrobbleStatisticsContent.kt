package com.music.bitchord.ui.scrobble

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.scrobbling.LastFmStatsPeriod
import com.music.bitchord.data.scrobbling.LastFmStatsSnapshot
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.IosMenuPopover
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.IosMenuRow
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PillSegmentedControl
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.icons.SfIcons
import com.music.bitchord.ui.scrobble.stats.StatsAvatar
import com.music.bitchord.ui.scrobble.stats.StatsDashboard
import com.music.bitchord.ui.scrobble.stats.StatsDayHeader
import com.music.bitchord.ui.scrobble.stats.StatsListEmpty
import com.music.bitchord.ui.scrobble.stats.StatsListFooter
import com.music.bitchord.ui.scrobble.stats.StatsListHeader
import com.music.bitchord.ui.scrobble.stats.StatsListRow
import com.music.bitchord.ui.scrobble.stats.StatsListSkeleton
import com.music.bitchord.ui.scrobble.stats.StatsMessage
import com.music.bitchord.ui.scrobble.stats.StatsPlays
import com.music.bitchord.ui.scrobble.stats.StatsSkeleton
import com.music.bitchord.ui.scrobble.stats.StatsStaleNotice
import com.music.bitchord.ui.scrobble.stats.StatsWhen
import com.music.bitchord.ui.scrobble.stats.rememberCountFormat
import com.music.bitchord.ui.scrobble.stats.toQuantity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit

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
    val onListModeSelect: (StatsListMode) -> Unit = {},
    /** The next page of the history, or the first again after it failed. */
    val onLoadMoreRecent: () -> Unit = {},
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
    pageBackdrop: Backdrop? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastFmEnabled by AppSettings.lastfmEnabled.collectAsStateWithLifecycle()
    val lastFmUsername by AppSettings.lastfmUsername.collectAsStateWithLifecycle()
    val lastFmApiKey by AppSettings.lastfmApiKey.collectAsStateWithLifecycle()
    val lastFmSecret by AppSettings.lastfmSecret.collectAsStateWithLifecycle()
    val lastFmEndpoint by AppSettings.lastfmEndpoint.collectAsStateWithLifecycle()
    val lastFmSession by AppSettings.lastfmSessionKey.collectAsStateWithLifecycle()
    val reduceMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, lastFmEnabled, lastFmUsername, lastFmApiKey, lastFmSecret, lastFmEndpoint, lastFmSession) {
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
            pageBackdrop = pageBackdrop,
        )
    }
}

/**
 * The Stats tab: the account and a period switcher, the period's dashboard,
 * and under it one list — the scrobble history by day, or the period's top
 * tracks, artists or albums — picked from a menu on the list's heading.
 *
 * Scrolling the list folds the dashboard into one line first; it opens again
 * once the list is back at its top, or on a tap. Between the two it settles to
 * whichever end it is nearer when the finger lifts.
 *
 * Stateless, so every state it can be in can be drawn on its own;
 * [initialCollapse] and [initialMenuOpen] let a test draw it folded, or with
 * the list menu up.
 */
@Composable
internal fun ScrobbleStatisticsScreen(
    state: ScrobbleStatisticsUiState,
    actions: StatsActions,
    contentPadding: PaddingValues,
    nowMillis: Long = System.currentTimeMillis(),
    reduceMotion: Boolean = false,
    initialCollapse: Float = 1f,
    initialMenuOpen: Boolean = false,
    pageBackdrop: Backdrop? = null,
) {
    val effectiveReduceMotion = scrobbleReduceMotion(reduceMotion)
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
    val anchorView = LocalView.current
    val snapshot = state.snapshot
    val contentAlpha by animateFloatAsState(
        targetValue = if (state.isSwitchingPeriod) 0.45f else 1f,
        animationSpec = if (effectiveReduceMotion) snap() else spring(),
        label = "stats period switch",
    )
    val listState = rememberLazyListState()
    val fold = rememberDashboardFold(initialCollapse, effectiveReduceMotion)
    val dashboard = snapshot?.takeIf { state.connected }
    val hasDashboard = dashboard != null
    LaunchedEffect(hasDashboard) { if (!hasDashboard) fold.reset() }

    val pinned by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    var menuOpen by remember { mutableStateOf(initialMenuOpen) }
    var menuAnchor by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(state.listMode) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) listState.scrollToItem(0)
    }
    if (state.listMode == StatsListMode.RECENT && state.connected && snapshot != null &&
        state.recent.canLoadMore && !state.recent.loading && !state.recent.failed
    ) {
        // Restart after each completed page, even if the viewport still fits
        // all loaded rows. A failed page waits for the explicit Retry action.
        LaunchedEffect(listState, state.recent.page) {
            snapshotFlow {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_AHEAD
            }
                .distinctUntilChanged()
                .filter { it }
                .collect { actions.onLoadMoreRecent() }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().nestedScroll(fold.connection)) {
                StatsHeader(
                    topPadding = contentPadding.calculateTopPadding(),
                    state = state,
                    onOpenProfile = actions.onOpenProfile,
                )
                if (state.connected) {
                    PeriodSwitcher(state.period, actions.onPeriodSelect)
                    if (state.failed && snapshot != null) StatsStaleNotice(onRetry = actions.onRetry)
                }
                if (dashboard != null) {
                    Dashboard(
                        snapshot = dashboard,
                        period = state.snapshotPeriod ?: state.period,
                        nowMillis = nowMillis,
                        reduceMotion = effectiveReduceMotion,
                        fold = fold,
                        modifier = Modifier.alpha(contentAlpha),
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 32.dp),
                ) {
                    when {
                        !state.connected -> item(key = "connect") {
                            StatsMessage(
                                icon = SfIcons.BarChart,
                                title = stringResource(R.string.scrobble_stats_connect_title),
                                body = stringResource(R.string.scrobble_stats_connect_body),
                                action = stringResource(R.string.scrobble_open_settings),
                                onAction = actions.onOpenSettings,
                            )
                        }
                        snapshot == null && state.failed -> item(key = "failed") {
                            StatsMessage(
                                icon = SfIcons.ErrorOutline,
                                title = stringResource(R.string.scrobble_stats_failed_title),
                                body = stringResource(R.string.scrobble_stats_failed_body),
                                action = stringResource(R.string.retry),
                                onAction = actions.onRetry,
                            )
                        }
                        snapshot == null -> item(key = "skeleton") { StatsSkeleton(reduceMotion = effectiveReduceMotion) }
                        else -> statsList(
                            state = state,
                            snapshot = snapshot,
                            alpha = contentAlpha,
                            nowMillis = nowMillis,
                            resolving = resolving,
                            onPlay = play,
                            actions = actions,
                            reduceMotion = effectiveReduceMotion,
                            menuOpen = menuOpen,
                            pinned = { pinned },
                            onOpenMenu = { menuOpen = true },
                            onAnchor = { menuAnchor = it.toScreenCoordinates(anchorView) },
                        )
                    }
                }
            }

            if (menuOpen) Dialog(
                onDismissRequest = { menuOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                IosMenuPopover(
                    item = state.listMode,
                    anchor = menuAnchor,
                    onDismiss = { menuOpen = false },
                    backdrop = pageBackdrop,
                    screenCoordinates = true,
                    bottomClearance = ScrobbleChromeClearance,
                ) { current ->
                StatsListMode.entries.forEach { mode ->
                    IosMenuRow(
                        label = stringResource(mode.label),
                        icon = mode.icon,
                        onClick = {
                            menuOpen = false
                            actions.onListModeSelect(mode)
                        },
                        trailing = if (mode == current) {
                            {
                                Icon(
                                    imageVector = SfIcons.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 10.dp).size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
                }
            }
        }
    }
}

/**
 * How far the dashboard is folded, 0 open to 1 folded, and the nested scroll
 * that drives it: a scroll up folds the card before the list moves, a scroll
 * down past the list's top opens it, and a lift between the two settles it.
 */
@Stable
internal class DashboardFold(
    initial: Float,
    private val scope: CoroutineScope,
    private var reduceMotion: Boolean,
) {
    var fraction by mutableFloatStateOf(initial.coerceIn(0f, 1f))
        private set

    /** The card's open height less its folded one, in px: the scroll it takes to fold. */
    var distance = 0f
    private var settling: Job? = null

    fun expand() = settle(0f)
    fun collapse() = settle(1f)

    fun reset() {
        cancel()
        fraction = 1f
    }

    fun cancel() {
        settling?.cancel()
        settling = null
    }

    fun setReduceMotion(reduce: Boolean) {
        if (reduceMotion == reduce) return
        val wasSettling = settling?.isActive == true
        cancel()
        reduceMotion = reduce
        if (reduce && wasSettling) fraction = if (fraction < 0.5f) 0f else 1f
    }

    private fun settle(target: Float, velocity: Float = 0f) {
        cancel()
        if (fraction == target) return
        if (reduceMotion) {
            fraction = target
            return
        }
        settling = scope.launch {
            animate(
                initialValue = fraction,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f),
            ) { value, _ -> fraction = value.coerceIn(0f, 1f) }
        }
    }

    /** Folds or opens the card by a scroll of [deltaY] px; returns the part it used. */
    private fun take(deltaY: Float): Float {
        if (distance <= 0f) return 0f
        val next = (fraction - deltaY / distance).coerceIn(0f, 1f)
        val used = (fraction - next) * distance
        if (next != fraction) {
            settling?.cancel()
            fraction = next
        }
        return used
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f) Offset(0f, take(available.y)) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0f && fraction < 1f && source == NestedScrollSource.UserInput) {
                Offset(0f, take(available.y))
            } else {
                Offset.Zero
            }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (fraction > 0f && fraction < 1f) {
                // A fling down the list had no room for opens the card;
                // otherwise it goes to whichever end it is nearer.
                val target = if (available.y > 0f || fraction < 0.5f) 0f else 1f
                settle(target, velocity = if (distance > 0f) -available.y / distance else 0f)
            }
            return Velocity.Zero
        }
    }
}

@Composable
private fun rememberDashboardFold(initial: Float, reduceMotion: Boolean): DashboardFold {
    val scope = rememberCoroutineScope()
    val fold = remember { DashboardFold(initial, scope, reduceMotion) }
    LaunchedEffect(fold, reduceMotion) { fold.setReduceMotion(reduceMotion) }
    DisposableEffect(fold) { onDispose { fold.cancel() } }
    return fold
}

@Composable
private fun Dashboard(
    snapshot: LastFmStatsSnapshot,
    period: LastFmStatsPeriod,
    nowMillis: Long,
    reduceMotion: Boolean,
    fold: DashboardFold,
    modifier: Modifier = Modifier,
) {
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
        collapse = { fold.fraction },
        onExpand = fold::expand,
        onCollapse = fold::collapse,
        onFoldDistance = { fold.distance = it },
        modifier = modifier,
    )
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
        modifier = Modifier.padding(horizontal = PAGE_GUTTER).padding(top = 14.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.statsList(
    state: ScrobbleStatisticsUiState,
    snapshot: LastFmStatsSnapshot,
    alpha: Float,
    nowMillis: Long,
    resolving: String?,
    onPlay: (String, String) -> Unit,
    actions: StatsActions,
    reduceMotion: Boolean,
    menuOpen: Boolean,
    pinned: () -> Boolean,
    onOpenMenu: () -> Unit,
    onAnchor: (Rect) -> Unit,
) {
    val mode = state.listMode
    stickyHeader(key = "list-header") {
        StatsListHeader(
            title = stringResource(mode.label),
            icon = mode.icon,
            menuOpen = menuOpen,
            pinned = pinned(),
            onOpenMenu = onOpenMenu,
            onAnchor = onAnchor,
            reduceMotion = reduceMotion,
        )
    }

    // Rows fade in and out when the list or the period changes; nothing
    // slides, and with reduced motion nothing animates.
    val itemModifier: LazyItemScope.() -> Modifier = {
        if (reduceMotion) {
            Modifier
        } else {
            Modifier.animateItem(fadeInSpec = tween(220), placementSpec = null, fadeOutSpec = tween(120))
        }
    }
    fun busy(title: String, artist: String) = resolving == "$title\u0000$artist"

    when (mode) {
        StatsListMode.RECENT -> {
            val feed = state.recent
            when {
                !feed.loaded && feed.failed -> item(key = "recent-failed") {
                    StatsListFooter(loading = false, failed = true, onRetry = actions.onLoadMoreRecent, reduceMotion = reduceMotion)
                }
                !feed.loaded -> item(key = "recent-skeleton") { StatsListSkeleton(reduceMotion = reduceMotion) }
                feed.items.isEmpty() -> item(key = "recent-empty") {
                    StatsListEmpty(stringResource(R.string.scrobble_stats_recent_empty))
                }
                else -> {
                    val zone = ZoneId.systemDefault()
                    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
                    val entries = groupRecentByDay(feed.items, zone, today)
                    items(entries, key = { it.key }, contentType = { it::class.simpleName }) { entry ->
                        when (entry) {
                            is RecentEntry.Day -> StatsDayHeader(
                                text = dayLabel(entry.date, today),
                                modifier = itemModifier(),
                            )
                            is RecentEntry.Scrobble -> {
                                val track = entry.track
                                val trailing = if (track.nowPlaying) {
                                    stringResource(R.string.scrobble_stats_now_playing)
                                } else {
                                    timeOfDay(track.timestamp * 1_000L)
                                }
                                StatsListRow(
                                    title = track.name,
                                    subtitle = track.artist,
                                    artworkUrl = track.artworkUrl,
                                    placeholder = SfIcons.MusicNote,
                                    description = "${track.name}, ${track.artist}, $trailing",
                                    onClick = { onPlay(track.name, track.artist) },
                                    modifier = itemModifier(),
                                ) {
                                    StatsWhen(trailing, track.nowPlaying, busy(track.name, track.artist), reduceMotion)
                                }
                            }
                        }
                    }
                }
            }
            if (feed.loaded && (feed.loading || feed.failed || feed.canLoadMore)) {
                item(key = "recent-footer") {
                    StatsListFooter(
                        loading = feed.loading,
                        failed = feed.failed,
                        onRetry = actions.onLoadMoreRecent,
                        reduceMotion = reduceMotion,
                    )
                }
            }
        }

        StatsListMode.TOP_TRACKS -> ranked(
            rows = snapshot.topTracks,
            key = { "track:${it.name}:${it.artist}" },
            alpha = alpha,
            itemModifier = itemModifier,
        ) { rank, track, modifier ->
            val plays = pluralStringResource(
                R.plurals.scrobble_stats_scrobble_count,
                track.playCount.toQuantity(),
                rememberCountFormat().format(track.playCount),
            )
            StatsListRow(
                title = track.name,
                subtitle = track.artist,
                artworkUrl = track.artworkUrl,
                placeholder = SfIcons.MusicNote,
                description = "$rank, ${track.name}, ${track.artist}, $plays",
                onClick = { onPlay(track.name, track.artist) },
                rank = rank,
                modifier = modifier,
            ) {
                StatsPlays(track.playCount, busy(track.name, track.artist), reduceMotion)
            }
        }

        StatsListMode.TOP_ARTISTS -> ranked(
            rows = snapshot.topArtists,
            key = { "artist:${it.name}" },
            alpha = alpha,
            itemModifier = itemModifier,
        ) { rank, artist, modifier ->
            val plays = pluralStringResource(
                R.plurals.scrobble_stats_scrobble_count,
                artist.playCount.toQuantity(),
                rememberCountFormat().format(artist.playCount),
            )
            StatsListRow(
                title = artist.name,
                subtitle = null,
                artworkUrl = artist.artworkUrl,
                placeholder = SfIcons.Artist,
                description = "$rank, ${artist.name}, $plays",
                onClick = { actions.onOpenArtist(artist.name) },
                rank = rank,
                round = true,
                modifier = modifier,
            ) {
                StatsPlays(artist.playCount)
            }
        }

        StatsListMode.TOP_ALBUMS -> ranked(
            rows = snapshot.topAlbums,
            key = { "album:${it.name}:${it.artist}" },
            alpha = alpha,
            itemModifier = itemModifier,
        ) { rank, album, modifier ->
            val plays = pluralStringResource(
                R.plurals.scrobble_stats_scrobble_count,
                album.playCount.toQuantity(),
                rememberCountFormat().format(album.playCount),
            )
            StatsListRow(
                title = album.name,
                subtitle = album.artist,
                artworkUrl = album.artworkUrl,
                placeholder = SfIcons.Album,
                description = "$rank, ${album.name}, ${album.artist}, $plays",
                onClick = { actions.onOpenAlbum(album.name, album.artist) },
                rank = rank,
                modifier = modifier,
            ) {
                StatsPlays(album.playCount)
            }
        }
    }
}

/** A ranked list for the period, dimmed with the dashboard while another period loads. */
private fun <T> LazyListScope.ranked(
    rows: List<T>,
    key: (T) -> String,
    alpha: Float,
    itemModifier: LazyItemScope.() -> Modifier,
    row: @Composable (rank: Int, item: T, modifier: Modifier) -> Unit,
) {
    if (rows.isEmpty()) {
        item(key = "ranked-empty") {
            StatsListEmpty(stringResource(R.string.scrobble_stats_list_period_empty))
        }
        return
    }
    itemsIndexed(rows, key = { index, item -> "$index:${key(item)}" }) { index, item ->
        row(index + 1, item, itemModifier().alpha(alpha))
    }
}

/** "Today", "Yesterday", a weekday for the rest of the week, then the date. */
@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val daysAgo = ChronoUnit.DAYS.between(date, today)
    return when {
        daysAgo == 0L -> stringResource(R.string.scrobble_stats_today)
        daysAgo == 1L -> stringResource(R.string.scrobble_stats_yesterday)
        daysAgo in 2L..6L -> date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        else -> remember(date, locale, today.year) {
            val skeleton = if (date.year == today.year) "MMMMd" else "yMMMMd"
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(date)
        }
    }
}

/** "21:04" or "9:04 PM", as the device tells time. */
@Composable
private fun timeOfDay(atMillis: Long): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(atMillis, context, configuration) { DateUtils.formatDateTime(context, atMillis, DateUtils.FORMAT_SHOW_TIME) }
}

private val StatsListMode.label: Int
    get() = when (this) {
        StatsListMode.RECENT -> R.string.scrobble_stats_list_recent
        StatsListMode.TOP_TRACKS -> R.string.scrobble_stats_top_tracks
        StatsListMode.TOP_ARTISTS -> R.string.scrobble_stats_top_artists
        StatsListMode.TOP_ALBUMS -> R.string.scrobble_stats_top_albums
    }

private val StatsListMode.icon: ImageVector
    get() = when (this) {
        StatsListMode.RECENT -> SfIcons.Clock
        StatsListMode.TOP_TRACKS -> SfIcons.MusicNote
        StatsListMode.TOP_ARTISTS -> SfIcons.Artist
        StatsListMode.TOP_ALBUMS -> SfIcons.Album
    }

internal val LastFmStatsPeriod.shortLabel: Int
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

/** Rows from the end of the loaded history at which the next page is asked for. */
private const val LOAD_AHEAD = 12
