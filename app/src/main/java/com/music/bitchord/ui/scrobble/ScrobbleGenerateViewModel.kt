package com.music.bitchord.ui.scrobble

import android.app.Application
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

    /** Starts a one-tap mix from any global song action without touching playback during discovery. */
    fun startMixWithSong(song: Song) {
        if (!PlaylistGenerationRules.canStartMixWithSong(song)) {
            _uiState.update { it.copy(error = "This song cannot start a mix. Choose a playable track.") }
            return
        }
        if (generationJob?.isActive == true) {
            _uiState.update { it.copy(error = "A playlist is already being generated.") }
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
        val queueAfterSave = queueMixOnSave
        // Consumed by this run only: a failed or cancelled Start mix must not
        // leave the flag set for a later, unrelated generation to pick up.
        queueMixOnSave = false
        val token = ++generationToken
        _uiState.update {
            it.copy(
                isGenerating = true,
                progress = "Preparing your playlist…",
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
                        // This synchronous commit is reached only after the job is
                        // confirmed active. Closing the screen cancels before it.
                        check(generationToken == token) { "Generation was dismissed" }
                        val title = uniquePlaylistTitle(request, LocalPlaylistStore.playlists.value.map { it.title })
                        LocalPlaylistStore.savePlaylist(
                            title = title,
                            songs = songs,
                            generationMode = request.mode.storageValue,
                        )
                    },
                )
                if (generationToken == token) {
                    _uiState.update { it.copy(savedPlaylist = playlist) }
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
                    _uiState.update { it.copy(error = "Playlist generation timed out. Check your connection and try again.") }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (generationToken == token) {
                    _uiState.update { it.copy(error = error.message ?: "Couldn't generate a playlist") }
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
        _uiState.update {
            it.copy(
                isGenerating = true,
                progress = "Preparing a similar mix…",
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
                check(generationToken == token) { "Generation was dismissed" }
                val title = uniquePlaylistTitle(
                    "${source.title} Mix",
                    LocalPlaylistStore.playlists.value.map { it.title },
                )
                val playlist = LocalPlaylistStore.savePlaylist(
                    title = title,
                    songs = songs,
                    generationMode = "similar-playlist",
                )
                if (generationToken == token) {
                    _uiState.update { it.copy(savedPlaylist = playlist) }
                    _savedPlaylistEvents.emit(playlist)
                }
            } catch (_: TimeoutCancellationException) {
                if (generationToken == token) {
                    _uiState.update { it.copy(error = "Similar playlist generation timed out. Check your connection and try again.") }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (generationToken == token) {
                    _uiState.update { it.copy(error = error.message ?: "Couldn't create a similar playlist") }
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
        generationToken++
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isGenerating = false, progress = "", error = null, activeRequest = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearResult() {
        queueMixOnSave = false
        _uiState.update { it.copy(savedPlaylist = null, error = null, activeRequest = null) }
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
