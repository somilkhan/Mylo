package com.music.bitchord.ui.scrobble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.scrobbling.LastFmStatisticsRepository
import com.music.bitchord.data.scrobbling.LastFmStatsCacheKey
import com.music.bitchord.data.scrobbling.LastFmStatsPeriod
import com.music.bitchord.data.scrobbling.LastFmStatsSnapshot
import com.music.bitchord.data.scrobbling.statsConfigurationFingerprint
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** The history is independent of the selected Last.fm chart period. */
internal enum class StatsListMode { RECENT, TOP_TRACKS, TOP_ARTISTS, TOP_ALBUMS }

/** A failed request keeps loaded rows and remembers the exact page to retry. */
internal data class RecentFeed(
    val items: List<LastFM.RecentTrack> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 0,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val failedPage: Int? = null,
    /** Fixed for a paging session so newly arriving scrobbles do not shift later pages. */
    val toEpochSeconds: Long? = null,
) {
    val loaded: Boolean get() = page > 0
    val canLoadMore: Boolean get() = loaded && page < totalPages
    val nextPage: Int get() = failedPage ?: if (loaded) page + 1 else 1
}

/** Every figure is Last.fm's, never blended with Mylo's local play log. */
internal data class ScrobbleStatisticsUiState(
    val period: LastFmStatsPeriod = LastFmStatsPeriod.MONTH,
    val connected: Boolean = false,
    val username: String = "",
    val snapshot: LastFmStatsSnapshot? = null,
    val snapshotPeriod: LastFmStatsPeriod? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val failed: Boolean = false,
    val listMode: StatsListMode = StatsListMode.RECENT,
    val recent: RecentFeed = RecentFeed(),
) {
    /** Earlier-period data may stay dimmed, but never data from another account/config. */
    val isSwitchingPeriod: Boolean get() = snapshot != null && snapshotPeriod != period
    val showSkeleton: Boolean get() = connected && snapshot == null && !failed
}

/** Includes settings and runtime identity, without retaining raw credentials in request keys. */
internal data class StatsAccount(val username: String, val connected: Boolean, val configuration: String) {
    val identity: Pair<String, String> get() = username.trim().lowercase(Locale.ROOT) to configuration
}

