package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.Song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScrobblePlaylistStoreTest {

    @Before
    fun clearBefore() {
        ScrobblePlaylistStore.playlists.clear()
    }

    @After
    fun clearAfter() {
        ScrobblePlaylistStore.playlists.clear()
    }

    @Test
    fun savingSamePlaylistReplacesItsSnapshotInsteadOfDuplicatingIt() {
        ScrobblePlaylistStore.save("Top tracks", "Old reason", listOf(song("old")))
        ScrobblePlaylistStore.save("Top tracks", "Updated reason", listOf(song("new")))

        assertEquals(1, ScrobblePlaylistStore.playlists.size)
        assertEquals("Updated reason", ScrobblePlaylistStore.playlists.single().reason)
        assertEquals(listOf("new"), ScrobblePlaylistStore.playlists.single().songs.map(Song::videoId))
    }

    @Test
    fun emptyMixesAreNotSavedAndSavedMixesCanBeRemoved() {
        ScrobblePlaylistStore.save("Empty", "No tracks", emptyList())
        assertTrue(ScrobblePlaylistStore.playlists.isEmpty())

        ScrobblePlaylistStore.save("Recent", "Last seven days", listOf(song("recent")))
        ScrobblePlaylistStore.remove("Recent")

        assertTrue(ScrobblePlaylistStore.playlists.isEmpty())
    }

    @Test
    fun codecRoundTripsPlaylistMetadataAndLocalFileIdentity() {
        val original = listOf(
            SavedScrobblePlaylist(
                title = "Local rediscovery",
                reason = "A mix with a local file",
                songs = listOf(
                    Song(
                        videoId = "",
                        title = "Offline track",
                        artist = "Local artist",
                        thumbnailUrl = null,
                        artists = listOf(ArtistRef("Local artist", "UCartist")),
                        localUri = "content://media/external/audio/media/42",
                        localPath = "/storage/emulated/0/Music/offline.flac",
                        durationText = "4:12",
                        trackNumber = 7,
                    ),
                ),
            ),
        )

        val restored = ScrobblePlaylistCodec.decodeOrNull(ScrobblePlaylistCodec.encode(original))

        assertEquals(original, restored)
    }

    @Test
    fun malformedOrUnknownSchemaDoesNotDecodeAsAnEmptyPlaylistList() {
        assertNull(ScrobblePlaylistCodec.decodeOrNull("not-json"))
        assertNull(ScrobblePlaylistCodec.decodeOrNull("""{"version":99,"playlists":[]}"""))
    }

    private fun song(id: String) = Song(id, id, "Artist", null)
}
