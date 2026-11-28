package com.music.bitchord.data.generate

import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository.SongPage
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class GenerationPagingTest {
    @Test
    fun includesEveryPageAndDeduplicatesIds() = runBlocking {
        val first = song("one")
        val second = song("two")
        val result = completeGenerationSongFeed(SongPage(listOf(first), "next")) { token ->
            assertEquals("next", token)
            SongPage(listOf(first, second), null)
        }
        assertEquals(listOf(first, second), result)
    }

    @Test
    fun rejectsRepeatedContinuationRatherThanReturningPartialExclusions() = runBlocking {
        var requests = 0
        try {
            completeGenerationSongFeed(SongPage(listOf(song("one")), "repeat")) {
                requests++
                SongPage(listOf(song("two")), "repeat")
            }
            fail("A repeating feed must not look complete")
        } catch (error: GenerationException) {
            assertEquals(R.string.scrobble_gen_error_exclusions_unavailable, error.resource)
        }
        assertEquals(1, requests)
    }

    @Test
    fun pageCapFailsClosedButExactlyCompleteFeedSucceeds() = runBlocking {
        try {
            completeGenerationSongFeed(SongPage(listOf(song("one")), "next"), maxPages = 1) {
                fail("No request beyond the bound")
                SongPage(emptyList(), null)
            }
            fail("A capped partial feed must fail")
        } catch (_: GenerationException) {
            // Expected: no partial exclusion set is returned.
        }
        assertEquals(
            listOf(song("one")),
            completeGenerationSongFeed(SongPage(listOf(song("one")), null), maxPages = 1) {
                error("No continuation expected")
            },
        )
    }

    @Test
    fun failedMandatoryReadDoesNotBecomeAnEmptyList() = runBlocking {
        try {
            generationExclusionRead<List<Song>> { throw IllegalStateException("provider detail") }
            fail("Mandatory reads must fail closed")
        } catch (error: GenerationException) {
            assertEquals(R.string.scrobble_gen_error_exclusions_unavailable, error.resource)
        }
    }

    @Test
    fun mandatoryReadPreservesCancellationIdentity() = runBlocking {
        val cancellation = CancellationException("cancel")
        try {
            generationExclusionRead<List<Song>> { throw cancellation }
            fail("Cancellation must propagate")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }
    }

    private fun song(id: String) = Song(id, "Track $id", "Artist", null)
}
