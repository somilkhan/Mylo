package com.music.bitchord.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobblePlaylistNamerTest {
    @Test
    fun generatedNamesAreNonBlankAndUniqueIgnoringCase() {
        val existing = listOf("Velvet Horizon", "Neon Mirage", "MIDNIGHT")

        val generated = ScrobblePlaylistNamer.generateUniqueName(existing)

        assertTrue(generated.isNotBlank())
        assertFalse(existing.any { it.equals(generated, ignoreCase = true) })
        assertTrue(generated.split(' ').size in 1..3)
    }

    @Test
    fun playlistSubtitleMatchesGenerationMode() {
        assertTrue(ScrobblePlaylistNamer.subtitleFor("tag", "dream pop").contains("Dream Pop"))
        assertTrue(ScrobblePlaylistNamer.subtitleFor("similar-artists", seedArtist = "Artist").contains("Artist"))
        assertTrue(ScrobblePlaylistNamer.subtitleFor("never-heard").contains("Never-Heard"))
    }
}
