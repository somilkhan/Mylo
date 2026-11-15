package com.music.bitchord.ui.screens

import com.music.bitchord.data.ListeningHistory
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleMixLogicTest {
    @Test
    fun seedVersionMatchingRejectsAlternateUploadsButKeepsUnrelatedTracks() {
        val seed = Song("seedtrack01", "Deep Swim", "Original Artist", null)
        val vip = Song("viptrack0001", "Deep Swim (VIP Remix)", "Another Artist", null)
        val officialAudio = Song("audio000001", "Deep Swim - Official Audio", "Original Artist", null)
        val unrelated = Song("unrelated01", "Deep Swimmer", "Different Artist", null)
        val differentSong = Song("different001", "Ocean Lights", "Original Artist", null)

        assertTrue(isSameSongOrEdit(vip, seed))
        assertTrue(isSameSongOrEdit(officialAudio, seed))
        assertFalse(isSameSongOrEdit(unrelated, seed))
        assertFalse(isSameSongOrEdit(differentSong, seed))
    }

    @Test
    fun radioMixUsesSeedRadioFirstAndFillsShortResultsWithoutSeedEditsOrDuplicates() {
        val seed = Song("seedtrack01", "Deep Swim", "Seed Artist", null)
        val radioOne = Song("radio000001", "Ocean Lights", "Other Artist", null)
        val alternateUpload = Song("alternate001", "Ocean Lights - Official Audio", "Other Artist", null)
        val similar = Song("similar0001", "Similar Track", "Similar Artist", null)
        val radioEdit = Song("radio000002", "Deep Swim (VIP Remix)", "Remixer", null)
        val favourite = Song("favourite01", "Favourite", "Seed Artist", null)
        val recent = Song("recent00001", "Recent", "Recent Artist", null)
        val duplicate = radioOne.copy(title = "Duplicate metadata")
        val discovery = Song("discover001", "Discovery", "Discovery Artist", null)
        val incomplete = Song("incomplete01", "", "Unknown", null)

        val result = buildRadioMix(
            radio = listOf(seed, radioOne, alternateUpload, radioEdit, incomplete),
            similar = listOf(similar),
            favourites = listOf(favourite, duplicate),
            recent = listOf(recent),
            discovery = listOf(discovery),
            seed = seed,
            limit = 5,
        )

        assertEquals(listOf(radioOne, similar, favourite, recent, discovery), result)
    }

    @Test
    fun radioMixReturnsOnlyAvailableTracksAndHonoursZeroLimit() {
        val seed = Song("seedtrack01", "Seed", "Artist", null)
        val onlyEdit = Song("edittrack001", "Seed (Live)", "Artist", null)

        assertEquals(emptyList<Song>(), buildRadioMix(emptyList(), emptyList(), listOf(onlyEdit), emptyList(), emptyList(), seed, 10))
        assertEquals(emptyList<Song>(), buildRadioMix(listOf(onlyEdit), emptyList(), emptyList(), emptyList(), emptyList(), seed, 0))
    }

    @Test
    fun artistCapLimitsSeedAndOtherArtistsThenRelaxesOnlyWhenPoolIsShort() {
        val candidates = listOf(
            com.music.bitchord.data.model.Song("aaaaaaaaaaa", "Seed 1", "Seed Artist", null),
            com.music.bitchord.data.model.Song("bbbbbbbbbbb", "Seed 2", "Seed Artist", null),
            com.music.bitchord.data.model.Song("ccccccccccc", "Seed 3", "Seed Artist", null),
            com.music.bitchord.data.model.Song("ddddddddddd", "Other 1", "Other Artist", null),
            com.music.bitchord.data.model.Song("eeeeeeeeeee", "Other 2", "Other Artist", null),
            com.music.bitchord.data.model.Song("fffffffffff", "Other 3", "Other Artist", null),
            com.music.bitchord.data.model.Song("ggggggggggg", "Third 1", "Third Artist", null),
            com.music.bitchord.data.model.Song("hhhhhhhhhhh", "Third 2", "Third Artist", null),
            com.music.bitchord.data.model.Song("iiiiiiiiiii", "Third 3", "Third Artist", null),
        )

        val strict = buildArtistCappedMix(candidates, "seed artist", targetSize = 3)
        assertEquals(listOf("Seed 1", "Other 1", "Other 2"), strict.map { it.title })

        val relaxed = buildArtistCappedMix(candidates, "Seed Artist", targetSize = 7)
        assertEquals(7, relaxed.size)
        assertEquals(2, relaxed.count { it.artist == "Seed Artist" })
        assertEquals(3, relaxed.count { it.artist == "Other Artist" })
        assertEquals(2, relaxed.count { it.artist == "Third Artist" })

        val fallback = buildArtistCappedMix(candidates.take(3), "Seed Artist", targetSize = 5)
        assertEquals(3, fallback.size)
        assertEquals(emptyList<com.music.bitchord.data.model.Song>(), buildArtistCappedMix(candidates, "Seed Artist", 0))
    }

    @Test
    fun rediscoverRequiresThreeCompletedPlaysAndSixtyQuietDays() {
        val now = 100L * DAY
        val oldFavourite = Song("oldtrack001", "Old favourite", "Artist", null)
        val recentFavourite = Song("recent00001", "Recent favourite", "Artist", null)
        val skippedTrack = Song("skiptrack01", "Short skip", "Artist", null)
        val listens = listOf(
            ListeningHistory.Entry(oldFavourite, now - 90 * DAY),
            ListeningHistory.Entry(oldFavourite, now - 80 * DAY),
            ListeningHistory.Entry(oldFavourite, now - 61 * DAY),
            ListeningHistory.Entry(recentFavourite, now - 5 * DAY),
            ListeningHistory.Entry(skippedTrack, now - 75 * DAY),
        )
        val counts = mapOf(
            songIdentity(oldFavourite) to 3,
            songIdentity(recentFavourite) to 3,
            songIdentity(skippedTrack) to 2,
        )

        val result = buildRediscoverMix(listens, counts, now)

        assertEquals(listOf(oldFavourite), result)
    }

    @Test
    fun rediscoverUsesLatestListenAndHonoursLimit() {
        val now = 100L * DAY
        val first = Song("firsttrack01", "First", "Artist", null)
        val second = Song("secondtrack1", "Second", "Artist", null)
        val listens = listOf(
            ListeningHistory.Entry(first, now - 90 * DAY),
            ListeningHistory.Entry(second, now - 70 * DAY),
            ListeningHistory.Entry(second, now - 2 * DAY),
        )
        val counts = mapOf(songIdentity(first) to 4, songIdentity(second) to 5)

        assertEquals(listOf(first), buildRediscoverMix(listens, counts, now, limit = 1))
    }


    @Test
    fun rediscoverPrioritizesTheLongestQuietPeriodRegardlessOfHistoryOrder() {
        val now = 100L * DAY
        val moreRecentlyQuiet = Song("quietrecent1", "More recent", "Artist", null)
        val longestQuiet = Song("quietlong001", "Longest quiet", "Artist", null)
        // Deliberately put the more-recent candidate first, as a stored history
        // file may be newest-first or oldest-first depending on its origin.
        val listens = listOf(
            ListeningHistory.Entry(moreRecentlyQuiet, now - 80 * DAY),
            ListeningHistory.Entry(longestQuiet, now - 95 * DAY),
            ListeningHistory.Entry(moreRecentlyQuiet, now - 70 * DAY),
        )
        val counts = mapOf(
            songIdentity(moreRecentlyQuiet) to 3,
            songIdentity(longestQuiet) to 3,
        )

        assertEquals(
            listOf(longestQuiet, moreRecentlyQuiet),
            buildRediscoverMix(listens, counts, now),
        )
    }

    @Test
    fun tagMixKeepsOnlyPlayableSongRowsDeduplicatesAndHonoursLimit() {
        val first = Song("abcdefghijk", "First", "Artist", null)
        val duplicate = first.copy(title = "Duplicate title")
        val alternateUpload = first.copy(videoId = "alttrack001")
        val second = Song("lmnopqrstuv", "Second", "Artist", null)
        val invalid = Song("local-file", "Local", "Artist", null)
        val browse = com.music.bitchord.data.model.BrowseItem(
            browseId = "MPREb123",
            title = "Album",
            subtitle = "Artist",
            thumbnailUrl = null,
            type = com.music.bitchord.data.model.BrowseType.ALBUM,
        )

        val result = buildTagMix(
            listOf(
                SearchResult.Track(first),
                SearchResult.TopTrack(duplicate),
                SearchResult.Track(alternateUpload),
                SearchResult.Browse(browse),
                SearchResult.Track(invalid),
                SearchResult.Track(second),
            ),
            limit = 10,
        )

        assertEquals(listOf(first, second), result)
    }

    @Test
    fun libraryMixMergesLikedAndAddedTracksWithoutDuplicates() {
        val liked = Song("abcdefghijk", "Liked", "Artist", null)
        val overlap = liked.copy(title = "Duplicate liked row")
        val added = Song("lmnopqrstuv", "Added", "Artist", null)
        val local = Song("content://media/1", "Local", "Artist", null)

        val result = buildLibraryMix(
            com.music.bitchord.data.model.LibraryPage(
                likedSongs = listOf(liked, local),
                librarySongs = listOf(overlap, added),
                shelves = emptyList(),
            ),
        )

        assertEquals(listOf(liked, added), result)
    }

    @Test
    fun tagAndLibraryMixesReturnEmptyForZeroLimitOrNoPlayableTracks() {
        val local = Song("content://media/1", "Local", "Artist", null)
        val library = com.music.bitchord.data.model.LibraryPage(
            likedSongs = listOf(local),
            librarySongs = emptyList(),
            shelves = emptyList(),
        )

        assertEquals(emptyList<Song>(), buildTagMix(emptyList(), limit = 0))
        assertEquals(emptyList<Song>(), buildLibraryMix(library))
    }

    @Test
    fun youtubeFirstMixUsesTwoToOnePriorityAndDeduplicatesAcrossSources() {
        val ytOne = Song("youtube0001", "YouTube one", "Artist", null)
        val shared = Song("shared00001", "Shared", "Artist", null)
        val ytTwo = Song("youtube0002", "YouTube two", "Artist", null)
        val lastFmOne = Song("lastfm00001", "Last.fm one", "Artist", null)
        val alternateUpload = shared.copy(videoId = "alternate001")

        assertEquals(
            // LastWave's youtubeFirstBlend keeps YouTube first in a 2:1 ratio;
            // duplicate Last.fm rows never replace the YouTube metadata.
            listOf(ytOne, shared, ytTwo, lastFmOne),
            buildYouTubeFirstMix(
                youtube = listOf(ytOne, shared, ytTwo),
                lastFm = listOf(shared.copy(title = "Duplicate"), alternateUpload, lastFmOne),
                limit = 10,
            ),
        )
    }

    @Test
    fun tasteMixUsesWeightedSourcesAndDeduplicatesByTrackIdentity() {
        val favouriteOne = Song("favourite001", "Favourite one", "Favourite Artist", null)
        val favouriteTwo = Song("favourite002", "Favourite two", "Favourite Artist", null)
        val favouriteThree = Song("favourite003", "Favourite three", "Favourite Artist", null)
        val recentOne = Song("recent00001", "Recent one", "Recent Artist", null)
        val recentTwo = Song("recent00002", "Recent two", "Recent Artist", null)
        val freshOne = Song("fresh000001", "Fresh one", "Fresh Artist", null)

        val result = buildTasteMix(
            favourites = listOf(favouriteOne, favouriteTwo, favouriteThree),
            recent = listOf(favouriteTwo.copy(title = "Duplicate"), recentOne, recentTwo),
            fresh = listOf(recentOne, freshOne),
            limit = 5,
        )

        assertEquals(
            listOf(favouriteOne, favouriteTwo, recentOne, recentTwo, freshOne),
            result,
        )
    }

    @Test
    fun precheckRejectsIncompleteRowsDeduplicatesAndCapsThreePerArtist() {
        val sameArtist = listOf(
            Song("artisttrack01", "One", "Artist", null),
            Song("artisttrack02", "Two", "Artist", null),
            Song("artisttrack03", "Three", "Artist", null),
            Song("artisttrack04", "Four", "Artist", null),
        )
        val other = Song("othertrack01", "Other", "Other Artist", null)
        val duplicateId = sameArtist.first().copy(title = "Different metadata")
        val duplicateName = sameArtist.first().copy(videoId = "alternate001")
        val incomplete = Song("incomplete01", "", "Unknown", null)

        assertEquals(
            listOf(sameArtist[0], sameArtist[1], sameArtist[2], other),
            buildPrecheckedMix(sameArtist + duplicateId + duplicateName + incomplete + other),
        )
        assertEquals(emptyList<Song>(), buildPrecheckedMix(sameArtist, limit = 0))
    }

    @Test
    fun tasteMixHandlesEmptySourcesAndZeroLimit() {
        val favourite = Song("favourite001", "Favourite", "Artist", null)

        assertEquals(listOf(favourite), buildTasteMix(listOf(favourite), emptyList(), emptyList()))
        assertEquals(emptyList<Song>(), buildTasteMix(listOf(favourite), emptyList(), emptyList(), limit = 0))
    }

    @Test
    fun discoverUsesLastWaveHiddenGemCommunityAndMatchSignals() {
        val popular = Song("populartrk1", "Popular track", "Artist A", null)
        val hiddenGem = Song("hiddengem01", "Hidden gem", "Artist B", null)

        val result = buildDiscoverMix(
            candidates = listOf(popular, hiddenGem),
            heardVideoIds = emptySet(),
            trackSignals = mapOf(
                recommendationIdentity(popular) to LastWaveTrackSignals(
                    listeners = 2_000_000,
                    playCount = 2_000_000,
                    match = 0.1,
                ),
                recommendationIdentity(hiddenGem) to LastWaveTrackSignals(
                    listeners = 1_000,
                    playCount = 6_000,
                    match = 0.5,
                    album = "Hidden album",
                ),
            ),
            limit = 2,
        )

        assertEquals(listOf(hiddenGem, popular), result)
    }

    @Test
    fun discoverPrefersHigherLastWaveSourceWeight() {
        val low = Song("lowweight01", "Low weight", "Unknown Artist", null)
        val high = Song("highwght001", "High weight", "Unknown Artist", null)

        val result = buildDiscoverMix(
            candidates = listOf(low, high),
            heardVideoIds = emptySet(),
            sourceWeights = mapOf(
                recommendationIdentity(low) to 1,
                recommendationIdentity(high) to 4,
            ),
            limit = 2,
        )

        assertEquals(listOf(high, low), result)
    }

    @Test
    fun discoverUsesLastWaveTasteAffinityAndArtistDiversityBeforeRelaxingCaps() {
        val topArtistA = Song("topartst001", "Top artist song", "Top Artist", null)
        val recentArtistA = Song("recentart01", "Recent artist song", "Recent Artist", null)
        val newArtistA = Song("newartst001", "New artist one", "New Artist", null)
        val newArtistB = Song("newartst002", "New artist two", "New Artist", null)
        val newArtistC = Song("newartst003", "New artist three", "New Artist", null)
        val newArtistD = Song("newartst004", "New artist four", "New Artist", null)

        val result = buildDiscoverMix(
            candidates = listOf(newArtistA, newArtistB, newArtistC, newArtistD, topArtistA, recentArtistA),
            heardVideoIds = emptySet(),
            topArtistNames = setOf("Top Artist"),
            recentArtistNames = setOf("Recent Artist"),
            artistAffinity = mapOf("top artist" to 1.0),
            limit = 5,
        )

        assertEquals(5, result.size)
        assertEquals("Top Artist", result.first().artist)
        assertEquals("Recent Artist", result.last().artist)
        assertEquals(3, result.count { it.artist == "New Artist" })
    }

    @Test
    fun neverHeardExcludesKnownTracksAndDuplicatesAndHonoursLimit() {
        val heard = Song("knowntrk001", "Already heard", "Artist", null)
        val fresh = Song("freshtrk001", "New track", "Artist", null)
        val duplicate = fresh.copy(title = "Duplicate row")
        val blankId = Song("", "Local-only", "Artist", null)

        val result = buildNeverHeardMix(
            candidates = listOf(heard, fresh, duplicate, blankId),
            heardVideoIds = setOf(heard.videoId),
            heardSongs = listOf(heard),
            limit = 1,
        )

        assertEquals(listOf(fresh), result)
    }

    @Test
    fun artistCapDeduplicatesAlternateUploadsBeforeApplyingDiversity() {
        val original = Song("original001", "Night Drive", "Artist A", null)
        val alternate = Song("alternate01", "Night Drive - Official Audio", "Artist A", null)
        val other = Song("othertrk001", "Morning Light", "Artist B", null)

        assertEquals(
            listOf(original, other),
            buildArtistCappedMix(
                candidates = listOf(original, alternate, other),
                seedArtist = "Artist A",
                targetSize = 10,
            ),
        )
    }

    @Test
    fun similarArtistMixExcludesSeedArtistAndBalancesOtherArtists() {
        val seedOne = Song("seedtrk0001", "Seed song", "Seed Artist", null)
        val seedTwo = Song("seedtrk0002", "Another seed song", "Seed Artist feat. Guest", null)
        val relatedA1 = Song("relAtrack01", "A one", "Related A", null)
        val relatedA2 = Song("relAtrack02", "A two", "Related A feat. Guest", null)
        val relatedA3 = Song("relAtrack03", "A three", "Related A", null)
        val relatedB1 = Song("relBtrack01", "B one", "Related B", null)
        val relatedB2 = Song("relBtrack02", "B two", "Related B", null)
        val relatedB3 = Song("relBtrack03", "B three", "Related B", null)

        val result = buildSimilarArtistMix(
            youtubeRadio = listOf(seedOne, relatedA1, relatedB1, relatedA2, relatedB2, seedTwo),
            lastFmSimilar = listOf(relatedA3, relatedB3),
            seedArtist = "Seed Artist",
            limit = 4,
        )

        assertEquals(4, result.size)
        assertTrue(result.none { it.artist.startsWith("Seed Artist") })
        assertEquals(2, result.count { com.music.bitchord.data.stats.ListeningStats.primaryArtist(it.artist) == "Related A" })
        assertEquals(2, result.count { it.artist == "Related B" })
    }

    @Test
    fun neverHeardExcludesAlternateUploadsAndEditsOfPreviouslyHeardTracks() {
        val heard = Song("heardtrack01", "Deep Swim", "Original Artist", null)
        val alternateUpload = Song("alttrack001", "Deep Swim - Official Audio", "Original Artist feat. Guest", null)
        val remix = Song("remixtrk001", "Deep Swim (VIP Remix)", "Original Artist feat. Guest", null)
        val unrelated = Song("freshtrk001", "Ocean Lights", "Original Artist", null)
        val sameTitleOtherArtist = Song("freshtrk002", "Deep Swim", "Different Artist", null)

        assertEquals(
            listOf(unrelated, sameTitleOtherArtist),
            buildNeverHeardMix(
                candidates = listOf(alternateUpload, remix, unrelated, sameTitleOtherArtist),
                heardVideoIds = emptySet(),
                heardSongs = listOf(heard),
            ),
        )
    }

    @Test
    fun discoverExcludesPreviouslyHeardTracksAndAlternateUploads() {
        val heard = Song("heardtrack01", "Deep Swim", "Original Artist", null)
        val alternate = Song("alttrack001", "Deep Swim - Official Audio", "Original Artist", null)
        val fresh = Song("freshtrk001", "Ocean Lights", "Original Artist", null)
        val sameTitleOtherArtist = Song("freshtrk002", "Deep Swim", "Different Artist", null)

        assertEquals(
            listOf(fresh, sameTitleOtherArtist),
            buildDiscoverMix(
                candidates = listOf(alternate, fresh, sameTitleOtherArtist),
                heardVideoIds = setOf(heard.videoId),
                heardSongs = listOf(heard),
            ),
        )
        assertTrue(buildDiscoverMix(listOf(fresh), emptySet(), limit = 0).isEmpty())
    }

    @Test
    fun neverHeardReturnsNoMoreThanRequestedAndZeroLimitIsEmpty() {
        val first = Song("freshtrk001", "First", "Artist", null)
        val second = Song("freshtrk002", "Second", "Artist", null)

        assertEquals(listOf(first), buildNeverHeardMix(listOf(first, second), emptySet(), limit = 1))
        assertEquals(emptyList<Song>(), buildNeverHeardMix(listOf(first), emptySet(), limit = 0))
    }

    @Test
    fun catalogueSeedRequiresAValidYoutubeVideoId() {
        val youtube = Song("dQw4w9WgXcQ", "Video", "Artist", null)
        val localContentUri = Song("content://media/external/audio/media/42", "Local", "Artist", null)
        val localPath = Song("/storage/music/local.mp3", "Local path", "Artist", null)

        assertEquals(true, isYouTubeCatalogTrack(youtube))
        assertEquals(false, isYouTubeCatalogTrack(localContentUri))
        assertEquals(false, isYouTubeCatalogTrack(localPath))
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }

    @Test
    fun lastWaveIdentityPortDeduplicatesWhitespaceCaseAndVideoIdWithoutLosingFirstMetadata() {
        val canonical = Song("abcdefghijk", "  Björk   –   Jóga ", "Björk", null)
        val sameSongDifferentUpload = Song("lmnopqrstuv", "Björk – Jóga", "BJÖRK", "alternate-art")
        val sameVideoDifferentMetadata = Song("abcdefghijk", "Different title", "Different artist", null)
        val unrelated = Song("zyxwvutsrqp", "Army of Me", "Björk", null)

        val result = distinctRecommendationSongs(
            listOf(canonical, sameSongDifferentUpload, sameVideoDifferentMetadata, unrelated),
        )

        assertEquals(listOf(canonical, unrelated), result)
        assertEquals(
            "björk – jóga|björk",
            recommendationIdentity(canonical),
        )
    }
}
