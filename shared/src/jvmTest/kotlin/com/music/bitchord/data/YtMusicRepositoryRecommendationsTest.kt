package com.music.bitchord.data

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YtMusicRepositoryRecommendationsTest {

    @Test
    fun quickPickExclusionsMergeLocalAndAccountHistory() {
        val result = mergeQuickPickExclusions(
            localHistoryIds = setOf("local-played", "shared", ""),
            accountHistoryIds = setOf("account-played", "shared"),
        )

        assertEquals(setOf("local-played", "account-played", "shared"), result)
    }

    @Test
    fun recommendationsDropBlankPlayedAndDuplicateIds() {
        val result = unplayedRecommendations(
            listOf(song("played"), song("fresh"), song("fresh"), song("")),
            setOf("played"),
        )

        assertEquals(listOf("fresh"), result.map(Song::videoId))
    }

    @Test
    fun localHistoryWithoutVideoIdsExcludesMatchingRecommendationMetadata() {
        val excludedMetadata = setOf(quickPickMetadataKey("  Local   Track ", "Local Artist"))
        val result = unplayedRecommendations(
            listOf(
                Song("yt-local-track", "Local Track", "Local Artist", null),
                Song("fresh", "Fresh Track", "Fresh Artist", null),
            ),
            excludedSongIds = emptySet(),
            excludedMetadataKeys = excludedMetadata,
        )
        assertEquals(listOf("fresh"), result.map(Song::videoId))
    }

    @Test
    fun metadataExclusionsNormalizeCaseAndWhitespace() {
        assertEquals(
            quickPickMetadataKey("Same Track", "Same Artist"),
            quickPickMetadataKey("  same   track ", " SAME ARTIST "),
        )
    }

    @Test
    fun fallbackUsesExploreWhenEveryHomeCandidateWasPlayed() {
        val result = selectQuickPickFallback(
            homeSongs = listOf(song("played"), song("")),
            exploreSongs = listOf(song("played"), song("new")),
            excludedSongIds = setOf("played"),
        )

        assertEquals(listOf("new"), result.map(Song::videoId))
    }

    @Test
    fun fallbackIsEmptyWhenEveryCandidateWasPlayedOrUnplayable() {
        val result = selectQuickPickFallback(
            homeSongs = listOf(song("played"), song("")),
            exploreSongs = listOf(song("played"), song("")),
            excludedSongIds = setOf("played"),
        )

        assertTrue(result.isEmpty())
    }

    private fun song(id: String) = Song(
        videoId = id,
        title = id.ifBlank { "Unplayable" },
        artist = "Artist",
        thumbnailUrl = null,
    )
}
