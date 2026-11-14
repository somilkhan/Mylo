package com.music.bitchord.data.sync

import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLibrarySnapshotStoreTest {
    @Test
    fun snapshotRoundTripsLibraryHistoryAndRecentActivityPerAccount() {
        val directory = Files.createTempDirectory("account-library").toFile()
        try {
            val store = AccountLibrarySnapshotStore(directory)
            val song = Song(
                videoId = "abcdefghijk",
                title = "Track",
                artist = "Artist",
                thumbnailUrl = "https://example.test/art.jpg",
                durationText = "3:10",
                artistId = "UCartist",
                albumId = "MPREalbum",
                albumName = "Album",
            )
            val snapshot = AccountLibrarySnapshot(
                library = LibraryPage(
                    likedSongs = listOf(song),
                    librarySongs = listOf(song.copy(videoId = "zyxwvutsrqp")),
                    shelves = listOf(HomeShelf(
                        title = "Albums",
                        items = listOf(ShelfItem("Album", "Artist", null, null, "MPREalbum")),
                    )),
                ),
                historySections = listOf(InnertubeParser.HistorySection("Today", listOf(song, song))),
                recentSongs = listOf(song),
                savedAt = 1_800_000_000_000L,
            )

            store.write("account-a", snapshot)
            store.write("account-b", snapshot.copy(recentSongs = emptyList()))

            val restored = store.read("account-a")!!
            assertEquals(snapshot.savedAt, restored.savedAt)
            assertEquals("abcdefghijk", restored.library.likedSongs.single().videoId)
            assertEquals("UCartist", restored.library.likedSongs.single().artistId)
            assertEquals("MPREalbum", restored.library.shelves.single().items.single().browseId)
            assertEquals(2, restored.historySections.single().songs.size)
            assertEquals(1, restored.recentSongs.size)
            assertTrue(store.read("account-b")!!.recentSongs.isEmpty())

            // A process may stop after the old snapshot has been moved aside but
            // before the replacement is installed. The backup must remain readable.
            val snapshotFile = directory.listFiles()!!.single { it.name.endsWith(".json") && it.readText().contains("\"account\":\"account-a\"") }
            val backupFile = File(directory, snapshotFile.name + ".bak")
            assertTrue(snapshotFile.renameTo(backupFile))
            assertEquals(snapshot.savedAt, store.read("account-a")!!.savedAt)
            assertTrue(snapshotFile.isFile)

            store.write("account-a", snapshot.copy(library = snapshot.library.copy(likedSongs = emptyList())))
            assertTrue(store.read("account-a")!!.library.likedSongs.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun missingOrDifferentAccountSnapshotIsNotReturned() {
        val directory = Files.createTempDirectory("account-library-empty").toFile()
        try {
            val store = AccountLibrarySnapshotStore(directory)
            assertNull(store.read("unknown"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
