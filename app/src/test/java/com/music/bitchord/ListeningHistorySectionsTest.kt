package com.music.bitchord

import com.music.bitchord.data.ListeningHistory
import com.music.bitchord.data.model.Song
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningHistorySectionsTest {
    private fun song(id: String) = Song(
        videoId = id,
        title = id,
        artist = "Artist",
        thumbnailUrl = null,
    )

    private fun timestamp(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun groupsLocalHistoryByCalendarDayWithoutDeduplicatingRepeatedPlays() {
        val today = LocalDate.of(2026, 11, 12)
        val entries = listOf(
            ListeningHistory.Entry(song("repeat"), timestamp(today)),
            ListeningHistory.Entry(song("repeat"), timestamp(today)),
            ListeningHistory.Entry(song("yesterday"), timestamp(today.minusDays(1))),
            ListeningHistory.Entry(song("older"), timestamp(today.minusDays(3))),
        )

        val sections = ListeningHistory.sections(entries, now = today)

        assertEquals(listOf("Today", "Yesterday"), sections.take(2).map { it.title })
        assertEquals(listOf("repeat", "repeat"), sections[0].songs.map { it.videoId })
        assertEquals(listOf("yesterday"), sections[1].songs.map { it.videoId })
        assertEquals(listOf("older"), sections[2].songs.map { it.videoId })
    }
}
