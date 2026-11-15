package com.music.bitchord.ui.screens.scrobble

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleRecommendationSelectionTest {
    private fun candidate(
        id: Int,
        artist: String = "Artist " + id,
        album: String = "Album " + id,
        genre: String = "tag:genre-" + (id % 3),
        score: Double = (100 - id).toDouble(),
        fresh: Boolean = true,
        familiar: Boolean = false,
    ) = ScrobbleRecommendationCandidate(
        song = Song(
            videoId = "video" + id.toString().padStart(6, '0'),
            title = "Track " + id,
            artist = artist,
            thumbnailUrl = null,
            albumName = album,
        ),
        tags = setOf(genre),
        fresh = fresh,
        familiar = familiar,
        score = score,
    )

    @Test
    fun selectsFreshTracksAndKeepsStrongestAtPlaylistBoundaries() {
        val input = listOf(
            candidate(1, score = 20.0),
            candidate(2, score = 100.0),
            candidate(3, score = 80.0),
            candidate(4, score = 90.0),
            candidate(5, fresh = false, score = 1000.0),
        )
        val result = ScrobbleRecommendationSelection.select(input, total = 4)
        assertEquals(4, result.size)
        assertEquals("Track 2", result.first().title)
        assertEquals("Track 4", result.last().title)
        assertTrue(result.none { it.title == "Track 5" })
    }

    @Test
    fun respectsArtistAlbumCapsWhenPoolAllows() {
        val input = (0 until 18).map { index ->
            candidate(
                id = index,
                artist = "Artist " + (index % 6),
                album = "Album " + (index % 9),
                genre = "tag:genre-" + (index % 4),
            )
        }
        val result = ScrobbleRecommendationSelection.select(input, total = 8)
        assertEquals(8, result.size)
        assertTrue(result.groupingBy { it.artist }.eachCount().values.all { it <= 2 })
        assertTrue(result.groupingBy { it.albumName }.eachCount().values.all { it <= 2 })
    }

    @Test
    fun progressivelyRelaxesDiversityToFillThinPools() {
        val input = (0 until 5).map { index ->
            candidate(index, artist = "One Artist", album = "One Album", genre = "tag:one")
        }
        val result = ScrobbleRecommendationSelection.select(input, total = 5)
        assertEquals(5, result.size)
    }

    @Test
    fun deduplicatesAlternateVideoIdsByNormalizedSongIdentity() {
        val first = candidate(1).copy(
            song = candidate(1).song.copy(title = "  Blue   Monday ", artist = "New Order"),
        )
        val duplicate = candidate(2).copy(
            song = candidate(2).song.copy(title = "blue monday", artist = " new  order "),
        )
        val result = ScrobbleRecommendationSelection.select(listOf(first, duplicate), total = 10)
        assertEquals(1, result.size)
    }

    @Test
    fun noFreshCandidatesNeverFallsBackToPreviouslyHeardTracks() {
        val result = ScrobbleRecommendationSelection.select(
            listOf(candidate(1, fresh = false), candidate(2, fresh = false)),
            total = 2,
        )
        assertTrue(result.isEmpty())
    }
}
