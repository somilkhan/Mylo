package com.music.bitchord.ui.scrobble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrobblePlaylistModeLabelTest {
    @Test
    fun mapsEveryGeneratorModeToItsVisibleLabel() {
        assertEquals("Top", playlistGenerationModeLabel("top"))
        assertEquals("Recent", playlistGenerationModeLabel("recent"))
        assertEquals("Similar Tracks", playlistGenerationModeLabel("similar-tracks"))
        assertEquals("Similar Artists", playlistGenerationModeLabel("similar-artists"))
        assertEquals("Tag", playlistGenerationModeLabel("tag"))
        assertEquals("Mix", playlistGenerationModeLabel("mix"))
        assertEquals("Recommendations", playlistGenerationModeLabel("recommendations"))
        assertEquals("Never Heard", playlistGenerationModeLabel("never-heard"))
        assertEquals("Library", playlistGenerationModeLabel("library"))
        assertEquals("Forgotten Favorites", playlistGenerationModeLabel("forgotten-favorites"))
        assertEquals("Tag Top Tracks", playlistGenerationModeLabel("tag-top"))
        assertEquals("Top Artists Mix", playlistGenerationModeLabel("top-artists-mix"))
    }

    @Test
    fun labelsSimilarPlaylistAndHidesManualOrUnknownOrigins() {
        assertEquals("Similar Playlist", playlistGenerationModeLabel("similar-playlist"))
        assertNull(playlistGenerationModeLabel(null))
        assertNull(playlistGenerationModeLabel("manual"))
        assertNull(playlistGenerationModeLabel("unknown-mode"))
    }
}
