package com.music.bitchord.ui.screens.scrobble

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleLocalTasteEngineTest {
    private fun song(id: Int, title: String, artist: String) = Song(
        videoId = "video" + id.toString().padStart(6, '0'),
        title = title,
        artist = artist,
        thumbnailUrl = null,
    )

    @Test
    fun excludesHeardSongsSeedsAlternateEditsAndJunk() {
        val seed = song(1, "Blue Monday", "New Order")
        val heard = song(2, "Old Song", "Old Artist")
        val rows = listOf(
            seed,
            song(3, "Blue Monday (Official Video)", "New Order"),
            heard,
            song(4, "Mega Mix 2026", "DJ"),
            song(5, "Fresh Track", "Fresh Artist"),
        )

        val result = buildLocalTasteCandidates(
            seeds = listOf(seed),
            radioBySeed = listOf(rows),
            heardSongs = listOf(seed, heard),
            total = 20,
        )

        assertEquals(listOf("Fresh Track"), result.map { it.title })
    }

    @Test
    fun capsArtistRepetitionAtThreeAcrossSeeds() {
        val rows = listOf(
            song(1, "Track One", "Same Artist"),
            song(2, "Track Two", "Same Artist"),
            song(3, "Track Three", "Same Artist"),
            song(4, "Track Four", "Same Artist"),
            song(5, "Other Track", "Other Artist"),
        )

        val result = buildLocalTasteCandidates(
            seeds = emptyList(),
            radioBySeed = listOf(rows),
            heardSongs = emptyList(),
            total = 10,
        )

        assertEquals(4, result.size)
        assertEquals(3, result.count { it.artist == "Same Artist" })
    }

    @Test
    fun capsTenTracksPerSeedAndPreservesFirstOccurrence() {
        val rows = (0 until 12).map { index ->
            song(index, "Track " + index, "Artist " + index)
        } + song(0, "Different metadata", "Another Artist")

        val result = buildLocalTasteCandidates(
            seeds = emptyList(),
            radioBySeed = listOf(rows),
            heardSongs = emptyList(),
            total = 20,
        )

        assertEquals(10, result.size)
        assertEquals("Track 0", result.first().title)
        assertTrue(result.none { it.title == "Track 10" })
    }

    @Test
    fun allowsSameTitleForDifferentArtistsButNotAlternateEditForSameArtist() {
        val rows = listOf(
            song(1, "Echo", "Artist One"),
            song(2, "Echo", "Artist Two"),
            song(3, "Echo (Live Version)", "Artist One"),
        )

        val result = buildLocalTasteCandidates(
            seeds = emptyList(),
            radioBySeed = listOf(rows),
            heardSongs = emptyList(),
            total = 10,
        )

        assertEquals(listOf("Artist One", "Artist Two"), result.map { it.artist })
    }
}
