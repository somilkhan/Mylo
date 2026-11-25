package com.music.bitchord.data.scrobbling

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrobbleManagerTest {

    private fun song(title: String = "Song", duration: String? = "3:20") = Song(
        videoId = title,
        title = title,
        artist = "Artist",
        thumbnailUrl = null,
        durationText = duration,
    )

    private fun TestScope.manager(submitted: MutableList<LastFmScrobbleEntry>) = ScrobbleManager(
        scope = this,
        clock = { testScheduler.currentTime },
        submit = { submitted += it },
    ).apply { useNowPlaying = false }

    /** Advance virtual time and run anything scheduled exactly at the new time. */
    private fun TestScope.elapse(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun scrobblesOnceAfterHalfOfTheTrack() = runTest {
        val submitted = mutableListOf<LastFmScrobbleEntry>()
        val manager = manager(submitted)
        advanceTimeBy(1_000_000L) // start at epoch 1000 s

        manager.onSongStart(song())
        elapse(99_999L)
        assertEquals(0, submitted.size)

        elapse(1L) // 200 s track, half is 100 s
        assertEquals(1, submitted.size)
        assertEquals(1_000L, submitted.single().timestamp)
    }

    @Test
    fun settingsChangeWhilePlayingDoesNotDelayScrobble() = runTest {
        val submitted = mutableListOf<LastFmScrobbleEntry>()
        val manager = manager(submitted)
        val playing = song()

        manager.onSongStart(playing)
        elapse(60_000L)
        // A settings emission re-drives the manager while the song keeps playing.
        manager.onSettingsChanged(isPlaying = true, song = playing, durationMs = null)
        elapse(39_999L)
        assertEquals(0, submitted.size)

        elapse(1L) // still due at 100 s after the start, not 160 s
        assertEquals(1, submitted.size)
    }

    @Test
    fun pausedTimeIsNotCounted() = runTest {
        val submitted = mutableListOf<LastFmScrobbleEntry>()
        val manager = manager(submitted)

        manager.onSongStart(song())
        elapse(30_000L)
        manager.onSongPause()
        elapse(1_000_000L)
        manager.onSongResume()

        elapse(69_999L) // 70 s still due after resume
        assertEquals(0, submitted.size)

        elapse(1L)
        assertEquals(1, submitted.size)
    }

    @Test
    fun resumeAfterScrobbleDoesNotSubmitAgain() = runTest {
        val submitted = mutableListOf<LastFmScrobbleEntry>()
        val manager = manager(submitted)

        manager.onSongStart(song())
        elapse(100_000L)
        assertEquals(1, submitted.size)

        manager.onSongPause()
        manager.onSongResume()
        elapse(500_000L)
        assertEquals(1, submitted.size)
    }

    @Test
    fun hourLongTrackScrobblesAtFourMinutes() = runTest {
        val submitted = mutableListOf<LastFmScrobbleEntry>()
        val manager = manager(submitted)

        manager.onSongStart(song(duration = "1:02:03"))
        elapse(179_999L)
        assertEquals(0, submitted.size)

        elapse(1L) // half of 1 h is more than the 4-minute cap
        assertEquals(1, submitted.size)
        assertEquals(3_723, submitted.single().duration)
    }

    @Test
    fun trackOfThirtySecondsOrLessIsNotScrobbled() = runTest {
        val submitted = mutableListOf<LastFmScrobbleEntry>()
        val manager = manager(submitted)

        manager.onSongStart(song(duration = "0:30"))
        elapse(1_000_000L)
        assertEquals(0, submitted.size)
    }

    @Test
    fun changingTrackScrobblesOnlyTheNewOne() = runTest {
        val submitted = mutableListOf<LastFmScrobbleEntry>()
        val manager = manager(submitted)

        manager.onSongStart(song("first"))
        elapse(50_000L)
        manager.onSongStop()
        manager.onSongStart(song("second"))
        elapse(100_000L)

        assertEquals(listOf("second"), submitted.map { it.track })
    }

    @Test
    fun settingsDoNotStartTrackingWhilePaused() = runTest {
        val submitted = mutableListOf<LastFmScrobbleEntry>()
        val manager = manager(submitted)

        manager.onSettingsChanged(isPlaying = false, song = song(), durationMs = null)
        elapse(1_000_000L)
        assertEquals(0, submitted.size)
    }

    @Test
    fun unknownDurationAtStartScrobblesOnceDurationIsKnown() = runTest {
        val submitted = mutableListOf<LastFmScrobbleEntry>()
        val manager = manager(submitted)

        manager.onSongStart(song(duration = null))
        elapse(10_000L)
        // The player reports the duration a moment later.
        manager.onPlayerStateChanged(isPlaying = true, song = song(duration = null), durationMs = 200_000L)

        elapse(99_999L) // 100 s threshold counted from the moment the duration became known
        assertEquals(0, submitted.size)
        elapse(1L)
        assertEquals(1, submitted.size)
    }

    @Test
    fun resumeWhileCountingDoesNotRearm() = runTest {
        val submitted = mutableListOf<LastFmScrobbleEntry>()
        val manager = manager(submitted)

        manager.onSongStart(song())
        elapse(60_000L)
        manager.onSongResume()
        elapse(39_999L)
        assertEquals(0, submitted.size)
        elapse(1L)
        assertEquals(1, submitted.size)
    }
}
