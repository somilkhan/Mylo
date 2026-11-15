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
    fun savedMixLimitNeverSilentlyDeletesAnOlderPlaylist() {
        ScrobblePlaylistStore.playlists.clear()
        try {
            repeat(20) { index ->
                val song = Song("video${index.toString().padStart(6, '0')}", "Track $index", "Artist", null)
                assertEquals(true, ScrobblePlaylistStore.save("Mix $index", "Test", listOf(song)))
            }
            val oldestTitle = ScrobblePlaylistStore.playlists.last().title
            assertEquals(false, ScrobblePlaylistStore.save("One more", "Test", listOf(Song("another0001", "Another", "Artist", null))))
            assertEquals(20, ScrobblePlaylistStore.playlists.size)
            assertEquals(oldestTitle, ScrobblePlaylistStore.playlists.last().title)
        } finally {
            ScrobblePlaylistStore.playlists.clear()
        }
    }

    @Test
    fun renameTrimsTitleAndPreservesTheSavedSnapshot() {
        val song = Song("abcdefghijk", "Track", "Artist", null)
        ScrobblePlaylistStore.playlists.clear()
        try {
            ScrobblePlaylistStore.save("Old title", "Generated from favourites", listOf(song))

            assertEquals(true, ScrobblePlaylistStore.rename("Old title", "  New title  "))
            assertEquals("New title", ScrobblePlaylistStore.playlists.single().title)
            assertEquals("Generated from favourites", ScrobblePlaylistStore.playlists.single().reason)
            assertEquals(song, ScrobblePlaylistStore.playlists.single().songs.single())
        } finally {
            ScrobblePlaylistStore.playlists.clear()
        }
    }

    @Test
    fun renameRejectsBlankNamesAndCaseInsensitiveDuplicates() {
        ScrobblePlaylistStore.playlists.clear()
        try {
            ScrobblePlaylistStore.save("First", "Reason", listOf(Song("abcdefghijk", "One", "Artist", null)))
            ScrobblePlaylistStore.save("Second", "Reason", listOf(Song("lmnopqrstuv", "Two", "Artist", null)))

            assertEquals(false, ScrobblePlaylistStore.rename("First", "   "))
            assertEquals(false, ScrobblePlaylistStore.rename("First", "sEcOnD"))
            assertEquals(false, ScrobblePlaylistStore.rename("Missing", "Renamed"))
            assertEquals(listOf("Second", "First"), ScrobblePlaylistStore.playlists.map { it.title })
        } finally {
            ScrobblePlaylistStore.playlists.clear()
        }
    }

    @Test
    fun olderPlaylistSnapshotsDecodeWithPinnedDefaultingToFalse() {
        val legacy = """{"version":1,"playlists":[{"title":"Old mix","reason":"Saved before pinning","songs":[{"videoId":"abcdefghijk","title":"Track","artist":"Artist"}]}]}"""

        val restored = ScrobblePlaylistCodec.decodeOrNull(legacy).orEmpty().single()

        assertEquals("Old mix", restored.title)
        assertEquals(false, restored.pinned)
        assertEquals("abcdefghijk", restored.songs.single().videoId)
    }

    @Test
    fun pinnedStateRoundTripsAndCanBeToggledWithoutChangingTracks() {
        val song = Song("abcdefghijk", "Track", "Artist", null)
        val original = SavedScrobblePlaylist("Pinned mix", "Reason", listOf(song), pinned = true)

        val restored = ScrobblePlaylistCodec.decodeOrNull(
            ScrobblePlaylistCodec.encode(listOf(original)),
        ).orEmpty().single()

        assertEquals(true, restored.pinned)
        assertEquals(listOf(song), restored.songs)

        ScrobblePlaylistStore.playlists.clear()
        try {
            ScrobblePlaylistStore.playlists.add(restored)
            assertEquals(true, ScrobblePlaylistStore.setPinned("Pinned mix", false))
            assertEquals(false, ScrobblePlaylistStore.playlists.single().pinned)
            assertEquals(listOf(song), ScrobblePlaylistStore.playlists.single().songs)
            assertEquals(false, ScrobblePlaylistStore.setPinned("Missing", true))
        } finally {
            ScrobblePlaylistStore.playlists.clear()
        }
    }

    @Test
    fun removingTrackOnlyChangesTheTargetPlaylistSnapshot() {
        val first = Song("abcdefghijk", "First", "Artist", null)
        val second = Song("lmnopqrstuv", "Second", "Artist", null)
        ScrobblePlaylistStore.playlists.clear()
        try {
            ScrobblePlaylistStore.save("Mix A", "Reason", listOf(first, second))
            ScrobblePlaylistStore.save("Mix B", "Reason", listOf(first))

            assertEquals(true, ScrobblePlaylistStore.removeSong("Mix A", 0))
            assertEquals(listOf(second), ScrobblePlaylistStore.playlists.first { it.title == "Mix A" }.songs)
            assertEquals(listOf(first), ScrobblePlaylistStore.playlists.first { it.title == "Mix B" }.songs)
            assertEquals(false, ScrobblePlaylistStore.removeSong("Missing", 0))
            assertEquals(false, ScrobblePlaylistStore.removeSong("Mix A", 4))
        } finally {
            ScrobblePlaylistStore.playlists.clear()
        }
    }

    @Test
    fun movingTrackPersistsNewOrderAndRejectsInvalidIndexes() {
        val first = Song("abcdefghijk", "First", "Artist", null)
        val second = Song("lmnopqrstuv", "Second", "Artist", null)
        val third = Song("zyxwvutsrqp", "Third", "Artist", null)
        ScrobblePlaylistStore.playlists.clear()
        try {
            ScrobblePlaylistStore.save("Ordered", "Reason", listOf(first, second, third))

            assertEquals(true, ScrobblePlaylistStore.moveSong("Ordered", 0, 2))
            assertEquals(listOf(second, third, first), ScrobblePlaylistStore.playlists.single().songs)
            assertEquals(false, ScrobblePlaylistStore.moveSong("Ordered", 3, 0))
            assertEquals(false, ScrobblePlaylistStore.moveSong("Missing", 0, 1))
            assertEquals(true, ScrobblePlaylistStore.moveSong("Ordered", 1, 1))
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

    @Test
    fun customCoverUriRoundTripsAndLegacyRecordsDefaultToAutomaticCover() {
        val song = Song("abcdefghijk", "Track", "Artist", null)
        val playlist = SavedScrobblePlaylist(
            title = "Covered mix",
            reason = "Inspired by favourites",
            songs = listOf(song),
            coverUri = "content://media/external/images/media/42",
        )

        val restored = ScrobblePlaylistCodec.decodeOrNull(
            ScrobblePlaylistCodec.encode(listOf(playlist)),
        ).orEmpty().single()

        assertEquals(playlist.coverUri, restored.coverUri)
        assertEquals(song, restored.songs.single())

        val legacy = """{"version":1,"playlists":[{"title":"Legacy","reason":"Old mix","songs":[{"videoId":"abcdefghijk","title":"Track","artist":"Artist"}]}]}"""
        val legacyPlaylist = ScrobblePlaylistCodec.decodeOrNull(legacy).orEmpty().single()
        assertNull(legacyPlaylist.coverUri)
    }

    @Test
    fun changingCoverPreservesPlaylistSnapshotAndReturnsPreviousUri() {
        val song = Song("abcdefghijk", "Track", "Artist", null)
        ScrobblePlaylistStore.playlists.clear()
        try {
            ScrobblePlaylistStore.save("Covered", "Original", listOf(song))
            assertNull(ScrobblePlaylistStore.setCover("Covered", "content://cover/one"))
            assertEquals("content://cover/one", ScrobblePlaylistStore.setCover("Covered", "content://cover/two"))
            assertEquals("content://cover/two", ScrobblePlaylistStore.playlists.single().coverUri)
            assertEquals(listOf(song), ScrobblePlaylistStore.playlists.single().songs)
            assertEquals("content://cover/two", ScrobblePlaylistStore.remove("Covered"))
        } finally {
            ScrobblePlaylistStore.playlists.clear()
        }
    }


    @Test
    fun customPlaylistCanBeEmptyAndSurvivesCodecRoundTrip() {
        val custom = SavedScrobblePlaylist("Road trip", "Custom playlist", emptyList())
        val restored = ScrobblePlaylistCodec.decodeOrNull(
            ScrobblePlaylistCodec.encode(listOf(custom)),
        ).orEmpty().single()

        assertEquals(custom.title, restored.title)
        assertEquals(custom.reason, restored.reason)
        assertEquals(emptyList<Song>(), restored.songs)
    }

    @Test
    fun createRejectsBlankDuplicateAndLimitAndAddSongsDeduplicates() {
        ScrobblePlaylistStore.playlists.clear()
        try {
            assertEquals(false, ScrobblePlaylistStore.create("   "))
            assertEquals(true, ScrobblePlaylistStore.create("Road trip"))
            assertEquals(false, ScrobblePlaylistStore.create("ROAD TRIP"))

            val first = Song("abcdefghijk", "First", "Artist", null)
            val second = Song("lmnopqrstuv", "Second", "Artist", null)
            assertEquals(2, ScrobblePlaylistStore.addSongs("Road trip", listOf(first, first, second)))
            assertEquals(0, ScrobblePlaylistStore.addSongs("Road trip", listOf(first, second)))
            assertEquals(listOf(first, second), ScrobblePlaylistStore.playlists.single().songs)
        } finally {
            ScrobblePlaylistStore.playlists.clear()
        }
    }


}
