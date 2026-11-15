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


    @Test
    fun neverHeardExcludesKnownTracksAndDuplicatesAndHonoursLimit() {
        val heard = Song("knowntrack01", "Already heard", "Artist", null)
        val fresh = Song("freshtrack01", "New track", "Artist", null)
        val duplicate = fresh.copy(title = "Duplicate row")
        val blankId = Song("", "Local-only", "Artist", null)

        val result = buildNeverHeardMix(
            candidates = listOf(heard, fresh, duplicate, blankId),
            heardVideoIds = setOf(heard.videoId),
            limit = 1,
        )

        assertEquals(listOf(fresh), result)
    }

    @Test
    fun neverHeardReturnsNoMoreThanRequestedAndZeroLimitIsEmpty() {
        val first = Song("freshtrack01", "First", "Artist", null)
        val second = Song("freshtrack02", "Second", "Artist", null)

        assertEquals(listOf(first), buildNeverHeardMix(listOf(first, second), emptySet(), limit = 1))
        assertEquals(emptyList<Song>(), buildNeverHeardMix(listOf(first), emptySet(), limit = 0))
    }

    @Test
    fun catalogueSeedRequiresAValidYoutubeVideoId() {
        val youtube = Song("dQw4w9WgXcQ", "Video", "Artist", null)
        val localContentUri = Song("content://media/external/audio/media/42", "Local", "Artist", null)
        val localPath = Song("/storage/music/local.mp3", "Local path", "Artist", null)

        assertEquals(true, isYouTubeCatalogTrack(youtube))
        assertEquals(false, isYouTubeCatalogTrack(localContentUri))
        assertEquals(false, isYouTubeCatalogTrack(localPath))
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
