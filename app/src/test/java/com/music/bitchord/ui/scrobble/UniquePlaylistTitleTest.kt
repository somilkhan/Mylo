package com.music.bitchord.ui.scrobble

import com.music.bitchord.data.generate.PlaylistGenerationMode
import com.music.bitchord.data.generate.PlaylistGenerationRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class UniquePlaylistTitleTest {

    @Test
    fun addsStableNumberedSuffixWhenModeTitleAlreadyExists() {
        val request = PlaylistGenerationRequest(mode = PlaylistGenerationMode.TOP)

        assertEquals("My Top Tracks (3)", uniquePlaylistTitle(request, listOf("My Top Tracks", "My Top Tracks (2)")))
    }

    @Test
    fun trimsAndBoundsSeedTitles() {
        val request = PlaylistGenerationRequest(
            mode = PlaylistGenerationMode.SIMILAR_TRACKS,
            seedTrack = com.music.bitchord.data.model.Song("id", "  Track  ", "Artist", null),
        )

        assertEquals("Similar to Track", uniquePlaylistTitle(request, emptyList()))
    }

    @Test
    fun createsUniqueInspiredPlaylistTitles() {
        assertEquals(
            "Inspired by Late night",
            uniqueInspiredPlaylistTitle(" Late night ", emptyList()),
        )
        assertEquals(
            "Inspired by Late night (2)",
            uniqueInspiredPlaylistTitle("Late night", listOf("Inspired by Late night")),
        )
    }
}
