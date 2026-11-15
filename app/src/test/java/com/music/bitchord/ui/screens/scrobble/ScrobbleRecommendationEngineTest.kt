package com.music.bitchord.ui.screens.scrobble

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.ScrobbleChartEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleRecommendationEngineTest {
    private fun song(id: String, title: String, artist: String) =
        Song(videoId = id, title = title, artist = artist, thumbnailUrl = null)

    @Test
    fun freshnessRejectsHeardVideoIdAndAlternateEdit() {
        val heard = song("video000001", "Blue Monday", "New Order")
        assertFalse(isScrobbleFreshCandidate(
            song("video000002", "Blue Monday (Official Video)", "New Order"),
            listOf(heard), emptyList(),
        ))
        assertFalse(isScrobbleFreshCandidate(
            song("video000001", "Different metadata", "Other Artist"),
            listOf(heard), emptyList(),
        ))
    }

    @Test
    fun freshnessRejectsLastFmChartTracksEvenWithoutLocalHistory() {
        val chart = ScrobbleChartEntry(name = "Blue Monday", artist = "New Order")
        assertFalse(isScrobbleFreshCandidate(
            song("video000002", "Blue Monday", "New Order"),
            emptyList(), listOf(chart),
        ))
    }

    @Test
    fun freshnessAllowsNewTrackAndDifferentArtistWithSameTitle() {
        assertTrue(isScrobbleFreshCandidate(
            song("video000002", "Echo", "Artist Two"),
            listOf(song("video000001", "Echo", "Artist One")),
            emptyList(),
        ))
    }
}
