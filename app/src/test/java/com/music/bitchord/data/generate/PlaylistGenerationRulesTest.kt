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
    fun radioPoolsAreInterleavedSoOneSeedDoesNotDominateTheMix() {
        val firstA = song("a1", "A1")
        val secondA = song("a2", "A2")
        val firstB = song("b1", "B1")
        val firstC = song("c1", "C1")
        val secondC = song("c2", "C2")

        assertEquals(
            listOf(firstA, firstB, firstC, secondA, secondC),
            PlaylistGenerationRules.interleave(
                listOf(
                    listOf(firstA, secondA),
                    listOf(firstB),
                    listOf(firstC, secondC),
                ),
            ),
        )
    }

    @Test
    fun weightedBlendPrefersHigherWeightAndDeduplicatesAcrossVideoIds() {
        val lowWeight = song("low", "Same Track")
        val highWeight = song("high", "Same Track")
        val other = song("other", "Other Track")

        val result = PlaylistGenerationRules.weightedBlend(
            pools = listOf(
                listOf(lowWeight, other) to 1,
                listOf(highWeight) to 4,
            ),
            limit = 3,
        )

        assertEquals(listOf(highWeight, other), result)
    }

    @Test
    fun weightedBlendCapsArtistsThenRelaxesCapToFillThinPool() {
        val sameArtist = listOf(
            song("a1", "A1", "Artist A"),
            song("a2", "A2", "Artist A"),
            song("a3", "A3", "Artist A"),
            song("a4", "A4", "Artist A"),
        )
        val otherArtist = song("b1", "B1", "Artist B")

        val result = PlaylistGenerationRules.weightedBlend(
            pools = listOf((sameArtist + otherArtist) to 4),
            limit = 5,
            maxPerArtist = 3,
        )

        assertEquals(5, result.size)
        assertEquals(4, result.count { it.artist == "Artist A" })
        assertEquals(1, result.count { it.artist == "Artist B" })
    }

    @Test
    fun weightedBlendMaintainsArtistCapWhenThereIsEnoughDiversity() {
        val tracks = listOf(
            song("a1", "A1", "Artist A"),
            song("a2", "A2", "Artist A"),
            song("a3", "A3", "Artist A"),
            song("b1", "B1", "Artist B"),
            song("b2", "B2", "Artist B"),
        )

        val result = PlaylistGenerationRules.weightedBlend(
            pools = listOf(tracks to 4),
            limit = 4,
            maxPerArtist = 2,
        )

        assertEquals(4, result.size)
        assertEquals(2, result.count { it.artist == "Artist A" })
        assertEquals(2, result.count { it.artist == "Artist B" })
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
    fun startMixRequiresAPlayableOnlineSongWithCompleteIdentity() {
        val valid = song("video-id", "Track", "Artist")
        assertEquals(true, PlaylistGenerationRules.canStartMixWithSong(valid))
        assertEquals(false, PlaylistGenerationRules.canStartMixWithSong(valid.copy(videoId = "")))
        assertEquals(false, PlaylistGenerationRules.canStartMixWithSong(valid.copy(isVideo = true)))
        assertEquals(false, PlaylistGenerationRules.canStartMixWithSong(valid.copy(localUri = "file:///music.mp3")))
        assertEquals(false, PlaylistGenerationRules.canStartMixWithSong(valid.copy(title = " ")))
        assertEquals(false, PlaylistGenerationRules.canStartMixWithSong(valid.copy(artist = "")))
    }

    @Test
    fun similarTrackCurationRemovesSeedEditsAndDuplicateTitlesThenRelaxesArtistCaps() {
        val seed = song("seed", "Deep Swim", "Seed Artist")
        val candidates = listOf(
            song("seed-copy", "Deep Swim (Official Video)", "Other Artist"),
            song("a1", "First", "Seed Artist"),
            song("a2", "Second", "Seed Artist"),
            song("b1", "Unique", "Artist B"),
            song("b2", "Unique (Live)", "Artist C"),
            song("c1", "Third", "Artist C"),
        )
        val result = PlaylistGenerationRules.curateSimilarTracks(candidates, seed, 5)
        assertEquals(listOf("a1", "a2", "b1", "c1"), result.map { it.videoId })
    }

    private fun song(id: String, title: String, artist: String = "The Artist") =
        Song(videoId = id, title = title, artist = artist, thumbnailUrl = null)
}
