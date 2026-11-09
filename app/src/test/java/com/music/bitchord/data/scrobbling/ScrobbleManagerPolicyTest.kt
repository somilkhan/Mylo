package com.music.bitchord.data.scrobbling

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleManagerPolicyTest {
    @Test
    fun failedSubmissionRetriesForTheStillCurrentTrackEvenWhenPaused() {
        assertTrue(shouldRetryScrobbleSubmission(success = false, playbackActive = true, songStarted = true, sameTrack = true))
        assertTrue(shouldRetryScrobbleSubmission(success = false, playbackActive = false, songStarted = true, sameTrack = true))
        assertFalse(shouldRetryScrobbleSubmission(success = true, playbackActive = true, songStarted = true, sameTrack = true))
        assertFalse(shouldRetryScrobbleSubmission(success = false, playbackActive = false, songStarted = false, sameTrack = true))
        assertFalse(shouldRetryScrobbleSubmission(success = false, playbackActive = true, songStarted = true, sameTrack = false))
    }
}
