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
}
