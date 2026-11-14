package com.music.bitchord.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrobbleStatsLogicTest {
    @Test
    fun groupsHourlyListeningIntoDaypartsAndPreservesTotal() {
        val parts = buildListeningDayParts((1L..24L).toList())

        assertEquals(listOf("Night", "Morning", "Afternoon", "Evening"), parts.map { it.name })
        assertEquals(listOf("00–05", "06–11", "12–17", "18–23"), parts.map { it.timeRange })
        assertEquals(listOf(21L, 57L, 93L, 129L), parts.map { it.listeningMs })
        assertEquals(300L, parts.sumOf { it.listeningMs })
    }

    @Test
    fun percentagesRepresentRecordedListeningTime() {
        val parts = buildListeningDayParts((1L..24L).toList())
        val total = parts.sumOf { it.listeningMs }

        assertEquals(listOf(7, 19, 31, 43), parts.map { it.percentageOf(total) })
    }

    @Test
    fun emptyAndShortHourlyDataAreSafe() {
        val empty = buildListeningDayParts(emptyList())
        assertEquals(listOf(0L, 0L, 0L, 0L), empty.map { it.listeningMs })
        assertEquals(listOf(0, 0, 0, 0), empty.map { it.percentageOf(0L) })

        val short = buildListeningDayParts(listOf(-5L, 5L, 10L))
        assertEquals(15L, short.first().listeningMs)
        assertEquals(listOf(0L, 0L, 0L), short.drop(1).map { it.listeningMs })
    }
}
