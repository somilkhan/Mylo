package com.music.bitchord.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListeningRecorderTest {
    @Test
    fun qualifiedPlayUsesAudibleStartRatherThanThresholdCrossingTime() {
        val clock = ListeningPlayClock()
        clock.start("track-a", 1_000L)

        val first = clock.sample("track-a", 5_000L, thresholdMillis = 10_000L)
        assertEquals(4_000L, first.elapsedMillis)
        assertNull(first.playStartedAtMillis)

        val qualified = clock.sample("track-a", 11_000L, thresholdMillis = 10_000L)
        assertEquals(6_000L, qualified.elapsedMillis)
        assertEquals(1_000L, qualified.playStartedAtMillis)

        val later = clock.sample("track-a", 16_000L, thresholdMillis = 10_000L)
        assertNull(later.playStartedAtMillis)
    }

    @Test
    fun firstSampleStillProvidesAnAnchorWhenNoAudibleCallbackWasDelivered() {
        val clock = ListeningPlayClock()

        assertEquals(0L, clock.sample("track-a", 5_000L, 1_000L).elapsedMillis)
        val qualified = clock.sample("track-a", 6_000L, 1_000L)

        assertEquals(5_000L, qualified.playStartedAtMillis)
    }

    @Test
    fun resetAfterPauseDoesNotCountThePausedGap() {
        val clock = ListeningPlayClock()
        clock.start("track-a", 1_000L)
        clock.sample("track-a", 6_000L, 10_000L)

        clock.reset()
        assertEquals(0L, clock.sample("track-a", 100_000L, 5_000L).elapsedMillis)
        val resumed = clock.sample("track-a", 105_000L, 5_000L)

        assertEquals(100_000L, resumed.playStartedAtMillis)
        assertEquals(5_000L, resumed.elapsedMillis)
    }

    @Test
    fun changingTracksStartsANewClockEvenWhenThePreviousTrackQualified() {
        val clock = ListeningPlayClock()
        clock.start("track-a", 1_000L)
        clock.sample("track-a", 6_000L, 5_000L)

        assertEquals(0L, clock.sample("track-b", 7_000L, 1_000L).elapsedMillis)
        val next = clock.sample("track-b", 8_000L, 1_000L)

        assertEquals(7_000L, next.playStartedAtMillis)
    }
}
