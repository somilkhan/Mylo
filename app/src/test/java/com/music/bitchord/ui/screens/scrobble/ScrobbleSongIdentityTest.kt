package com.music.bitchord.ui.screens.scrobble

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleSongIdentityTest {
    private fun song(
        id: String,
        title: String,
        artist: String,
        artwork: String? = null,
    ) = Song(videoId = id, title = title, artist = artist, thumbnailUrl = artwork)

    @Test
    fun normalizedIdentityCollapsesWhitespaceAndCase() {
        val first = song("video000001", "  Blue   Monday ", " New  Order ")
        val second = song("video000002", "blue monday", "new order")

        assertEquals(first.scrobbleIdentityKey, second.scrobbleIdentityKey)
        assertTrue(first.sameScrobbleSongAs(second))
    }

    @Test
    fun sameVideoIdWinsWhenMetadataDrifts() {
        val first = song("sameVideo01", "Blue Monday", "New Order")
        val alternateMetadata = song("sameVideo01", "Blue Monday (Official Video)", "New Order - Topic")

        assertTrue(first.sameScrobbleSongAs(alternateMetadata))
        assertEquals(listOf(first), listOf(first, alternateMetadata).distinctScrobbleSongs())
    }

    @Test
    fun deduplicationKeepsFirstMetadataAcrossNormalizedDuplicates() {
        val first = song("video000001", "Blue Monday", "New Order", "first-art")
        val duplicate = song("video000002", " blue   monday ", "new order", "second-art")

        val result = listOf(first, duplicate).distinctScrobbleSongs()

        assertEquals(1, result.size)
        assertEquals("first-art", result.single().thumbnailUrl)
    }

    @Test
    fun blankTitlesDoNotCollapseDifferentVideoIds() {
        val first = song("video000001", "", "")
        val second = song("video000002", "", "")

        assertFalse(first.sameScrobbleSongAs(second))
        assertEquals(2, listOf(first, second).distinctScrobbleSongs().size)
    }

    @Test
    fun repeatedVideoIdsAreRemovedInLinearPass() {
        val songs = (0 until 1000).map { index ->
            song("video${index.toString().padStart(6, '0')}", "Track $index", "Artist $index")
        } + song("video000123", "Different title", "Different artist")

        val result = songs.distinctScrobbleSongs()

        assertEquals(1000, result.size)
        assertEquals("Track 123", result[123].title)
    }
}
