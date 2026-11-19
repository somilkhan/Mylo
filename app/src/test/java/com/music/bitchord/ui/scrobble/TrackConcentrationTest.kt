package com.music.bitchord.ui.scrobble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackConcentrationTest {
    @Test
    fun percentagesRepresentTopOneNextTwoAndRemainingMergedPlays() {
        val result = calculateTrackConcentration(listOf(40L, 30L, 20L, 10L))!!

        assertEquals(40, result.topTrackPercent)
        assertEquals(50, result.nextTwoPercent)
        assertEquals(10, result.otherTracksPercent)
        assertEquals(100, result.topTrackPercent + result.nextTwoPercent + result.otherTracksPercent)
        assertEquals(40L, result.topTrackPlays)
        assertEquals(50L, result.nextTwoPlays)
        assertEquals(10L, result.otherTracksPlays)
        assertEquals(100L, result.totalPlays)
        assertEquals(4, result.uniqueTracks)
        assertEquals("25.0", result.averagePlaysPerTrack)
    }

    @Test
    fun oneTrackAssignsAllPlaysToTopTrack() {
        val result = calculateTrackConcentration(listOf(3L))!!

        assertEquals(100, result.topTrackPercent)
        assertEquals(0, result.nextTwoPercent)
        assertEquals(0, result.otherTracksPercent)
        assertEquals(3L, result.topTrackPlays)
    }

    @Test
    fun emptyOrZeroPlayInputHasNoConcentration() {
        assertNull(calculateTrackConcentration(emptyList()))
        assertNull(calculateTrackConcentration(listOf(0L, -4L)))
    }

    @Test
    fun negativePlayCountsCannotProduceNegativeShares() {
        val result = calculateTrackConcentration(listOf(100L, -20L))!!

        assertEquals(100, result.topTrackPercent)
        assertEquals(0, result.nextTwoPercent)
        assertEquals(0, result.otherTracksPercent)
        assertEquals(0L, result.otherTracksPlays)
    }
}
