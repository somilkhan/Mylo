package com.music.bitchord.data.scrobbling

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToLong

internal fun shouldRetryScrobbleSubmission(success: Boolean, playbackActive: Boolean, songStarted: Boolean, sameTrack: Boolean): Boolean =
    !success && playbackActive && songStarted && sameTrack

class ScrobbleManager(
    private val scope: CoroutineScope,
    var minSongDuration: Int = 30,
    var scrobbleDelayPercent: Float = 0.5f,
    var scrobbleDelaySeconds: Int = 180,
) {
    private var scrobbleJob: Job? = null
    private var scrobbleRemainingMillis: Long = 0L
    private var scrobbleTimerStartedAt: Long = 0L
    private var songStartedAt: Long = 0L
    private var songStarted = false
    private var currentSongId: String? = null
    private var hasScrobbledCurrentSong = false
    private var scrobbleInFlight = false
    private var playbackActive = false
    private var trackGeneration = 0L
    var useNowPlaying = true
    var usePrimaryArtistOnly = false

    fun destroy() {
        trackGeneration++
        scrobbleJob?.cancel()
        scrobbleRemainingMillis = 0L
        scrobbleTimerStartedAt = 0L
        songStartedAt = 0L
        songStarted = false
        currentSongId = null
        hasScrobbledCurrentSong = false
        scrobbleInFlight = false
        playbackActive = false
    }

    fun onSongStart(
        song: Song?,
        durationMs: Long? = null,
    ) {
        if (song == null) return
        songStartedAt = System.currentTimeMillis() / 1000
        currentSongId = song.videoId
        trackGeneration++
        hasScrobbledCurrentSong = false
        scrobbleInFlight = false
        playbackActive = true
        songStarted = true
        startScrobbleTimer(song, durationMs)
        if (useNowPlaying) {
            updateNowPlaying(song)
        }
    }

    fun onSongResume(song: Song) {
        resumeScrobbleTimer(song)
    }

    fun onSongPause() {
        playbackActive = false
        pauseScrobbleTimer()
    }

    fun onSongStop() {
        trackGeneration++
        stopScrobbleTimer()
        songStarted = false
        currentSongId = null
        hasScrobbledCurrentSong = false
        scrobbleInFlight = false
        playbackActive = false
    }

    private fun startScrobbleTimer(
        song: Song,
        durationMs: Long? = null,
    ) {
        scrobbleJob?.cancel()
        val resolvedDurationSeconds = durationMs?.toInt()?.div(1000)
            ?: song.durationText?.let { parseDurationSeconds(it) }
            ?: return

        if (resolvedDurationSeconds <= minSongDuration) return

        val thresholdMs = (resolvedDurationSeconds * 1000L * scrobbleDelayPercent).roundToLong()
        scrobbleRemainingMillis = min(thresholdMs, scrobbleDelaySeconds * 1000L)

        if (scrobbleRemainingMillis <= 0) {
            scrobbleSong(song, resolvedDurationSeconds)
            return
        }
        scrobbleTimerStartedAt = System.currentTimeMillis()
        scrobbleJob =
            scope.launch {
                delay(scrobbleRemainingMillis)
                scrobbleSong(song, resolvedDurationSeconds)
                scrobbleJob = null
            }
    }

    private fun pauseScrobbleTimer() {
        scrobbleJob?.cancel()
        if (hasScrobbledCurrentSong) {
            scrobbleTimerStartedAt = 0L
            return
        }
        if (scrobbleTimerStartedAt != 0L) {
            val elapsed = System.currentTimeMillis() - scrobbleTimerStartedAt
            scrobbleRemainingMillis -= elapsed
            if (scrobbleRemainingMillis < 0) scrobbleRemainingMillis = 0
            scrobbleTimerStartedAt = 0L
        }
    }

    private fun resumeScrobbleTimer(song: Song) {
        if (hasScrobbledCurrentSong || scrobbleRemainingMillis <= 0) return
        scrobbleJob?.cancel()
        scrobbleTimerStartedAt = System.currentTimeMillis()
        scrobbleJob =
            scope.launch {
                delay(scrobbleRemainingMillis)
                val durationSeconds = song.durationText?.let { parseDurationSeconds(it) } ?: 0
                scrobbleSong(song, durationSeconds)
                scrobbleJob = null
            }
    }

    private fun stopScrobbleTimer() {
        scrobbleJob?.cancel()
        scrobbleJob = null
        scrobbleRemainingMillis = 0
    }

    private fun scrobbleSong(song: Song, durationSeconds: Int) {
        if (hasScrobbledCurrentSong || scrobbleInFlight) return
        scrobbleInFlight = true
        scrobbleTimerStartedAt = 0L
        scrobbleRemainingMillis = 0L
        scrobbleJob = null
        val scrobbleArtist = song.artist.forScrobble()
        val generation = trackGeneration
        val startedAt = songStartedAt
        scope.launch {
            LastFM
                .scrobble(
                    artist = scrobbleArtist,
                    track = song.title,
                    duration = durationSeconds,
                    timestamp = startedAt,
                    album = song.albumName,
                ).onSuccess {
                    Log.d(TAG, "Scrobbled: ${song.title} by ${song.artist}")
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to scrobble: ${song.title}", throwable)
                }.also { result ->
                    if (generation != trackGeneration) return@also
                    scrobbleInFlight = false
                    if (result.isSuccess) hasScrobbledCurrentSong = true
                    else if (shouldRetryScrobbleSubmission(result.isSuccess, playbackActive, songStarted, currentSongId == song.videoId)) {
                        scrobbleRemainingMillis = RETRY_DELAY_MS
                        scrobbleTimerStartedAt = System.currentTimeMillis()
                        scrobbleJob = scope.launch {
                            delay(RETRY_DELAY_MS)
                            if (playbackActive && songStarted && currentSongId == song.videoId) scrobbleSong(song, durationSeconds)
                        }
                    }
                }
        }
    }

    private fun updateNowPlaying(song: Song) {
        val scrobbleArtist = song.artist.forScrobble()
        scope.launch {
            LastFM
                .updateNowPlaying(
                    artist = scrobbleArtist,
                    track = song.title,
                    album = song.albumName,
                    duration = song.durationText?.let { parseDurationSeconds(it) },
                ).onSuccess {
                    Log.d(TAG, "Updated now playing: ${song.title}")
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to update now playing: ${song.title}", throwable)
                }
        }
    }

    fun onPlayerStateChanged(
        isPlaying: Boolean,
        song: Song?,
        durationMs: Long? = null,
    ) {
        if (song == null) return
        if (isPlaying) {
            playbackActive = true
            // Player callbacks can report a track transition without an intervening
            // paused/stopped state. Restart timing and now-playing for the new track.
            if (!songStarted || currentSongId != song.videoId) {
                onSongStart(song, durationMs)
            } else {
                onSongResume(song)
            }
        } else {
            onSongPause()
        }
    }

    /**
     * Parse "M:SS" or "MM:SS" duration text to total seconds.
     */
    private fun parseDurationSeconds(text: String): Int {
        val parts = text.split(":")
        if (parts.size != 2) return 0
        val minutes = parts[0].toIntOrNull() ?: return 0
        val seconds = parts[1].toIntOrNull() ?: return 0
        return minutes * 60 + seconds
    }

    private fun String.forScrobble(): String = if (usePrimaryArtistOnly) primaryArtist() else this

    companion object {
        private const val TAG = "ScrobbleManager"
        private const val RETRY_DELAY_MS = 30_000L
    }
}
