package com.music.bitchord.ui.screens.scrobble

import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobblePlaylistCodecTest {
    @Test
    fun pinnedPlaylistRoundTripsWithTrackMetadata() {
        val playlist = SavedScrobblePlaylist(
            id = "playlist-1",
            title = "Velvet Horizon",
            subtitle = "Recommendations",
            tracks = listOf(
                Song(
                    videoId = "video000001",
                    title = "Blue Monday",
                    artist = "New Order",
                    thumbnailUrl = "https://example.test/art.jpg",
                    artists = listOf(ArtistRef("New Order", "UC123")),
                    albumName = "Power, Corruption & Lies",
                    sourceQuality = "LOSSLESS",
                    sourceHiRes = true,
                ),
            ),
            createdAtMillis = 1234L,
            pinned = true,
            customCoverUri = "content://covers/1",
        )

        val decoded = ScrobblePlaylistCodec.decode(ScrobblePlaylistCodec.encode(listOf(playlist)))

        assertEquals(playlist.id, decoded.single().id)
        assertEquals(playlist.title, decoded.single().title)
        assertEquals(playlist.subtitle, decoded.single().subtitle)
        assertEquals(playlist.pinned, decoded.single().pinned)
        assertEquals(playlist.customCoverUri, decoded.single().customCoverUri)
        assertEquals(playlist.tracks.single().videoId, decoded.single().tracks.single().videoId)
        assertEquals(playlist.tracks.single().artists, decoded.single().tracks.single().artists)
        assertEquals("LOSSLESS", decoded.single().tracks.single().sourceQuality)
        assertTrue(decoded.single().tracks.single().sourceHiRes)
    }

    @Test
    fun legacySnapshotWithoutPinnedAndCoverDefaultsSafely() {
        val legacy = """[{"id":"old","title":"Old Mix","tracks":[{"videoId":"video000001","title":"Track","artist":"Artist"}],"createdAtMillis":42}]"""

        val decoded = ScrobblePlaylistCodec.decode(legacy).single()

        assertFalse(decoded.pinned)
        assertNull(decoded.customCoverUri)
        assertEquals("", decoded.subtitle)
        assertEquals("Track", decoded.tracks.single().title)
    }

    @Test
    fun codecKeepsFirstDuplicateTrackAndPreservesOrder() {
        val first = Song("video000001", "Blue Monday", "New Order", "first-art")
        val second = Song("video000002", "blue   monday", "new order", "second-art")
        val playlist = SavedScrobblePlaylist(
            id = "dedupe",
            title = "Dedupe",
            subtitle = "",
            tracks = listOf(first, second),
            createdAtMillis = 5L,
        )

        val decoded = ScrobblePlaylistCodec.decode(ScrobblePlaylistCodec.encode(listOf(playlist)))

        assertEquals(2, decoded.single().tracks.size)
        assertEquals("first-art", decoded.single().tracks.first().thumbnailUrl)
        assertEquals("second-art", decoded.single().tracks.last().thumbnailUrl)
    }
}
