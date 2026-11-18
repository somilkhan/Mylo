package com.music.bitchord.data.spotify

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaylistCodecTest {

    @Test
    fun decodesLegacyPlaylistsWithSafeMetadataDefaults() {
        val legacyJson = """
            [{
              "id":"sp_local_legacy",
              "title":"Old playlist",
              "songs":[{
                "videoId":"video-1",
                "title":"Track",
                "artist":"Artist"
              }]
            }]
        """.trimIndent()

        val playlist = LocalPlaylistCodec.decode(legacyJson).single()

        assertEquals("sp_local_legacy", playlist.id)
        assertEquals("Old playlist", playlist.title)
        assertEquals(0L, playlist.createdAtEpochMillis)
        assertNull(playlist.generationMode)
        assertFalse(playlist.isPinned)
        assertEquals("video-1", playlist.songs.single().videoId)
        assertNull(playlist.songs.single().thumbnailUrl)
        assertFalse(playlist.songs.single().isVideo)
    }

    @Test
    fun roundTripsGeneratedMetadataAndSongIdentity() {
        val playlist = LocalPlaylist(
            id = "sp_local_generated",
            title = "Late night",
            songs = listOf(
                Song(
                    videoId = "video-42",
                    title = "Track 42",
                    artist = "Artist 42",
                    thumbnailUrl = "https://example.test/art.jpg",
                    durationText = "3:42",
                    artistId = "artist-id",
                    albumId = "album-id",
                    albumName = "Album",
                    isVideo = true,
                ),
            ),
            createdAtEpochMillis = 1_800_000_000_000L,
            generationMode = "similar-tracks",
            isPinned = true,
        )

        val decoded = LocalPlaylistCodec.decode(LocalPlaylistCodec.encode(listOf(playlist))).single()

        assertEquals(playlist.id, decoded.id)
        assertEquals(playlist.title, decoded.title)
        assertEquals(playlist.createdAtEpochMillis, decoded.createdAtEpochMillis)
        assertEquals(playlist.generationMode, decoded.generationMode)
        assertTrue(decoded.isPinned)
        assertEquals(playlist.songs.single(), decoded.songs.single())
        assertEquals(playlist.browseId, decoded.browseId)
    }

    @Test
    fun encodesAnEmptyPlaylistCollectionAsAnEmptyArray() {
        assertEquals("[]", LocalPlaylistCodec.encode(emptyList()))
        assertTrue(LocalPlaylistCodec.decode("[]").isEmpty())
    }

    @Test
    fun newestAndOldestSortsKeepPinnedPlaylistsFirst() {
        val rows = listOf(
            playlist("old", "Old", createdAt = 10),
            playlist("pinned", "Pinned", createdAt = 1, pinned = true),
            playlist("new", "New", createdAt = 30),
        )

        assertEquals(
            listOf("pinned", "new", "old"),
            sortLocalPlaylists(rows, LocalPlaylistSortMode.NEWEST).map { it.id },
        )
        assertEquals(
            listOf("pinned", "old", "new"),
            sortLocalPlaylists(rows, LocalPlaylistSortMode.OLDEST).map { it.id },
        )
    }

    @Test
    fun nameAndTrackCountSortsAreDeterministicAndKeepPinnedFirst() {
        val rows = listOf(
            playlist("z", "Zeta", createdAt = 1, trackCount = 1),
            playlist("a", "alpha", createdAt = 2, trackCount = 1),
            playlist("p", "Pinned", createdAt = 0, pinned = true),
            playlist("b", "Beta", createdAt = 3, trackCount = 4),
        )

        assertEquals(
            listOf("p", "a", "b", "z"),
            sortLocalPlaylists(rows, LocalPlaylistSortMode.NAME).map { it.id },
        )
        assertEquals(
            listOf("p", "b", "a", "z"),
            sortLocalPlaylists(rows, LocalPlaylistSortMode.TRACK_COUNT).map { it.id },
        )
    }

    @Test
    fun customSortPreservesThePersistedStoreOrder() {
        val rows = listOf(
            playlist("second", "Second", createdAt = 1),
            playlist("first", "First", createdAt = 100, pinned = true),
        )

        assertEquals(
            listOf("second", "first"),
            sortLocalPlaylists(rows, LocalPlaylistSortMode.CUSTOM).map { it.id },
        )
    }

    @Test
    fun appendsNewPlaylistSongsWithoutDuplicatesOrVideoUploads() {
        val existing = Song("existing", "Existing", "Artist", null)
        val next = Song("next", "Next", "Artist", null)

        assertEquals(
            listOf(existing, next),
            appendUniquePlaylistSong(listOf(existing), next),
        )
        assertNull(appendUniquePlaylistSong(listOf(existing), existing))
        assertNull(appendUniquePlaylistSong(emptyList(), next.copy(videoId = " ")))
        assertNull(appendUniquePlaylistSong(emptyList(), next.copy(isVideo = true)))
    }

    private fun playlist(
        id: String,
        title: String,
        createdAt: Long,
        pinned: Boolean = false,
        trackCount: Int = 0,
    ) = LocalPlaylist(
        id = id,
        title = title,
        songs = List(trackCount) { index ->
            Song(
                videoId = "$id-$index",
                title = "$title $index",
                artist = "Artist",
                thumbnailUrl = null,
            )
        },
        createdAtEpochMillis = createdAt,
        isPinned = pinned,
    )
}
