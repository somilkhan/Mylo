package com.music.bitchord

import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sync.AccountLibrarySnapshot
import com.music.bitchord.data.sync.AccountLibrarySnapshotStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AccountLibrarySnapshotStoreTest {
    @JvmField
    @Rule
    val temporaryFolder = TemporaryFolder()

    private fun song(id: String, title: String = id) = Song(
        videoId = id,
        title = title,
        artist = "Artist",
        thumbnailUrl = null,
    )

    private fun snapshot() = AccountLibrarySnapshot(
        library = LibraryPage(
            likedSongs = listOf(song("liked")),
            librarySongs = emptyList(),
            shelves = emptyList(),
        ),
        // Repeated plays are meaningful history entries and must not be
        // collapsed just because both rows point to the same video id.
        historySections = listOf(
            InnertubeParser.HistorySection("Today", listOf(song("repeat"), song("repeat"))),
            InnertubeParser.HistorySection("Yesterday", listOf(song("older"))),
        ),
        recentSongs = listOf(song("repeat")),
        savedAt = 1_799_000_000_000L,
    )

    @Test
    fun roundTripPreservesHistorySectionsAndRepeatedPlays() {
        val store = AccountLibrarySnapshotStore(temporaryFolder.newFolder())
        val expected = snapshot()

        store.write("google-account:profile-one", expected)

        assertEquals(expected, store.read("google-account:profile-one"))
    }

    @Test
    fun snapshotsAreIsolatedByAccountAndProfile() {
        val store = AccountLibrarySnapshotStore(temporaryFolder.newFolder())
        store.write("google-account:profile-one", snapshot())

        assertNull(store.read("google-account:profile-two"))
        assertNull(store.read("another-account:profile-one"))
    }

    @Test
    fun corruptSnapshotDoesNotEscapeAsPartialHistory() {
        val directory = temporaryFolder.newFolder()
        val store = AccountLibrarySnapshotStore(directory)
        store.write("google-account:profile-one", snapshot())
        directory.listFiles()!!.single { it.name.endsWith(".json") }.writeText("{broken")

        assertNull(store.read("google-account:profile-one"))
    }
}
