package com.music.bitchord.ui.screens

import com.music.bitchord.data.ListeningHistory
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrobbleMixLogicTest {
    @Test
    fun rediscoverRequiresThreeCompletedPlaysAndSixtyQuietDays() {
        val now = 100L * DAY
        val oldFavourite = Song("oldtrack001", "Old favourite", "Artist", null)
        val recentFavourite = Song("recent00001", "Recent favourite", "Artist", null)
        val skippedTrack = Song("skiptrack01", "Short skip", "Artist", null)
        val listens = listOf(
            ListeningHistory.Entry(oldFavourite, now - 90 * DAY),
            ListeningHistory.Entry(oldFavourite, now - 80 * DAY),
            ListeningHistory.Entry(oldFavourite, now - 61 * DAY),
            ListeningHistory.Entry(recentFavourite, now - 5 * DAY),
            ListeningHistory.Entry(skippedTrack, now - 75 * DAY),
        )
        val counts = mapOf(
            songIdentity(oldFavourite) to 3,
            songIdentity(recentFavourite) to 3,
            songIdentity(skippedTrack) to 2,
        )

        val result = buildRediscoverMix(listens, counts, now)

        assertEquals(listOf(oldFavourite), result)
    }

    @Test
    fun rediscoverUsesLatestListenAndHonoursLimit() {
        val now = 100L * DAY
        val first = Song("firsttrack01", "First", "Artist", null)
        val second = Song("secondtrack1", "Second", "Artist", null)
        val listens = listOf(
            ListeningHistory.Entry(first, now - 90 * DAY),
            ListeningHistory.Entry(second, now - 70 * DAY),
            ListeningHistory.Entry(second, now - 2 * DAY),
        )
        val counts = mapOf(songIdentity(first) to 4, songIdentity(second) to 5)

        assertEquals(listOf(first), buildRediscoverMix(listens, counts, now, limit = 1))
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
