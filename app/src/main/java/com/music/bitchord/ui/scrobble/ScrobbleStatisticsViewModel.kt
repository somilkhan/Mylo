package com.music.bitchord.ui.scrobble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.scrobbling.LastFmStatisticsRepository
import com.music.bitchord.data.scrobbling.LastFmStatsPeriod
import com.music.bitchord.data.scrobbling.LastFmStatsSnapshot
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * What the Stats tab shows. Every figure is Last.fm's: Mylo's own play log is
 * not read here, so nothing on the screen is a blend of two sources.
 *
 * [snapshot] can belong to an earlier period than [period] while the new one
 * loads — it stays on screen, dimmed by the caller, rather than blanking to a
 * skeleton on every switch. [snapshotPeriod] says which period it is.
 */
internal data class ScrobbleStatisticsUiState(
    val period: LastFmStatsPeriod = LastFmStatsPeriod.MONTH,
    val connected: Boolean = false,
    val username: String = "",
    val snapshot: LastFmStatsSnapshot? = null,
    val snapshotPeriod: LastFmStatsPeriod? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    /** The last fetch failed. With a [snapshot] it is shown as saved data. */
    val failed: Boolean = false,
) {
    /** A snapshot from another period is showing while [period] loads. */
    val isSwitchingPeriod: Boolean
        get() = snapshot != null && snapshotPeriod != period

    /** Nothing to show yet and nothing went wrong: draw the skeleton. */
    val showSkeleton: Boolean
        get() = connected && snapshot == null && !failed
}

class ScrobbleStatisticsViewModel : ViewModel() {
    private val repository = LastFmStatisticsRepository()
    private val _state = MutableStateFlow(ScrobbleStatisticsUiState())
    internal val state = _state.asStateFlow()

    private var loadJob: Job? = null
    private var activeKey: String? = null

    fun onScreenOpened() = load(force = false)

    fun onAccountChanged() = load(force = false)

    /** The first fetch starts as soon as Settings finishes connecting the account. */
    fun onLastFmConnected() = load(force = true)

    fun onScreenClosed() {
        loadJob?.cancel()
        loadJob = null
        activeKey = null
        _state.update { it.copy(isRefreshing = false, isLoading = false) }
    }

    internal fun setPeriod(period: LastFmStatsPeriod) {
        if (_state.value.period == period) return
        _state.update { it.copy(period = period, failed = false) }
        load(force = false)
    }

    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        val period = _state.value.period
        val username = AppSettings.lastfmUsername.value.trim()
        val connected = AppSettings.lastfmEnabled.value && username.isNotEmpty() && LastFM.isInitialized()
        if (!connected) {
            loadJob?.cancel()
            activeKey = null
            _state.value = ScrobbleStatisticsUiState(period = period, connected = false)
            return
        }

        val runtime = LastFM.currentConfig()
        val requestKey = listOf(
            period.name,
            username.lowercase(Locale.ROOT),
            runtime.endpoint,
            31 * runtime.apiKey.hashCode() + runtime.secret.hashCode(),
        ).joinToString("|")
        if (!force && loadJob?.isActive == true && activeKey == requestKey) return

        val key = repository.key(username, period)
        val cached = repository.cached(key)
        val fresh = repository.isFresh(key)
        val accountChanged = !_state.value.username.equals(username, ignoreCase = true)

        _state.update { current ->
            val shown = when {
                cached != null -> cached
                accountChanged -> null
                else -> current.snapshot
            }
            current.copy(
                connected = true,
                username = username,
                snapshot = shown,
                snapshotPeriod = if (cached != null) period else if (accountChanged) null else current.snapshotPeriod,
                isLoading = shown == null,
                isRefreshing = force,
                failed = false,
            )
        }
        if (!force && cached != null && fresh) {
            _state.update { it.copy(isLoading = false, isRefreshing = false) }
            return
        }

        loadJob?.cancel()
        activeKey = requestKey
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.load(username, period, force) }
            if (_state.value.period != period) return@launch
            result.fold(
                onSuccess = { snapshot ->
                    _state.update {
                        it.copy(
                            snapshot = snapshot,
                            snapshotPeriod = period,
                            isLoading = false,
                            isRefreshing = false,
                            failed = false,
                        )
                    }
                },
                onFailure = {
                    _state.update { current ->
                        // A snapshot from another period is not this period's
                        // saved data; keep it only if it is this period's.
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
