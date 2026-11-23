package com.music.bitchord.data.scrobbling

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LastFmScrobbleQueueTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun flushesPendingEntriesInFifoOrder() = runBlocking {
        val sent = mutableListOf<String>()
        val queue = queue(File(temporaryFolder.root, "queue.json")) { entry ->
            sent += entry.track
            Result.success(Unit)
        }
        queue.enqueue(entry("first"))
        queue.enqueue(entry("second"))
        queue.enqueue(entry("third"))

        queue.flush()

        assertEquals(listOf("first", "second", "third"), sent)
        assertTrue(queue.pendingEntries().isEmpty())
    }

    @Test
    fun dropsOldestWhenCapacityIsExceeded() = runBlocking {
        val logs = mutableListOf<String>()
        val queue = DurableLastFmScrobbleQueue(
            file = File(temporaryFolder.root, "queue.json"),
            canFlush = { false },
            send = { Result.success(Unit) },
            log = { message, _ -> logs += message },
        )
        repeat(DurableLastFmScrobbleQueue.MAX_ENTRIES + 1) { index ->
            queue.enqueue(entry("track-$index"))
        }

        val pending = queue.pendingEntries()
        assertEquals(DurableLastFmScrobbleQueue.MAX_ENTRIES, pending.size)
        assertEquals("track-1", pending.first().track)
        assertEquals("track-200", pending.last().track)
        assertEquals(1, logs.size)
        assertTrue(logs.single().contains("dropped oldest"))
    }

    @Test
    fun pendingEntriesSurviveAStoreRecreation() = runBlocking {
        val file = File(temporaryFolder.root, "queue.json")
        val firstProcess = queue(file) { Result.success(Unit) }
        firstProcess.enqueue(entry("persisted"))

        val delivered = mutableListOf<String>()
        val restartedProcess = queue(file) { pending ->
            delivered += pending.track
            Result.success(Unit)
        }
        restartedProcess.flush()

        assertEquals(listOf("persisted"), delivered)
        assertTrue(restartedProcess.pendingEntries().isEmpty())
    }

    @Test
    fun retryableFailureKeepsHeadAndStopsLaterEntries() = runBlocking {
        val file = File(temporaryFolder.root, "queue.json")
        val attempted = mutableListOf<String>()
        val firstProcess = queue(file) { pending ->
            attempted += pending.track
            Result.failure(IOException("offline"))
        }
        firstProcess.enqueue(entry("first"))
        firstProcess.enqueue(entry("second"))

        firstProcess.flush()

        assertEquals(listOf("first"), attempted)
        assertEquals(listOf("first", "second"), firstProcess.pendingEntries().map { it.track })

        val replayed = mutableListOf<String>()
        val restartedProcess = queue(file) { pending ->
            replayed += pending.track
            Result.success(Unit)
        }
        restartedProcess.flush()

        assertEquals(listOf("first", "second"), replayed)
        assertTrue(restartedProcess.pendingEntries().isEmpty())
    }

    @Test
    fun sessionFailurePausesQueueAndKeepsEveryEntry() = runBlocking {
        val attempted = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val queue = DurableLastFmScrobbleQueue(
            file = File(temporaryFolder.root, "queue.json"),
            canFlush = { true },
            send = { pending ->
                attempted += pending.track
                Result.failure(LastFM.LastFmException(9, "Invalid session key"))
            },
            log = { message, _ -> logs += message },
        )
        queue.enqueue(entry("first"))
        queue.enqueue(entry("second"))

        queue.flush()

        assertEquals(listOf("first"), attempted)
        assertEquals(listOf("first", "second"), queue.pendingEntries().map { it.track })
        assertTrue(logs.any { it.contains("Paused Last.fm queue") })
    }

    @Test
    fun httpForbiddenAndUnauthorizedAreAuthBlockedNotDropped() = runBlocking {
        for (status in listOf(401, 403)) {
            val file = File(temporaryFolder.root, "queue-$status.json")
            val queue = queue(file) { Result.failure(LastFM.LastFmException(status, "Forbidden")) }
            queue.enqueue(entry("kept"))

            queue.flush()

            assertEquals("status $status", listOf("kept"), queue.pendingEntries().map { it.track })
        }
    }

    @Test
    fun retryableFailureBacksOffAndResetsAfterSuccess() = runBlocking {
        var now = 0L
        val scheduled = mutableListOf<Long>()
        var fail = true
        val sent = mutableListOf<String>()
        val queue = DurableLastFmScrobbleQueue(
            file = File(temporaryFolder.root, "queue.json"),
            canFlush = { true },
            send = { pending ->
                if (fail) Result.failure(LastFM.LastFmException(11, "Service offline"))
                else { sent += pending.track; Result.success(Unit) }
            },
            log = { _, _ -> },
            clock = { now },
            scheduleRetry = { scheduled += it },
        )
        queue.enqueue(entry("only"))

        queue.flush()
        assertEquals(listOf(30_000L), scheduled)

        // Still inside the backoff window: nothing is sent.
        now = 10_000L
        fail = false
        queue.flush()
        assertTrue(sent.isEmpty())

        now = 30_000L
        queue.flush()
        assertEquals(listOf("only"), sent)
        assertTrue(queue.pendingEntries().isEmpty())
    }

    @Test
    fun backoffDoublesAndIsCapped() {
        assertEquals(30_000L, DurableLastFmScrobbleQueue.retryBackoffMillis(1))
        assertEquals(60_000L, DurableLastFmScrobbleQueue.retryBackoffMillis(2))
        assertEquals(120_000L, DurableLastFmScrobbleQueue.retryBackoffMillis(3))
        assertEquals(DurableLastFmScrobbleQueue.MAX_BACKOFF_MILLIS, DurableLastFmScrobbleQueue.retryBackoffMillis(30))
    }

    @Test
    fun rejectedTrackIsPermanentButDailyLimitIsRetryable() = runBlocking {
        val queue = queue(File(temporaryFolder.root, "queue.json")) { pending ->
            if (pending.track == "rejected") {
                Result.failure(LastFM.ScrobbleRejectedException(2, false, "Track ignored"))
            } else {
                Result.failure(LastFM.ScrobbleRejectedException(5, true, "Daily limit"))
            }
        }
        queue.enqueue(entry("rejected"))
        queue.enqueue(entry("daily-limit"))

        queue.flush()

        assertEquals(listOf("daily-limit"), queue.pendingEntries().map { it.track })
    }

    @Test
    fun cancellationIsRethrownAndPendingEntryRemains() = runBlocking {
        val queue = queue(File(temporaryFolder.root, "queue.json")) {
            throw CancellationException("cancelled")
        }
        queue.enqueue(entry("keep"))

        try {
            queue.flush()
            throw AssertionError("Expected cancellation")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }

        assertEquals(listOf("keep"), queue.pendingEntries().map { it.track })
    }

    private fun queue(
        file: File,
        send: suspend (LastFmScrobbleEntry) -> Result<Unit>,
    ) = DurableLastFmScrobbleQueue(
        file = file,
        canFlush = { true },
        send = send,
        log = { _, _ -> },
    )

    private fun entry(track: String) = LastFmScrobbleEntry(
        artist = "Artist",
        track = track,
        timestamp = 1_700_000_000L,
    )
}