class ScrobbleStatisticsViewModel internal constructor(
    private val repository: LastFmStatisticsRepository,
    private val accountProvider: () -> StatsAccount,
    private val ioDispatcher: CoroutineDispatcher,
    private val prepareAccount: () -> Unit = {},
) : ViewModel() {
    constructor() : this(
        LastFmStatisticsRepository(), ::currentStatsAccount, Dispatchers.IO, ::configureStatsAccount,
    )

    private val _state = MutableStateFlow(ScrobbleStatisticsUiState())
    internal val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var activeKey: LastFmStatsCacheKey? = null
    private var recentJob: Job? = null
    private var shownAccount: StatsAccount? = null
    private var loadGeneration = 0L
    private var recentGeneration = 0L

    fun onScreenOpened() = load(force = false)
    fun onAccountChanged() = load(force = false)
    fun onLastFmConnected() = load(force = true)

    fun onScreenClosed() {
        cancelSnapshot()
        cancelRecent()
        _state.update {
            it.copy(isRefreshing = false, isLoading = false, recent = it.recent.copy(loading = false))
        }
    }

    internal fun setPeriod(period: LastFmStatsPeriod) {
        if (_state.value.period == period) return
        _state.update { it.copy(period = period, failed = false) }
        load(force = false)
    }

    internal fun setListMode(mode: StatsListMode) {
        _state.update { it.copy(listMode = mode) }
    }

    fun refresh() = load(force = true)

    /** Explicit retry is allowed even if a failed refresh was of an already-complete feed. */
    fun loadMoreRecent() {
        val feed = _state.value.recent
        if (feed.loading || (feed.loaded && !feed.canLoadMore && !feed.failed)) return
        loadRecent(feed.nextPage)
    }

    private fun cancelSnapshot() {
        loadGeneration++
        loadJob?.cancel()
        loadJob = null
        activeKey = null
    }

    private fun cancelRecent() {
        recentGeneration++
        recentJob?.cancel()
        recentJob = null
    }

    private fun isCurrentAccount(account: StatsAccount): Boolean {
        val current = accountProvider()
        return current.connected && current.identity == account.identity && shownAccount?.identity == account.identity
    }

    private fun loadRecent(page: Int) {
        val account = shownAccount?.takeIf { isCurrentAccount(it) } ?: return
        cancelRecent()
        val generation = recentGeneration
        val cutoff = if (page == 1) repository.recentCutoff() else _state.value.recent.toEpochSeconds
        _state.update { it.copy(recent = it.recent.copy(loading = true, failed = false, failedPage = null)) }
        recentJob = viewModelScope.launch {
            val result = withContext(ioDispatcher) { repository.recentPage(account.username, page, cutoff) }
            currentCoroutineContext().ensureActive()
            if (generation != recentGeneration || !isCurrentAccount(account)) return@launch
            _state.update { current ->
                val feed = current.recent
                result.fold(
                    onSuccess = { response ->
                        current.copy(
                            recent = RecentFeed(
                                items = mergeRecentTracks(if (page == 1) emptyList() else feed.items, response.items),
                                page = page,
                                totalPages = response.totalPages.coerceAtLeast(page),
                                toEpochSeconds = cutoff,
                            ),
                        )
                    },
                    onFailure = {
                        current.copy(recent = feed.copy(loading = false, failed = true, failedPage = page))
                    },
                )
            }
        }
    }

    private fun load(force: Boolean) {
        // Settings changes can reach this screen before playback/queue collectors
        // configure LastFM. Synchronize once, not as a side effect of a result guard.
        prepareAccount()
        val account = accountProvider()
        val period = _state.value.period
        if (!account.connected) {
            cancelSnapshot()
            cancelRecent()
            shownAccount = null
            _state.value = ScrobbleStatisticsUiState(period = period, listMode = _state.value.listMode)
            return
        }

        val key = repository.key(account.username, period)
        if (!force && loadJob?.isActive == true && activeKey == key && shownAccount?.identity == account.identity) return
        val accountChanged = shownAccount?.identity != account.identity
        // Invalidate the old request BEFORE the fresh-cache early return.
        cancelSnapshot()
        if (accountChanged) cancelRecent()
        shownAccount = account
        val generation = loadGeneration
        val cached = repository.cached(key)
        val fresh = repository.isFresh(key)
        _state.update { current ->
            val shown = cached ?: current.snapshot.takeUnless { accountChanged }
            current.copy(
                connected = true,
                username = account.username,
                snapshot = shown,
                snapshotPeriod = if (cached != null) period else if (accountChanged) null else current.snapshotPeriod,
                isLoading = shown == null,
                isRefreshing = force,
                failed = false,
                recent = if (accountChanged) RecentFeed() else current.recent,
            )
        }
        if (force || accountChanged || (!_state.value.recent.loaded && !_state.value.recent.loading)) loadRecent(1)
        if (!force && cached != null && fresh) {
            _state.update { it.copy(isLoading = false, isRefreshing = false) }
            return
        }

        activeKey = key
        loadJob = viewModelScope.launch {
            val result = withContext(ioDispatcher) { repository.load(account.username, period, force) }
            currentCoroutineContext().ensureActive()
            if (generation != loadGeneration || _state.value.period != period || !isCurrentAccount(account)) return@launch
            result.fold(
                onSuccess = { snapshot ->
                    _state.update {
                        it.copy(snapshot = snapshot, snapshotPeriod = period, isLoading = false, isRefreshing = false, failed = false)
                    }
                },
                onFailure = {
                    _state.update { current ->
                        val stale = repository.cached(key)
                        current.copy(
                            snapshot = stale ?: current.snapshot?.takeIf { current.snapshotPeriod == period },
                            snapshotPeriod = if (stale != null) period else current.snapshotPeriod?.takeIf { it == period },
                            isLoading = false,
                            isRefreshing = false,
                            failed = true,
                        )
                    }
                },
            )
        }
    }
}

private fun statsSettingsConfig(): LastFM.RuntimeConfig? = runCatching {
    val apiKey = AppSettings.lastfmApiKey.value.trim()
    val secret = AppSettings.lastfmSecret.value.trim()
    require(apiKey.isNotEmpty() && secret.isNotEmpty())
    LastFM.RuntimeConfig(
        LastFM.normalizeEndpoint(AppSettings.lastfmEndpoint.value.ifBlank { LastFM.DEFAULT_API_ENDPOINT }),
        apiKey, secret, AppSettings.lastfmSessionKey.value.takeIf { it.isNotBlank() },
    )
}.getOrNull()

private fun configureStatsAccount() {
    if (!AppSettings.lastfmEnabled.value || AppSettings.lastfmUsername.value.isBlank()) return
    val config = statsSettingsConfig() ?: return
    if (LastFM.currentConfig() != config) LastFM.configure(config.endpoint, config.apiKey, config.secret, config.sessionKey)
}

private fun currentStatsAccount(): StatsAccount {
    val username = AppSettings.lastfmUsername.value.trim()
    val settings = statsSettingsConfig()
    val runtime = LastFM.currentConfig()
    return StatsAccount(
        username = username,
        connected = AppSettings.lastfmEnabled.value && username.isNotEmpty() && settings != null && runtime == settings,
        configuration = statsConfigurationFingerprint(runtime),
    )
}
