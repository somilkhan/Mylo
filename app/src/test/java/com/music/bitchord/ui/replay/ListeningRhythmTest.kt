package com.music.bitchord.ui.replay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListeningRhythmTest {
    @Test
    fun preservesAllTwentyFourHourlyBucketsAndFindsPeak() {
        val hours = List(24) { hour -> if (hour == 19) 90_000L else if (hour == 8) 30_000L else 0L }

        val result = calculateListeningRhythm(hours)

        assertEquals(24, result.hours.size)
        assertEquals(120_000L, result.totalMs)
        assertEquals(19, result.peakHour)
        assertEquals(90_000L, result.peakDurationMs)
    }

    @Test
    fun padsMissingBucketsAndClampsNegativeDurations() {
        val result = calculateListeningRhythm(listOf(10L, -5L, 20L))

        assertEquals(24, result.hours.size)
        assertEquals(listOf(10L, 0L, 20L), result.hours.take(3))
        assertEquals(30L, result.totalMs)
        assertEquals(2, result.peakHour)
    }

    @Test
    fun zeroListeningHasNoPeak() {
        val result = calculateListeningRhythm(emptyList())

        assertEquals(0L, result.totalMs)
        assertNull(result.peakHour)
        assertEquals(0L, result.peakDurationMs)
    }
}
