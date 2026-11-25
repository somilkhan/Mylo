package com.music.bitchord.data.scrobbling

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the scrobble timer for the song that is playing.
 *
 * The timer counts only while the song plays: [onSongPause] stores what is left,
 * [onSongResume] continues from there, and a song is submitted at most once.
 * [clock] (epoch ms) and [submit] are injectable so the rules can be tested in virtual time.
 */
internal class ScrobbleManager(
    private val scope: CoroutineScope,
    var minSongDuration: Int = 30,
    var scrobbleDelayPercent: Float = 0.5f,
    var scrobbleDelaySeconds: Int = 180,
    private val clock: () -> Long = System::currentTimeMillis,
    private val submit: (LastFmScrobbleEntry) -> Unit = { entry -> LastFmScrobbleQueue.submit(entry) },
) {
    private var scrobbleJob: Job? = null
    private var currentSong: Song? = null
    private var songDurationSeconds = 0
    private var songStartedAt = 0L // Last.fm timestamp: epoch seconds of the listen's start
    private var songStarted = false
    private var scrobbled = false

    /** True once the current song has a threshold timer. A song can start with its duration unknown. */
    private var armed = false

    /** Milliseconds still needed before the scrobble, as of [scrobbleTimerStartedAt]. */
    private var scrobbleRemainingMillis = 0L

    /** Epoch ms when the timer last started counting, or null while paused or stopped. */
    private var scrobbleTimerStartedAt: Long? = null

    var useNowPlaying = true
    var usePrimaryArtistOnly = false

    fun destroy() {
        cancelTimer()
        currentSong = null
        songStarted = false
        scrobbled = false
        armed = false
    }

    fun onSongStart(song: Song?, durationMs: Long? = null) {
        if (song == null) return
        cancelTimer()
        songStartedAt = clock() / 1000
        songStarted = true
        scrobbled = false
        armed = false
        currentSong = song
        armFor(song, durationMs)
        if (useNowPlaying) {
            updateNowPlaying(song)
        }
    }

    fun onSongPause() {
        pauseTimer()
    }

    fun onSongResume(durationMs: Long? = null) {
        // Already counting (for example, a settings change while playing): leave it alone,
        // otherwise it would re-arm with its full remaining time and delay the scrobble.
        if (scrobbleJob?.isActive == true) return
        val song = currentSong
        if (!armed && song != null) {
            // The duration was unknown at start and may be known now.
            armFor(song, durationMs)
            return
        }
        startCountingFromRemaining()
    }

    fun onSongStop() {
        cancelTimer()
        scrobbleRemainingMillis = 0L
        currentSong = null
        songStarted = false
        armed = false
    }

    /**
     * Called when a setting changes while the player is in some state. It starts tracking only a
     * song that is not yet tracked and is playing. It never re-drives a running timer.
     */
    fun onSettingsChanged(isPlaying: Boolean, song: Song?, durationMs: Long? = null) {
        if (!isPlaying || song == null) return
        if (!songStarted) onSongStart(song, durationMs) else if (!armed) onSongResume(durationMs)
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
                onSongResume(durationMs)
            }
        } else {
            onSongPause()
        }
    }

    private fun armFor(song: Song, durationMs: Long?) {
        val duration = durationMs?.let { (it / 1000).toInt() }
            ?: song.durationText?.let { ScrobbleTiming.parseDurationSeconds(it) }
            ?: return
        if (!ScrobbleTiming.isEligible(duration, minSongDuration)) return
        songDurationSeconds = duration
        scrobbleRemainingMillis = ScrobbleTiming.thresholdMillis(
            duration,
            scrobbleDelayPercent,
            scrobbleDelaySeconds,
        )
        armed = true
        startCountingFromRemaining()
    }

    private fun startCountingFromRemaining() {
        if (scrobbled || scrobbleRemainingMillis <= 0L) return
        scrobbleJob?.cancel()
        scrobbleTimerStartedAt = clock()
        val waitMillis = scrobbleRemainingMillis
        scrobbleJob = scope.launch {
            delay(waitMillis)
            scrobbleCurrent()
        }
    }

    private fun pauseTimer() {
        scrobbleJob?.cancel()
        scrobbleJob = null
        scrobbleTimerStartedAt?.let { startedAt ->
            val elapsed = clock() - startedAt
            scrobbleRemainingMillis = (scrobbleRemainingMillis - elapsed).coerceAtLeast(0L)
        }
        scrobbleTimerStartedAt = null
    }

    private fun cancelTimer() {
        scrobbleJob?.cancel()
        scrobbleJob = null
        scrobbleRemainingMillis = 0L
        scrobbleTimerStartedAt = null
    }

    private fun scrobbleCurrent() {
        val song = currentSong ?: return
        if (scrobbled) return
        scrobbled = true
        scrobbleRemainingMillis = 0L
        scrobbleTimerStartedAt = null
        scrobbleJob = null
        submit(
            LastFmScrobbleEntry(
                artist = song.artist.forScrobble(),
                track = song.title,
                timestamp = songStartedAt,
                album = song.albumName,
                duration = songDurationSeconds.takeIf { it > 0 },
            ),
        )
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

    private fun String.forScrobble(): String = if (usePrimaryArtistOnly) primaryArtist() else this

    companion object {
        private const val TAG = "ScrobbleManager"
    }
}
