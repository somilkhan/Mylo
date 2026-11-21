package com.music.bitchord.ui.scrobble

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerateCommitTest {

    @Test
    fun cancellationBeforeGenerationReturnsNeverCommits() = runTest {
        val generated = CompletableDeferred<String>()
        var commits = 0
        val job = launch {
            generateAndCommit(
                generate = { generated.await() },
                commit = { commits++ },
            )
        }

        runCurrent()
        job.cancelAndJoin()
        generated.complete("playlist")

        assertEquals(0, commits)
    }

    @Test
    fun cancellationAfterProviderReturnsStillBlocksCommit() = runTest {
        var commits = 0
        val job = launch {
            try {
                generateAndCommit(
                    generate = {
                        currentCoroutineContext().cancel()
                        "generated tracks"
                    },
                    commit = { commits++ },
                )
            } catch (_: CancellationException) {
                // Expected: ensureActive rejects the commit after cancellation.
            }
        }

        job.join()
        assertEquals(0, commits)
    }

    @Test
    fun similarPlaylistTitleUsesSourceNameAndAvoidsDuplicates() {
        assertEquals("Focus Mix", uniquePlaylistTitle("Focus Mix", emptyList()))
        assertEquals("Focus Mix (2)", uniquePlaylistTitle("Focus Mix", listOf("focus mix")))
        assertEquals("My Mix", uniquePlaylistTitle("   ", emptyList()))
    }

    @Test
    fun activeGenerationCommitsExactlyOnce() = runTest {
        var committed: String? = null

        val result = generateAndCommit(
            generate = { "generated tracks" },
            commit = { value ->
                committed = value
                value
            },
        )

        assertEquals("generated tracks", result)
        assertEquals("generated tracks", committed)
    }
}
