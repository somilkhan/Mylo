package com.music.bitchord.data.sync

import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRulesTest {
    @Test fun onlyNewestSyncGenerationOwnsSharedState() {
        val generations = SyncJobGeneration()
        val first = generations.begin()
        assertTrue(generations.owns(first))

        val second = generations.begin()
        assertFalse(generations.owns(first))
        assertTrue(generations.owns(second))

        generations.invalidate()
        assertFalse(generations.owns(second))
    }

    @Test fun automaticSyncRequiresValidatedInternetAndRespectsBatterySaver() {
        assertFalse(automaticSyncEligible(false, true, false, false))
        assertFalse(automaticSyncEligible(true, false, false, false))
        assertTrue(automaticSyncEligible(true, true, false, true))
        assertFalse(automaticSyncEligible(true, true, true, true))
        assertTrue(automaticSyncEligible(true, true, true, false))
        // Unknown network cost must never be treated as an unmetered connection.
        assertFalse(automaticSyncEligible(true, true, true, null))
        assertTrue(automaticSyncEligible(true, true, false, null))
    }

    @Test fun onlyCompleteNonEmptyReadsMayRemove() {
        assertTrue(mayRemoveMissing(true, 20))
        assertFalse(mayRemoveMissing(false, 20))
        assertFalse(mayRemoveMissing(true, 0))
    }

    @Test fun emptyRemoteNeverReplacesPopulatedPlaylist() {
        assertFalse(mayReplacePlaylist(true, 0, true))
        assertFalse(mayReplacePlaylist(false, 20, true))
        assertTrue(mayReplacePlaylist(true, 20, true))
        assertTrue(mayReplacePlaylist(true, 0, false))
    }

    @Test fun emptyCategoryRefreshKeepsLastKnownGoodRows() {
        assertEquals(listOf("saved"), preserveNonEmptyOnEmptyRemote(listOf("saved"), emptyList()))
        assertEquals(listOf("remote"), preserveNonEmptyOnEmptyRemote(listOf("saved"), listOf("remote")))
        assertTrue(preserveNonEmptyOnEmptyRemote(emptyList<String>(), emptyList()).isEmpty())
    }

    @Test fun addOnlyKeepsLocalRowsWhileOverwriteUsesRemoteRows() {
        val local = listOf("a", "b")
        val remote = listOf("b", "c")
        assertEquals(listOf("b", "c", "a"), mergeRemoteRows(local, remote, false) { it })
        assertEquals(listOf("b", "c"), mergeRemoteRows(local, remote, true) { it })
        assertEquals(local, mergeRemoteRows(local, emptyList(), true) { it })
    }

    @Test fun manualSyncReportsFailureAndBusyAccurately() {
        assertEquals(SyncResult.FAILED, SyncResult.combine(listOf(SyncResult.SYNCED, SyncResult.FAILED)))
        assertEquals(SyncResult.BUSY, SyncResult.combine(listOf(SyncResult.BUSY, SyncResult.NOTHING)))
        assertEquals(SyncResult.NOTHING, SyncResult.combine(emptyList()))
    }

    @Test fun unlikesRequirePreviousSnapshotAndCompleteNonEmptyRead() {
        val now = LocalDateTime.parse("2026-11-09T12:00:00")
        val likes = listOf(
            LikedSync.LocalLike("old", now.minusDays(10)),
            LikedSync.LocalLike("new", now.minusDays(1)),
            LikedSync.LocalLike("never-synced", now.minusDays(10)),
        )
        assertEquals(setOf("old"), LikedSync.idsToUnlike(
            likes, setOf("still-liked"), complete = true, snapshot = setOf("old", "new"),
            addOnly = false, now = now, graceDays = 3,
        ))
        assertTrue(LikedSync.idsToUnlike(
            likes, emptySet(), complete = true, snapshot = setOf("old", "new"),
            addOnly = false, now = now, graceDays = 3,
        ).isEmpty())
        assertTrue(LikedSync.idsToUnlike(
            likes, setOf("still-liked"), complete = false, snapshot = setOf("old"),
            addOnly = false, now = now, graceDays = 3,
        ).isEmpty())
        assertTrue(LikedSync.idsToUnlike(
            likes, setOf("still-liked"), complete = true, snapshot = null,
            addOnly = false, now = now, graceDays = 3,
        ).isEmpty())
    }

    @Test fun songCountHandlesLocaleSeparatorsAndIncompleteWalks() {
        assertEquals(1234, LikedSync.parseSongCount("1,234 songs"))
        assertEquals(1234, LikedSync.parseSongCount("1 234 titres"))
        assertTrue(LikedSync.readLooksComplete(95, 100))
        assertFalse(LikedSync.readLooksComplete(70, 100))
        assertTrue(LikedSync.readLooksComplete(0, null))
    }

    @Test fun accountIdentityPrefersDataSyncIdAndNormalizesDelegatedValue() {
        assertEquals("dsid:active", LikedSync.accountKey("base||active", "other@example.com"))
        assertEquals("dsid:base", LikedSync.accountKey("base||", null))
        assertEquals("email:user@example.com", LikedSync.accountKey(null, " User@Example.com "))
        assertNull(LikedSync.accountKey(null, null))
    }

    @Test fun likedSnapshotsAreIsolatedByAccountAndReplaceAsAWhole() {
        val dir = Files.createTempDirectory("liked-sync-test").toFile()
        try {
            val store = LikedSnapshotStore(dir)
            assertNull(store.read("dsid:a"))
            store.write("dsid:a", setOf("one", "two"))
            store.write("dsid:b", setOf("three"))
            assertEquals(setOf("one", "two"), store.read("dsid:a"))
            assertEquals(setOf("three"), store.read("dsid:b"))
            store.write("dsid:a", setOf("two"))
            assertEquals(setOf("two"), store.read("dsid:a"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
