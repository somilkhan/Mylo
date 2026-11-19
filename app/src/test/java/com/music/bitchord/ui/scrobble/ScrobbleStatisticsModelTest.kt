package com.music.bitchord.ui.scrobble

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.stats.RankedSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleStatisticsModelTest {
    @Test
    fun normalizesIdentityAggregatesDuplicatesAndUsesCrossSourceMaximum() {
        val local = listOf(
            ranked("local-1", "Don't Stop!", "AC/DC", plays = 4, ms = 100L),
            ranked("local-2", "dont stop", "a c d c", plays = 3, ms = 200L),
        )
        val remote = listOf(
            LastFM.RankedTrack("DONT STOP", "AC-DC", 4L, "https://img.test/remote.jpg"),
            LastFM.RankedTrack("Don't Stop", "AC/DC", 3L, null),
        )

        val merged = mergeStatisticsTracks(local, remote, lastFmConnected = true)

        assertEquals(1, merged.size)
        assertEquals(7L, merged.single().localPlays)
        assertEquals(7L, merged.single().lastFmPlays)
        assertEquals(7L, merged.single().plays)
        assertEquals(300L, merged.single().localListeningMs)
        assertEquals("local-2", merged.single().song?.videoId)
    }

    @Test
    fun largerSourceWinsAndRemoteOnlyTracksAreIncludedWhenConnected() {
        val local = listOf(ranked("local", "Track A", "Artist A", plays = 2, ms = 500L))
        val remote = listOf(
            LastFM.RankedTrack("Track A", "Artist A", 8L, null),
            LastFM.RankedTrack("Track B", "Artist B", 5L, null),
        )

        val merged = mergeStatisticsTracks(local, remote, lastFmConnected = true)

        assertEquals(2, merged.size)
        assertEquals(8L, merged.first { it.title == "Track A" }.plays)
        assertEquals(5L, merged.first { it.title == "Track B" }.plays)
        assertEquals(0L, merged.first { it.title == "Track B" }.localListeningMs)
    }

    @Test
    fun disconnectedStatisticsAreStrictlyLocalOnly() {
        val local = listOf(ranked("local", "Track A", "Artist A", plays = 5, ms = 900L))
        val remote = listOf(
            LastFM.RankedTrack("Track A", "Artist A", 12L, null),
            LastFM.RankedTrack("Remote only", "Artist B", 9L, null),
        )

        val merged = mergeStatisticsTracks(local, remote, lastFmConnected = false)

        assertEquals(1, merged.size)
        assertEquals(5L, merged.single().plays)
        assertEquals(0L, merged.single().lastFmPlays)
        assertEquals(900L, merged.single().localListeningMs)
    }

    @Test
    fun listeningTimeIsNeverInventedForRemoteOnlyTracks() {
        val merged = mergeStatisticsTracks(
            localTracks = emptyList(),
            lastFmTracks = listOf(LastFM.RankedTrack("Remote", "Artist", 4L, null)),
            lastFmConnected = true,
        )

        assertTrue(merged.single().localListeningMs == 0L)
    }

    private fun ranked(
        id: String,
        title: String,
        artist: String,
        plays: Int,
        ms: Long,
    ) = RankedSong(
        song = Song(videoId = id, title = title, artist = artist, thumbnailUrl = null),
        ms = ms,
        plays = plays,
    )
}
