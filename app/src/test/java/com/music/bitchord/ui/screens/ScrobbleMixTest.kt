package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrobbleMixTest {
    @Test
    fun interleavesTopTracksAndDiscoveriesWithoutDuplicateCatalogueIds() {
        val mixed = interleaveScrobbleMix(
            topTracks = listOf(song("top-a"), song("top-b")),
            recommendations = listOf(song("new-a"), song("top-a"), song("new-b")),
        )

        assertEquals(
            listOf("top-a", "new-a", "top-b", "new-b"),
            mixed.map(Song::videoId),
        )
    }

    @Test
    fun interleavesPathBackedLocalFilesByPathWhenUriIsUnavailable() {
        val first = song("matched-video", localPath = "/storage/music/one.flac")
        val second = song("matched-video", localPath = "/storage/music/two.flac")

        val mixed = interleaveScrobbleMix(
            topTracks = listOf(first),
            recommendations = listOf(second),
        )

        assertEquals(
            listOf("/storage/music/one.flac", "/storage/music/two.flac"),
            mixed.map(Song::localPath),
        )
    }

    @Test
    fun preservesLocalFileIdentityAndSkipsRowsThatCannotBePlayed() {
        val firstLocal = song("", localUri = "file:///music/one.flac")
        val secondLocal = song("", localUri = "file:///music/two.flac")
        val duplicateLocal = song("", localUri = "file:///music/one.flac")
        val unplayable = song("")

        val mixed = interleaveScrobbleMix(
            topTracks = listOf(firstLocal, secondLocal, unplayable),
            recommendations = listOf(duplicateLocal),
        )

        assertEquals(
            listOf("file:///music/one.flac", "file:///music/two.flac"),
            mixed.mapNotNull(Song::localUri),
        )
    }

    @Test
    fun respectsTheQueueLimit() {
        val mixed = interleaveScrobbleMix(
            topTracks = (1..10).map { song("top-$it") },
            recommendations = (1..10).map { song("new-$it") },
            limit = 5,
        )

        assertEquals(5, mixed.size)
    }

    private fun song(
        videoId: String,
        localUri: String? = null,
        localPath: String? = null,
    ) = Song(
        videoId = videoId,
        title = videoId.ifBlank { "Local track" },
        artist = "Artist",
        thumbnailUrl = null,
        localUri = localUri,
        localPath = localPath,
    )
}
