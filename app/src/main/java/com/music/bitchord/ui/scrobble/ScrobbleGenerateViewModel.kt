package com.music.bitchord.ui.scrobble

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.generate.PlaylistGenerationRepository
import com.music.bitchord.data.generate.PlaylistGenerationRequest
import com.music.bitchord.data.generate.PlaylistGenerationMode
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
)

/**
 * Screen-scoped generation state. Dismissing Scrobble must call
 * [cancelGeneration]; ViewModel teardown also cancels the active request.
 */
class ScrobbleGenerateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlaylistGenerationRepository(application)

    private val _uiState = MutableStateFlow(ScrobbleGenerateUiState())
    val uiState = _uiState.asStateFlow()

    private val _savedPlaylistEvents = MutableSharedFlow<LocalPlaylist>(extraBufferCapacity = 1)
    val savedPlaylistEvents = _savedPlaylistEvents.asSharedFlow()

    private var generationJob: Job? = null
    private var generationToken = 0L

    fun generate(request: PlaylistGenerationRequest) {
        if (generationJob?.isActive == true) return
        val token = ++generationToken
        _uiState.update { it.copy(isGenerating = true, progress = "Preparing your playlist…", error = null) }

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
                    _savedPlaylistEvents.tryEmit(playlist)
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

    /** Cancels all in-flight generation before the Scrobble surface is dismissed. */
    fun cancelGeneration() {
        generationToken++
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isGenerating = false, progress = "", error = null) }
    }

    fun clearResult() {
        _uiState.update { it.copy(savedPlaylist = null, error = null) }
    }

    override fun onCleared() {
        cancelGeneration()
        super.onCleared()
    }

    private companion object {
        const val GENERATION_TIMEOUT_MS = 60_000L
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
    val existing = existingTitles.mapTo(HashSet()) { it.trim().lowercase(Locale.ROOT) }
    if (base.lowercase(Locale.ROOT) !in existing) return base
    var suffix = 2
    while ("${base.take(58)} ($suffix)".lowercase(Locale.ROOT) in existing) suffix++
    return "${base.take(58)} ($suffix)"
}
