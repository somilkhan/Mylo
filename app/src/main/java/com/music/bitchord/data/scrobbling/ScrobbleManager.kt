package com.music.bitchord.data.scrobbling

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    private var scrobbled = false
    var useNowPlaying = true
    var usePrimaryArtistOnly = false

    fun destroy() {
        scrobbleJob?.cancel()
        scrobbleRemainingMillis = 0L
        scrobbleTimerStartedAt = 0L
        songStartedAt = 0L
        songStarted = false
        scrobbled = false
    }

    fun onSongStart(
        song: Song?,
        durationMs: Long? = null,
    ) {
        if (song == null) return
        songStartedAt = System.currentTimeMillis() / 1000
        songStarted = true
        scrobbled = false
        startScrobbleTimer(song, durationMs)
        if (useNowPlaying) {
            updateNowPlaying(song)
        }
    }

    fun onSongResume(song: Song) {
        resumeScrobbleTimer(song)
    }

    fun onSongPause() {
        pauseScrobbleTimer()
    }

    fun onSongStop() {
        stopScrobbleTimer()
        songStarted = false
    }

    private fun startScrobbleTimer(
        song: Song,
        durationMs: Long? = null,
    ) {
        scrobbleJob?.cancel()
        if (scrobbled) return
        val resolvedDurationSeconds = durationMs?.toInt()?.div(1000)
            ?: song.durationText?.let { ScrobbleTiming.parseDurationSeconds(it) }
            ?: return

        if (!ScrobbleTiming.isEligible(resolvedDurationSeconds, minSongDuration)) return

        scrobbleRemainingMillis = ScrobbleTiming.thresholdMillis(
            resolvedDurationSeconds,
            scrobbleDelayPercent,
            scrobbleDelaySeconds,
        )

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
        if (scrobbleTimerStartedAt != 0L) {
            val elapsed = System.currentTimeMillis() - scrobbleTimerStartedAt
            scrobbleRemainingMillis -= elapsed
            if (scrobbleRemainingMillis < 0) scrobbleRemainingMillis = 0
            scrobbleTimerStartedAt = 0L
        }
    }

    private fun resumeScrobbleTimer(song: Song) {
        // Once a song has been scrobbled, a resume must not submit it again.
        if (scrobbled || scrobbleRemainingMillis <= 0) return
        scrobbleJob?.cancel()
        scrobbleTimerStartedAt = System.currentTimeMillis()
        scrobbleJob =
            scope.launch {
                delay(scrobbleRemainingMillis)
                val durationSeconds = song.durationText?.let { ScrobbleTiming.parseDurationSeconds(it) } ?: 0
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
        if (scrobbled) return
        scrobbled = true
        scrobbleRemainingMillis = 0L
        val entry = LastFmScrobbleEntry(
            artist = song.artist.forScrobble(),
            track = song.title,
            timestamp = songStartedAt,
            album = song.albumName,
            duration = durationSeconds.takeIf { it > 0 },
        )
        LastFmScrobbleQueue.submit(entry)
    }

    private fun updateNowPlaying(song: Song) {
        val scrobbleArtist = song.artist.forScrobble()
        scope.launch {
            LastFM
                .updateNowPlaying(
                    artist = scrobbleArtist,
                    track = song.title,
                    album = song.albumName,
                    duration = song.durationText?.let { ScrobbleTiming.parseDurationSeconds(it) },
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
            if (!songStarted) {
                onSongStart(song, durationMs)
            } else {
                onSongResume(song)
            }
        } else {
            onSongPause()
        }
    }

    private fun String.forScrobble(): String = if (usePrimaryArtistOnly) primaryArtist() else this

    companion object {
        private const val TAG = "ScrobbleManager"
    }
}
