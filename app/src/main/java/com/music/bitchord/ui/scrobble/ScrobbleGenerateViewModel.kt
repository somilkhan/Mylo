package com.music.bitchord.ui.scrobble

import android.app.Application
import com.music.bitchord.R
import com.music.bitchord.data.generate.GenerationAccountSnapshot
import com.music.bitchord.data.generate.GenerationException
import com.music.bitchord.data.generate.generationMessage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.generate.PlaylistGenerationRepository
import com.music.bitchord.data.generate.PlaylistGenerationRequest
import com.music.bitchord.data.generate.PlaylistGenerationMode
import com.music.bitchord.data.generate.PlaylistGenerationRules
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale

data class ScrobbleGenerateUiState(
    val isGenerating: Boolean = false,
    val progress: String = "",
    val error: String? = null,
    val savedPlaylist: LocalPlaylist? = null,
    val activeRequest: PlaylistGenerationRequest? = null,
)

/**
 * Screen-scoped generation state. Dismissing Scrobble must call
 * [cancelGeneration]; ViewModel teardown also cancels the active request.
 */
class ScrobbleGenerateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlaylistGenerationRepository(application)

    private val _uiState = MutableStateFlow(ScrobbleGenerateUiState())
    val uiState = _uiState.asStateFlow()

    private val _savedPlaylistEvents = MutableSharedFlow<LocalPlaylist>(replay = 1, extraBufferCapacity = 1)
    val savedPlaylistEvents = _savedPlaylistEvents.asSharedFlow()

    private val _pendingStartMixQueue = MutableStateFlow<LocalPlaylist?>(null)
    val pendingStartMixQueue = _pendingStartMixQueue.asStateFlow()

    private var generationJob: Job? = null
    private var generationToken = 0L
    private var queueMixOnSave = false
    private var retryStartMixRequest: PlaylistGenerationRequest? = null

    /** Starts a one-tap mix from any global song action without touching playback during discovery. */
    fun startMixWithSong(song: Song) {
        if (!PlaylistGenerationRules.canStartMixWithSong(song)) {
            _uiState.update { it.copy(error = getApplication<Application>().getString(R.string.scrobble_gen_error_song_no_mix)) }
            return
        }
        if (generationJob?.isActive == true) {
            _uiState.update { it.copy(error = getApplication<Application>().getString(R.string.scrobble_gen_error_already_running)) }
            return
        }
        queueMixOnSave = true
        generate(
            PlaylistGenerationRequest(
                mode = PlaylistGenerationMode.SIMILAR_TRACKS,
                count = START_MIX_TRACK_COUNT,
                seedTrack = song,
            ),
        )
    }

    fun consumeStartMixQueue(playlistId: String) {
        if (_pendingStartMixQueue.value?.id == playlistId) {
            _pendingStartMixQueue.value = null
        }
    }

    fun generate(request: PlaylistGenerationRequest) {
        if (generationJob?.isActive == true) return
        val queueAfterSave = queueMixOnSave || retryStartMixRequest == request
        // Retain playback intent only for an explicit retry of this exact Start mix.
        queueMixOnSave = false
        retryStartMixRequest = if (queueAfterSave) request else null
        val accounts = GenerationAccountSnapshot(getApplication<Application>())
        val token = ++generationToken
        _uiState.update {
            it.copy(
                isGenerating = true,
                progress = getApplication<Application>().getString(R.string.scrobble_gen_progress_preparing),
                error = null,
                savedPlaylist = null,
                activeRequest = request,
            )
        }

        generationJob = viewModelScope.launch {
            try {
                val playlist = generateAndCommit(
                    generate = {
                        withTimeout(GENERATION_TIMEOUT_MS) {
                            repository.generate(request) { message ->
                                if (generationToken == token) {
                                    _uiState.update { it.copy(progress = message) }
                                }
                            }
                        }
                    },
                    commit = { songs ->
                        // Storage may suspend on its mutex/IO; only begin while this run is active.
                        currentCoroutineContext().ensureActive()
                        if (generationToken != token) throw CancellationException()
                        accounts.ensureUnchanged()
                        val title = uniquePlaylistTitle(request, LocalPlaylistStore.playlists.value.map { it.title })
                        saveGeneratedPlaylist(title, songs, request.mode.storageValue)
                    },
                )
                if (generationToken == token) {
                    retryStartMixRequest = null
                    _uiState.update { it.copy(savedPlaylist = playlist, error = null) }
                    if (queueAfterSave) {
                        // A Start mix stays on Generate: it queues playback instead of
                        // emitting the saved-playlist handoff that moves to Playlists.
                        _pendingStartMixQueue.value = playlist
                    } else {
                        _savedPlaylistEvents.emit(playlist)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                if (generationToken == token) {
                    _uiState.update { it.copy(error = getApplication<Application>().getString(R.string.scrobble_gen_error_timeout)) }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (generationToken == token) {
                    _uiState.update { it.copy(error = error.generationMessage(getApplication<Application>(), R.string.scrobble_gen_error_generic)) }
                }
            } finally {
                if (generationToken == token) {
                    generationJob = null
                    _uiState.update { it.copy(isGenerating = false, progress = "") }
                }
            }
        }
    }

    /** Creates a new playlist inspired by the source; the source itself is never edited. */
    fun generateSimilar(source: LocalPlaylist) {
        if (generationJob?.isActive == true || source.songs.isEmpty()) return
        val token = ++generationToken
        queueMixOnSave = false
        retryStartMixRequest = null
        val accounts = GenerationAccountSnapshot(getApplication<Application>())
        _uiState.update {
            it.copy(
                isGenerating = true,
                progress = getApplication<Application>().getString(R.string.scrobble_gen_progress_similar),
                error = null,
                savedPlaylist = null,
                activeRequest = null,
            )
        }

        generationJob = viewModelScope.launch {
            try {
                val songs = withTimeout(GENERATION_TIMEOUT_MS) {
                    repository.generateSimilar(source.songs) { message ->
                        if (generationToken == token) {
                            _uiState.update { it.copy(progress = message) }
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                if (generationToken != token) throw CancellationException()
                accounts.ensureUnchanged()
                val title = uniquePlaylistTitle(
                    "${source.title} Mix",
                    LocalPlaylistStore.playlists.value.map { it.title },
                )
                val playlist = saveGeneratedPlaylist(title, songs, "similar-playlist")
                currentCoroutineContext().ensureActive()
                if (generationToken == token) {
                    _uiState.update { it.copy(savedPlaylist = playlist, error = null) }
                    _savedPlaylistEvents.emit(playlist)
                }
            } catch (_: TimeoutCancellationException) {
                if (generationToken == token) {
                    _uiState.update { it.copy(error = getApplication<Application>().getString(R.string.scrobble_gen_error_similar_timeout)) }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (generationToken == token) {
                    _uiState.update { it.copy(error = error.generationMessage(getApplication<Application>(), R.string.scrobble_gen_error_similar_generic)) }
                }
            } finally {
                if (generationToken == token) {
                    generationJob = null
                    _uiState.update { it.copy(isGenerating = false, progress = "") }
                }
            }
        }
    }

    /** Cancels all in-flight generation before the Scrobble surface is dismissed. */
    fun cancelGeneration() {
        queueMixOnSave = false
        retryStartMixRequest = null
        _pendingStartMixQueue.value = null
        generationToken++
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isGenerating = false, progress = "", error = null, activeRequest = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearResult() {
        if (_uiState.value.isGenerating) return
        queueMixOnSave = false
        retryStartMixRequest = null
        _uiState.update { it.copy(savedPlaylist = null, error = null, activeRequest = null) }
    }

    private suspend fun saveGeneratedPlaylist(title: String, songs: List<Song>, mode: String): LocalPlaylist = try {
        LocalPlaylistStore.savePlaylist(title = title, songs = songs, generationMode = mode)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        throw GenerationException(R.string.scrobble_gen_error_save)
    }

    override fun onCleared() {
        cancelGeneration()
        super.onCleared()
    }

    private companion object {
        const val GENERATION_TIMEOUT_MS = 60_000L
        const val START_MIX_TRACK_COUNT = 28
    }
}

internal fun uniquePlaylistTitle(
    request: PlaylistGenerationRequest,
    existingTitles: List<String>,
): String {
    val base = when (request.mode) {
        PlaylistGenerationMode.TOP -> "My Top Tracks"
        PlaylistGenerationMode.RECENT -> "Recently Played"
        PlaylistGenerationMode.SIMILAR_TRACKS -> "Similar to ${request.seedTrack?.title?.trim().orEmpty()}".trim()
        PlaylistGenerationMode.SIMILAR_ARTISTS -> "${request.seedArtist.trim()} Mix"
        PlaylistGenerationMode.TAG -> "${request.tag.trim()} Mix"
        PlaylistGenerationMode.MIX -> "My Mix"
        PlaylistGenerationMode.RECOMMENDATIONS -> "Made for You"
        PlaylistGenerationMode.NEVER_HEARD -> "Never Heard"
        PlaylistGenerationMode.LIBRARY -> "My Library Mix"
        PlaylistGenerationMode.FORGOTTEN_FAVORITES -> "Forgotten Favorites"
        PlaylistGenerationMode.TAG_TOP -> "${request.tag.trim()} Top Tracks"
        PlaylistGenerationMode.TOP_ARTISTS_MIX -> "Top Artists Mix"
    }.take(64).ifBlank { "My Mix" }
    return uniquePlaylistTitle(base, existingTitles)
}

internal fun uniquePlaylistTitle(baseTitle: String, existingTitles: List<String>): String {
    val base = baseTitle.trim().take(64).ifBlank { "My Mix" }
    val existing = existingTitles.mapTo(HashSet()) { it.trim().lowercase(Locale.ROOT) }
    if (base.lowercase(Locale.ROOT) !in existing) return base
    var suffix = 2
    while ("${base.take(58)} ($suffix)".lowercase(Locale.ROOT) in existing) suffix++
    return "${base.take(58)} ($suffix)"
}
