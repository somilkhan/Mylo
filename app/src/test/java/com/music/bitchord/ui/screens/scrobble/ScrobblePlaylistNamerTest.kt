package com.music.bitchord.ui.screens.scrobble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScrobblePlaylistNamerTest {
    @Test
    fun subtitleMapsEveryLastWaveGeneratorMode() {
        assertEquals("Recent Tracks", ScrobblePlaylistNamer.subtitleFor("recent"))
        assertEquals("Top Tracks", ScrobblePlaylistNamer.subtitleFor("library"))
        assertEquals("My Mix", ScrobblePlaylistNamer.subtitleFor("mix"))
        assertEquals("Recommendations", ScrobblePlaylistNamer.subtitleFor("recommendations"))
        assertEquals("Never-Heard Mix", ScrobblePlaylistNamer.subtitleFor("never-heard"))
        assertEquals("Track Mix • Blue Monday", ScrobblePlaylistNamer.subtitleFor("similar-tracks", seedTrackName = "Blue Monday"))
        assertEquals("Artist Mix • New Order", ScrobblePlaylistNamer.subtitleFor("similar-artists", seedArtistInput = "New Order"))
        assertEquals("Genre Mix • Indie Rock", ScrobblePlaylistNamer.subtitleFor("tag", tagInput = "indie rock"))
        assertEquals("Discover Feed", ScrobblePlaylistNamer.subtitleFor("discover"))
    }

    @Test
    fun generatedNamesAreNotBlankAndHaveOneToThreeWords() {
        repeat(100) {
            val name = ScrobblePlaylistNamer.generateUniqueName(emptyList())
            val wordCount = name.split(' ').size
            assertFalse(name.isBlank())
            assertEquals(true, wordCount in 1..3)
        }
    }

    @Test
    fun generatedNameAvoidsExistingCaseAndWhitespaceVariants() {
        repeat(20) {
            val existing = listOf("  crimson horizon  ", "NEON MIRAGE", "Velvet Orbit")
            val name = ScrobblePlaylistNamer.generateUniqueName(existing)
            assertFalse(existing.any { it.trim().equals(name, ignoreCase = true) })
        }
    }
}
