package com.music.bitchord.data.spotify

import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercise the actual store transactions with controlled SharedPreferences writes. */
class LocalPlaylistMutationTest {
    private val prefsField = LocalPlaylistStore::class.java.getDeclaredField("prefs").apply { isAccessible = true }
    private var originalPrefs: Any? = null
    private var commitSucceeds = true
    private var commitStarted: CountDownLatch? = null
    private var releaseCommit: CountDownLatch? = null
    private var legacyApplied: CountDownLatch? = null

    @Before fun installControlledPreferences() {
        originalPrefs = prefsField.get(LocalPlaylistStore)
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, _ ->
            when (method.name) {
                "commit" -> {
                    commitStarted?.countDown()
                    check(releaseCommit?.await(5, TimeUnit.SECONDS) != false) { "Controlled write timed out" }
                    commitSucceeds
                }
                "apply" -> { legacyApplied?.countDown(); null }
                else -> proxy
            }
        }
        val prefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "edit" -> editor
                else -> error("Unexpected preferences call: ${method.name}")
            }
        }
        prefsField.set(LocalPlaylistStore, prefs)
        LocalPlaylistStore.setPlaylistsForTest(emptyList())
    }

    @After fun restorePreferences() {
        releaseCommit?.countDown()
        LocalPlaylistStore.setPlaylistsForTest(emptyList())
        prefsField.set(LocalPlaylistStore, originalPrefs)
    }

    private fun song(id: String) = Song(id, id, "Artist", null)
    private fun playlist(id: String, vararg songs: String) = LocalPlaylist(id, id, songs.map(::song))

    @Test fun failedRenameDeleteAndAddLeaveThePublishedPlaylistUnchanged() = runBlocking {
        val playlist = playlist("playlist", "a", "b")
        LocalPlaylistStore.setPlaylistsForTest(listOf(playlist))
        commitSucceeds = false
        assertFalse(LocalPlaylistStore.renamePlaylist(playlist.id, "Renamed"))
        assertFalse(LocalPlaylistStore.deletePlaylist(playlist.id))
        assertFalse(LocalPlaylistStore.addSongIfAbsent(playlist.id, song("c")))
        assertFalse(LocalPlaylistStore.moveSong(playlist.id, 0, "a", 1))
        assertFalse(LocalPlaylistStore.removeSong(playlist.id, 0, "a"))
        assertFalse(LocalPlaylistStore.setCustomCover(playlist.id, "content://cover"))
        assertEquals(listOf(playlist), LocalPlaylistStore.playlists.value)
    }

    @Test fun relativePlaylistMoveUsesTheLivePersistedOrder() = runBlocking {
        val rows = listOf(playlist("inserted"), playlist("a"), playlist("b"), playlist("c"))
        LocalPlaylistStore.setPlaylistsForTest(rows)
        assertTrue(LocalPlaylistStore.movePlaylistBy("b", 1))
        assertEquals(listOf("inserted", "a", "c", "b"), LocalPlaylistStore.playlists.value.map { it.id })
        assertTrue(LocalPlaylistStore.movePlaylistBy("local:playlist:b", -1))
        assertEquals(rows, LocalPlaylistStore.playlists.value)
    }

    @Test fun queuedLegacyRemovalDoesNotDiscardAConcurrentAdd() = runBlocking {
        val playlist = playlist("playlist", "a", "b")
        LocalPlaylistStore.setPlaylistsForTest(listOf(playlist))
        commitStarted = CountDownLatch(1)
        releaseCommit = CountDownLatch(1)
        legacyApplied = CountDownLatch(1)
        val add = async(start = CoroutineStart.UNDISPATCHED) {
            LocalPlaylistStore.addSongIfAbsent(playlist.id, song("c"))
        }
        try {
            assertTrue("The add must own the mutation lock", commitStarted!!.await(5, TimeUnit.SECONDS))
            LocalPlaylistStore.removeSong(playlist.browseId, song("b"))
        } finally {
            releaseCommit!!.countDown()
        }
        assertTrue(add.await())
        val result = withTimeout(5_000) {
            LocalPlaylistStore.playlists.first { rows -> rows.single().songs.map { it.videoId } == listOf("a", "c") }
        }
        assertTrue("Legacy persistence must finish before restoring shared state", legacyApplied!!.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("a", "c"), result.single().songs.map { it.videoId })
    }
}
