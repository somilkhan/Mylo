package com.music.bitchord.data.scrobbling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleTimingTest {

    @Test
    fun parsesMinuteSecondDurations() {
        assertEquals(215, ScrobbleTiming.parseDurationSeconds("3:35"))
        assertEquals(215, ScrobbleTiming.parseDurationSeconds("03:35"))
    }

    @Test
    fun parsesHourDurationsInsteadOfReturningZero() {
        assertEquals(3_723, ScrobbleTiming.parseDurationSeconds("1:02:03"))
    }

    @Test
    fun rejectsNonDurationText() {
        assertNull(ScrobbleTiming.parseDurationSeconds(""))
        assertNull(ScrobbleTiming.parseDurationSeconds("3"))
        assertNull(ScrobbleTiming.parseDurationSeconds("a:bc"))
        assertNull(ScrobbleTiming.parseDurationSeconds("-1:00"))
        assertNull(ScrobbleTiming.parseDurationSeconds("1:2:3:4"))
    }

    @Test
    fun trackMustBeLongerThanMinimum() {
        assertFalse(ScrobbleTiming.isEligible(30, 30))
        assertTrue(ScrobbleTiming.isEligible(31, 30))
    }

    @Test
    fun thresholdIsHalfTheTrackForShortTracks() {
        assertEquals(100_000L, ScrobbleTiming.thresholdMillis(200, 0.5f, 180))
    }

    @Test
    fun thresholdIsCappedAtFourMinutes() {
        assertEquals(180_000L, ScrobbleTiming.thresholdMillis(3_600, 0.5f, 180))
    }

    @Test
    fun listenCountsAtHalfOrFourMinutes() {
        assertEquals(false, ScrobbleTiming.isListenCountable(29_000L, 180))
        assertEquals(true, ScrobbleTiming.isListenCountable(90_000L, 180))
        assertEquals(false, ScrobbleTiming.isListenCountable(179_000L, 3_600))
        assertEquals(true, ScrobbleTiming.isListenCountable(180_000L, 3_600))
    }

    @Test
    fun listenOfUnknownDurationCountsAtFourMinutes() {
        assertEquals(false, ScrobbleTiming.isListenCountable(60_000L, null))
        assertEquals(true, ScrobbleTiming.isListenCountable(180_000L, null))
    }

    @Test
    fun shortTrackNeverCounts() {
        assertEquals(false, ScrobbleTiming.isListenCountable(30_000L, 30))
    }
}
