package com.music.bitchord.ui.scrobble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.scrobbling.LastFmStatisticsRepository
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class ScrobbleStatisticsUiState(
    val period: ReplayPeriod = ReplayPeriod.ALL_TIME,
    val localSummary: ReplaySummary? = null,
    val tracks: List<MergedStatisticsTrack> = emptyList(),
    val lastFmConnected: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val showingStaleLastFmData: Boolean = false,
    val loadedPeriod: ReplayPeriod? = null,
)

class ScrobbleStatisticsViewModel : ViewModel() {
    private val repository = LastFmStatisticsRepository()
    private val _state = MutableStateFlow(ScrobbleStatisticsUiState())
    internal val state = _state.asStateFlow()

    private var loadJob: Job? = null
    private var activeRequestKey: String? = null

    fun onScreenOpened() = load(force = false)

    fun onAccountChanged() = load(force = false)

    /** The first account fetch starts immediately after Settings completes the connection. */
    fun onLastFmConnected() = load(force = true)

    fun onScreenClosed() {
        loadJob?.cancel()
        loadJob = null
        activeRequestKey = null
        _state.update { it.copy(isRefreshing = false) }
    }

    fun setPeriod(period: ReplayPeriod) {
        if (_state.value.period == period) return
        _state.update {
            it.copy(
                period = period,
                isLoading = it.tracks.isEmpty() && it.localSummary == null,
                isRefreshing = it.tracks.isNotEmpty() || it.localSummary != null,
                error = null,
                showingStaleLastFmData = false,
            )
        }
        load(force = false)
    }

    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        val snapshot = _state.value
        val period = snapshot.period
        val username = AppSettings.lastfmUsername.value.trim()
        val connected = AppSettings.lastfmEnabled.value &&
            username.isNotEmpty() &&
            LastFM.isInitialized()
        val query = if (connected) repository.query(username, period) else null
        val cached = query?.let(repository::cached)
        val cachedTracks = cached?.tracks.orEmpty()
        val cacheFresh = query?.let(repository::isFresh) == true
        val remotePending = connected && query != null && (force || !cacheFresh)
        val runtime = LastFM.currentConfig()
        val requestKey = listOf(
            period.name,
            connected.toString(),
            username.lowercase(Locale.ROOT),
            runtime.endpoint,
            31 * runtime.apiKey.hashCode() + runtime.secret.hashCode(),
        ).joinToString("|")

        if (!force && loadJob?.isActive == true && activeRequestKey == requestKey) return

        val samePeriodData = snapshot.loadedPeriod == period
        val existingTracks = when {
            samePeriodData -> mergeStatisticsTracks(
                snapshot.localSummary?.songs.orEmpty(),
                cachedTracks,
                connected,
            )
            cachedTracks.isNotEmpty() -> mergeStatisticsTracks(emptyList(), cachedTracks, connected)
            else -> snapshot.tracks
        }
        val hasExistingData = existingTracks.isNotEmpty()

        loadJob?.cancel()
        activeRequestKey = requestKey
        _state.update {
            it.copy(
                lastFmConnected = connected,
                tracks = existingTracks,
                isLoading = existingTracks.isEmpty() && !samePeriodData,
                isRefreshing = force || (remotePending && hasExistingData) ||
                    (!samePeriodData && hasExistingData),
                error = null,
                showingStaleLastFmData = false,
            )
        }

        loadJob = viewModelScope.launch {
            try {
                val local = withContext(Dispatchers.IO) {
                    ListeningStats.summary(period)
                }
                if (_state.value.period != period) return@launch

                val mergedWithCache = mergeStatisticsTracks(local.songs, cachedTracks, connected)
                val hasData = mergedWithCache.isNotEmpty()
                _state.update {
                    if (it.period != period) it else it.copy(
                        localSummary = local,
                        loadedPeriod = period,
                        tracks = mergedWithCache,
                        isLoading = remotePending && !hasData,
                        isRefreshing = force || (remotePending && hasData),
                    )
                }

                if (remotePending && query != null) {
                    val result = withContext(Dispatchers.IO) {
                        repository.refresh(username, query, force)
                    }
                    if (_state.value.period != period) return@launch

                    result.fold(
                        onSuccess = { remoteTracks ->
                            _state.update {
                                if (it.period != period) it else it.copy(
                                    tracks = mergeStatisticsTracks(local.songs, remoteTracks, true),
                                    isLoading = false,
                                    isRefreshing = false,
                                    error = null,
                                    showingStaleLastFmData = false,
                                )
                            }
                        },
                        onFailure = {
                            val stale = repository.cached(query)
                            val hasStaleTracks = !stale?.tracks.isNullOrEmpty()
                            _state.update { current ->
                                if (current.period != period) current else current.copy(
                                    tracks = mergeStatisticsTracks(local.songs, stale?.tracks.orEmpty(), true),
                                    isLoading = false,
                                    isRefreshing = false,
                                    error = if (hasStaleTracks) {
                                        "Last.fm couldn't refresh. Showing saved statistics."
                                    } else {
                                        "Last.fm couldn't refresh. Showing Mylo statistics."
                                    },
                                    showingStaleLastFmData = hasStaleTracks,
                                )
                            }
                        },
                    )
                } else {
                    _state.update {
                        if (it.period != period) it else it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                            showingStaleLastFmData = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    if (it.period != period) it else it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (connected) "Statistics couldn't refresh. Please try again." else null,
                    )
                }
            }
        }
    }
}
