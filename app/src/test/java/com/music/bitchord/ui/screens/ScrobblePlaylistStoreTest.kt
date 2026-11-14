package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrobblePlaylistStoreTest {
    @Test
    fun codecRoundTripsSavedTrackMetadataWithoutPersistingQueueSessionState() {
        val song = Song(
            videoId = "abcdefghijk",
            title = "Test track",
            artist = "Test artist",
            thumbnailUrl = "https://example.com/art.jpg",
            durationText = "3:42",
            albumName = "Test album",
        )
        val original = SavedScrobblePlaylist("My mix", "A test mix", listOf(song))

        val restored = ScrobblePlaylistCodec.decodeOrNull(ScrobblePlaylistCodec.encode(listOf(original))).orEmpty().single()

        assertEquals(original.title, restored.title)
        assertEquals(original.reason, restored.reason)
        assertEquals(song.videoId, restored.songs.single().videoId)
        assertEquals(song.title, restored.songs.single().title)
        assertEquals(song.artist, restored.songs.single().artist)
        assertEquals(song.albumName, restored.songs.single().albumName)
        assertNull(restored.songs.single().queueEntryId)
    }

    @Test
    fun savingNewSnapshotDoesNotSilentlyOverwriteAnExistingPlaylist() {
        val first = Song("abcdefghijk", "First", "Artist", null)
        val second = Song("lmnopqrstuv", "Second", "Artist", null)
        ScrobblePlaylistStore.playlists.clear()

        try {
            ScrobblePlaylistStore.save("Your favourites", "Most played", listOf(first))
            ScrobblePlaylistStore.save("Your favourites", "Most played", listOf(second))

            assertEquals(listOf("Your favourites 2", "Your favourites"), ScrobblePlaylistStore.playlists.map { it.title })
            assertEquals("lmnopqrstuv", ScrobblePlaylistStore.playlists.first().songs.single().videoId)
        } finally {
            ScrobblePlaylistStore.playlists.clear()
        }
    }

    @Test
    fun identicalSavedSnapshotIsNotDuplicated() {
        val song = Song("abcdefghijk", "First", "Artist", null)
        ScrobblePlaylistStore.playlists.clear()

        try {
            ScrobblePlaylistStore.save("Your favourites", "Most played", listOf(song))
            ScrobblePlaylistStore.save("Your favourites", "Most played", listOf(song))

            assertEquals(1, ScrobblePlaylistStore.playlists.size)
        } finally {
            ScrobblePlaylistStore.playlists.clear()
        }
    }
}
