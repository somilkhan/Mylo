package com.music.bitchord.data.generate

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistGenerationRulesTest {

    @Test
    fun deduplicatesByVideoIdAndDropsInvalidOrVideoRows() {
        val first = song("same", "First")
        val duplicate = song("same", "Duplicate")
        val result = PlaylistGenerationRules.deduplicate(
            listOf(first, duplicate, song("", "Missing id"), song("video", "Video").copy(isVideo = true)),
        )

        assertEquals(listOf(first), result)
    }

    @Test
    fun heardFilteringUsesVideoIdAndNormalizedTitleArtistIdentity() {
        val byId = song("heard-id", "Different title")
        val sameRecording = song("new-upload-id", "  SAME   TRACK ").copy(artist = "THE ARTIST")
        val unheard = song("new-id", "Another track")

        val result = PlaylistGenerationRules.excludeHeard(
            candidates = listOf(byId, sameRecording, unheard),
            heardVideoIds = setOf("heard-id"),
            heardTrackKeys = setOf(PlaylistGenerationRules.trackKey(song("old-id", "Same Track").copy(artist = "The Artist"))),
        )

        assertEquals(listOf(unheard), result)
    }

    @Test
    fun savedAndLibraryTracksAreNotExcludedWithoutAnExplicitIdentitySet() {
        val saved = song("saved", "Saved")
        val liked = song("liked", "Liked")

        val result = PlaylistGenerationRules.excludeHeard(
            candidates = listOf(saved, liked),
            heardVideoIds = emptySet(),
            heardTrackKeys = emptySet(),
        )

        assertEquals(listOf(saved, liked), result)
    }

    @Test
    fun savedTracksAreDeprioritizedButNotDiscardedWhenPoolIsThin() {
        val saved = song("saved", "Saved")
        val fresh = song("fresh", "Fresh")
        val result = PlaylistGenerationRules.prioritizeFresh(
            candidates = listOf(saved, fresh),
            savedVideoIds = setOf("saved"),
            savedTrackKeys = emptySet(),
            limit = 2,
        )

        assertEquals(listOf(fresh, saved), result)
    }

    @Test
    fun similarPlaylistCandidatesExcludeOriginalIdsAndSameRecording() {
        val original = song("source-id", "Original track")
        val sameRecording = song("new-upload-id", "  ORIGINAL TRACK ").copy(artist = "THE ARTIST")
        val fresh = song("fresh-id", "Fresh track")

        assertEquals(
            listOf(fresh),
            PlaylistGenerationRules.excludeSourcePlaylistTracks(
                candidates = listOf(original, sameRecording, fresh),
                sourceSongs = listOf(original.copy(artist = "The Artist")),
            ),
        )
    }

    private fun song(id: String, title: String) =
        Song(videoId = id, title = title, artist = "The Artist", thumbnailUrl = null)
}
