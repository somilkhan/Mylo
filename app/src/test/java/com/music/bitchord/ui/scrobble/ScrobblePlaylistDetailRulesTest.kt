package com.music.bitchord.ui.scrobble

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylist
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrobblePlaylistDetailRulesTest {
    @Test fun duplicateTracksHaveDistinctStableRowKeys() {
        val a = Song("a", "A", "Artist", null)
        val b = Song("b", "B", "Artist", null)
        val playlist = LocalPlaylist("playlist", "Playlist", listOf(a, b, a))
        val keys = playlistTrackRowKeys(playlist)
        assertEquals(3, keys.distinct().size)
        assertEquals(keys, playlistTrackRowKeys(playlist.copy(title = "Renamed")))
        assertEquals(listOf(keys[1], keys[0], keys[2]), playlistTrackRowKeys(playlist.copy(songs = listOf(b, a, a))))
    }

    @Test fun locatorAccountsForTheOptionalSearchResultsItem() {
        assertEquals(3, playlistTrackListIndex(0, hasAddResults = false))
        assertEquals(4, playlistTrackListIndex(0, hasAddResults = true))
        assertEquals(8, playlistTrackListIndex(5, hasAddResults = false))
        assertEquals(9, playlistTrackListIndex(5, hasAddResults = true))
    }
}
