package com.music.bitchord.data

import com.music.bitchord.data.model.Song
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test
import org.junit.Assert.assertEquals

class ListeningHistoryTest {
    @Test
    fun groupsLocalPlaysByDayWithoutDeduplicatingRepeatedTracks() {
        val today = LocalDate.of(2026, 11, 9)
        val zone = ZoneId.systemDefault()
        fun at(day: LocalDate, hour: Int) =
            day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        fun entry(id: String, day: LocalDate, hour: Int) =
            ListeningHistory.Entry(
                Song(videoId = id, title = id, artist = "Artist", thumbnailUrl = null),
                at(day, hour),
            )

        val sections = ListeningHistory.sections(
            listOf(
                entry("repeat", today, 20),
                entry("repeat", today, 18),
                entry("yesterday", today.minusDays(1), 22),
            ),
            now = today,
        )

        assertEquals(listOf("Today", "Yesterday"), sections.map { it.title })
        assertEquals(listOf("repeat", "repeat"), sections[0].songs.map { it.videoId })
        assertEquals(listOf("yesterday"), sections[1].songs.map { it.videoId })
    }
}
