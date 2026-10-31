package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.Song
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun song(id: String) = Song(id, id, "Artist", null)
}
