package com.music.bitchord.data.scrobbling

import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ListenBrainzScrobbleQueueTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun flushesPendingEntriesInFifoOrder() = runBlocking {
        val sent = mutableListOf<String>()
        val queue = queue(file()) { entry ->
            sent += entry.videoId
            Result.success(Unit)
        }
        queue.enqueue(entry("first", 1L))
        queue.enqueue(entry("second", 2L))
        queue.enqueue(entry("third", 3L))

        queue.flush()

        assertEquals(listOf("first", "second", "third"), sent)
        assertTrue(queue.pendingEntries().isEmpty())
    }

    @Test
    fun unauthorizedKeepsEntriesAndPausesUntilSettingsChange() = runBlocking {
        for (code in listOf(401, 403)) {
            val sent = mutableListOf<String>()
            var rejected = true
            val queue = queue(file("auth-$code.json")) { entry ->
                sent += entry.videoId
                if (rejected) Result.failure(ListenBrainzHttpException(code)) else Result.success(Unit)
            }
            queue.enqueue(entry("a", 1L))
            queue.enqueue(entry("b", 2L))
            queue.enqueue(entry("c", 3L))

            queue.flush()
            assertEquals(listOf("a"), sent)
            assertEquals(3, queue.pendingEntries().size)

            // Paused: a second flush must not call the server at all.
            queue.flush()
            assertEquals(listOf("a"), sent)

            rejected = false
            queue.resumeAfterSettingsChange()
            queue.flush()
            assertEquals(listOf("a", "a", "b", "c"), sent)
            assertTrue(queue.pendingEntries().isEmpty())
        }
    }

    @Test
    fun rateLimitBacksOffThenSucceeds() = runBlocking {
        val pauses = mutableListOf<Long>()
        var attempts = 0
        val queue = queue(file(), pauses = pauses) {
            attempts++
            if (attempts == 1) Result.failure(ListenBrainzHttpException(429)) else Result.success(Unit)
        }
        queue.enqueue(entry("a", 1L))

        queue.flush()

        assertEquals(2, attempts)
        assertEquals(listOf(30_000L), pauses)
        assertTrue(queue.pendingEntries().isEmpty())
    }

    @Test
    fun retryAfterOnRateLimitReplacesComputedBackoff() = runBlocking {
        val pauses = mutableListOf<Long>()
        var attempts = 0
        val queue = queue(file(), pauses = pauses) {
            attempts++
            if (attempts == 1) {
                Result.failure(ListenBrainzHttpException(429, retryAfterMs = 5_000L))
            } else {
                Result.success(Unit)
            }
        }
        queue.enqueue(entry("a", 1L))

        queue.flush()

        assertEquals(listOf(5_000L), pauses)
        assertTrue(queue.pendingEntries().isEmpty())
    }

    @Test
    fun ioFailureIsRetriedNotDropped() = runBlocking {
        val pauses = mutableListOf<Long>()
        var attempts = 0
        val queue = queue(file(), pauses = pauses) {
            attempts++
            if (attempts == 1) Result.failure(IOException("offline")) else Result.success(Unit)
        }
        queue.enqueue(entry("a", 1L))

        queue.flush()

        assertEquals(2, attempts)
        assertEquals(listOf(30_000L), pauses)
        assertTrue(queue.pendingEntries().isEmpty())
    }

    @Test
    fun badRequestDropsHeadAndContinues() = runBlocking {
        val sent = mutableListOf<String>()
        val queue = queue(file()) { entry ->
            sent += entry.videoId
            if (entry.videoId == "bad") {
                Result.failure(ListenBrainzHttpException(400))
            } else {
                Result.success(Unit)
            }
        }
        queue.enqueue(entry("bad", 1L))
        queue.enqueue(entry("good", 2L))

        queue.flush()

        assertEquals(listOf("bad", "good"), sent)
        assertTrue(queue.pendingEntries().isEmpty())
    }

    @Test
    fun duplicateVideoIdAndListenedAtIsQueuedOnce() = runBlocking {
        val queue = queue(file(), canFlush = { false }) { Result.success(Unit) }

        assertTrue(queue.enqueue(entry("a", 100L)))
        assertFalse(queue.enqueue(entry("a", 100L)))
        assertTrue(queue.enqueue(entry("a", 101L)))
        assertTrue(queue.enqueue(entry("b", 100L)))

        assertEquals(3, queue.pendingEntries().size)
    }

    @Test
    fun pendingEntriesSurviveAStoreRecreation() = runBlocking {
        val file = file()
        val firstProcess = queue(file, canFlush = { false }) { Result.success(Unit) }
        firstProcess.enqueue(entry("persisted", 1L))

        val sent = mutableListOf<String>()
        val afterRestart = queue(file) { entry ->
            sent += entry.videoId
            Result.success(Unit)
        }
        assertEquals(listOf("persisted"), afterRestart.pendingEntries().map { it.videoId })

        afterRestart.flush()

        assertEquals(listOf("persisted"), sent)
        assertTrue(afterRestart.pendingEntries().isEmpty())
    }

    @Test
    fun backoffDoublesFromThirtySecondsAndCapsAtThirtyMinutes() = runBlocking {
        val pauses = mutableListOf<Long>()
        var attempts = 0
        val queue = queue(file(), pauses = pauses) {
            attempts++
            if (attempts <= 8) Result.failure(ListenBrainzHttpException(503)) else Result.success(Unit)
        }
        queue.enqueue(entry("a", 1L))

        queue.flush()

        assertEquals(
            listOf(30_000L, 60_000L, 120_000L, 240_000L, 480_000L, 960_000L, 1_800_000L, 1_800_000L),
            pauses,
        )
        assertEquals(9, attempts)
        assertTrue(queue.pendingEntries().isEmpty())
        assertEquals(1_800_000L, DurableListenBrainzScrobbleQueue.backoffMs(50))
    }

    private fun file(name: String = "queue.json") = File(temporaryFolder.root, name)

    private fun queue(
        file: File,
        canFlush: () -> Boolean = { true },
        pauses: MutableList<Long> = mutableListOf(),
        send: suspend (ListenBrainzListenEntry) -> Result<Unit>,
    ) = DurableListenBrainzScrobbleQueue(
        file = file,
        canFlush = canFlush,
        send = send,
        log = { _, _ -> },
        pause = { waitMs -> pauses += waitMs },
    )

    private fun entry(videoId: String, listenedAt: Long) = ListenBrainzListenEntry(
        videoId = videoId,
        artist = "Artist",
        track = "Track $videoId",
        listenedAt = listenedAt,
        startMs = listenedAt * 1_000L,
        endMs = listenedAt * 1_000L + 180_000L,
    )
}
