package com.music.bitchord.ui.scrobble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackConcentrationTest {
    @Test
    fun percentagesRepresentTopOneNextTwoAndRemainingTracks() {
        val result = calculateTrackConcentration(
            trackDurationsMs = listOf(40L, 30L, 20L, 10L),
            totalListeningMs = 100L,
            totalPlays = 20,
        )!!

        assertEquals(40, result.topTrackPercent)
        assertEquals(50, result.nextTwoPercent)
        assertEquals(10, result.otherTracksPercent)
        assertEquals(100, result.topTrackPercent + result.nextTwoPercent + result.otherTracksPercent)
        assertEquals(40L, result.topTrackMs)
        assertEquals(50L, result.nextTwoMs)
        assertEquals(10L, result.otherTracksMs)
        assertEquals("5.0", result.averagePlaysPerTrack)
    }

    @Test
    fun oneTrackAssignsRemainingShareToTopTrack() {
        val result = calculateTrackConcentration(listOf(120L), 120L, 3)!!

        assertEquals(100, result.topTrackPercent)
        assertEquals(0, result.nextTwoPercent)
        assertEquals(0, result.otherTracksPercent)
    }

    @Test
    fun emptyOrZeroDurationInputHasNoConcentration() {
        assertNull(calculateTrackConcentration(emptyList(), 100L, 4))
        assertNull(calculateTrackConcentration(listOf(100L), 0L, 4))
    }

    @Test
    fun negativeTrackDurationsCannotProduceNegativeShares() {
        val result = calculateTrackConcentration(listOf(100L, -20L), 100L, 2)!!

        assertEquals(100, result.topTrackPercent)
        assertEquals(0, result.nextTwoPercent)
        assertEquals(0, result.otherTracksPercent)
        assertEquals(0L, result.otherTracksMs)
    }
}
