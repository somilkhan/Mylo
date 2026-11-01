package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** Where a track is between "not on this computer" and "on it". */
sealed interface DesktopDownloadState {

    /** Accepted, waiting for the one in front of it. */
    data object Queued : DesktopDownloadState

    /** [fraction] is 0f until the length is known, which is the first thing asked for. */
    data class Running(val fraction: Float) : DesktopDownloadState

    data class Failed(val reason: String) : DesktopDownloadState
}

/** One row of the downloads manager. */
data class DesktopDownloadItem(
    val song: Song,
    val state: DesktopDownloadState,
    /** The order it was asked for in, which is the order the queue reaches it in. */
    val sequence: Long,
)

/**
 * The download queue, and what came out of it.
 *
 * Android's `Downloads` split, kept: [active] is what is happening now and lives only in memory,
 * because a transfer interrupted by the process dying did not happen; what exists on disk is the
 * files themselves, which the library scan finds on its own.
 *
 * [WORKERS] run at once, each pulling from the same queue, so one slow track does not hold up the
 * rest and a release asked for as a whole arrives in the order it was asked for.
 */
object DesktopDownloadQueue {

    /** How many transfers run at once. Android's `DownloadService` uses the same number. */
    internal const val WORKERS = 4

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val sequence = AtomicLong(0)

    /** Asked for, not yet claimed by a worker. Insertion-ordered, which is queue order. */
    private val pending = LinkedHashMap<String, DesktopDownloadItem>()

    /** Claimed by a worker, with its generation so an old worker cannot mutate a retry. */
    private data class RunningDownload(val sequence: Long, val job: Job)
    private val running = LinkedHashMap<String, RunningDownload>()

    private var workers = 0

    private val _active = MutableStateFlow<Map<String, DesktopDownloadItem>>(emptyMap())

    /** Everything queued, running or just failed, keyed by videoId. */
    val active: StateFlow<Map<String, DesktopDownloadItem>> = _active

    private val _finished = MutableSharedFlow<Song>(extraBufferCapacity = 64)

    /** Tracks that reached disk, for the library to record. */
    val finished: SharedFlow<Song> = _finished

    /**
     * Adds [song] to the queue, unless it is already in it.
     *
     * A second tap on a track already queued or running is a no-op rather than a second transfer:
     * the row it would produce is the same row.
     */
    fun enqueue(song: Song) {
        val id = song.videoId
        if (id.isBlank()) return
        synchronized(lock) {
            if (id in pending || id in running) return
            val item = DesktopDownloadItem(song, DesktopDownloadState.Queued, sequence.incrementAndGet())
            pending[id] = item
            _active.update { it + (id to item) }
        }
        ensureWorkers()
    }

    fun enqueueAll(songs: List<Song>) = songs.forEach(::enqueue)

    /** Remove a queued/running row and cancel only the worker that owned that row. */
    fun cancel(videoId: String) {
        val job = synchronized(lock) {
            pending.remove(videoId)
            val worker = running.remove(videoId)
            _active.update { it - videoId }
            worker?.job
        }
        job?.cancel()
    }

    /** Atomically remove the current queue; later enqueues are new work, not part of this cancel. */
    fun cancelAll() {
        val jobs = synchronized(lock) {
            pending.clear()
            val currentJobs = running.values.map(RunningDownload::job)
            running.clear()
            _active.value = emptyMap()
            currentJobs
        }
        jobs.forEach { it.cancel() }
    }

    /** Drops the finished and failed rows, leaving whatever is still going. */
    fun clearFinished() {
        _active.update { current ->
            current.filterValues { it.state !is DesktopDownloadState.Failed }
        }
    }

    /** Puts a failed track back at the end of the queue. */
    fun retry(videoId: String) {
        val item = _active.value[videoId] ?: return
        if (item.state !is DesktopDownloadState.Failed) return
        enqueue(item.song)
    }

    private fun ensureWorkers() {
        val toStart = synchronized(lock) {
            val wanted = minOf(WORKERS, pending.size + running.size)
            val starting = (wanted - workers).coerceAtLeast(0)
            workers += starting
            starting
        }
        repeat(toStart) { scope.launch { work() } }
    }

    private suspend fun work() {
        try {
            while (true) {
                val item = takeNext(currentJob() ?: error("Download worker has no coroutine Job")) ?: return
                val id = item.song.videoId
                val result: Result<Result<Song>> = try {
                    Result.success(
                        DesktopDownloadManager.download(item.song) { downloaded, total ->
                            val fraction = if (total != null && total > 0L) {
                                (downloaded.toDouble() / total).toFloat().coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            _active.update { current ->
                                // A cancelled/retried row has a different sequence even when its ID matches.
                                if (current[id]?.sequence != item.sequence) current
                                else current + (id to item.copy(state = DesktopDownloadState.Running(fraction)))
                            }
                        },
                    )
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    Result.failure(failure)
                }
                onIdle(id, item.sequence)
                result.fold(
                    onSuccess = { outcome ->
                        outcome.fold(
                            onSuccess = { saved ->
                                val stillOwned = synchronized(lock) {
                                    var removed = false
                                    _active.update { current ->
                                        if (current[id]?.sequence != item.sequence) current
                                        else {
                                            removed = true
                                            current - id
                                        }
                                    }
                                    removed
                                }
                                if (stillOwned) _finished.tryEmit(saved)
                            },
                            onFailure = { failed(id, item, it) },
                        )
                    },
                    onFailure = { failed(id, item, it) },
                )
            }
        } finally {
            synchronized(lock) { workers-- }
            // An enqueue can race the last worker's exit after it observed an empty queue.
            // Recheck after decrementing so pending work cannot be stranded with workers == 0.
            ensureWorkers()
        }
    }

    private fun failed(id: String, item: DesktopDownloadItem, cause: Throwable) {
        if (cause is kotlinx.coroutines.CancellationException) return
        val reason = cause.message?.takeIf(String::isNotBlank) ?: "The download did not finish"
        val recorded = synchronized(lock) {
            var updated = false
            _active.update { current ->
                if (current[id]?.sequence != item.sequence) current
                else {
                    updated = true
                    current + (id to item.copy(state = DesktopDownloadState.Failed(reason)))
                }
            }
            updated
        }
        if (recorded) DesktopTrackLog.log("download failed for '${item.song.title}' — $reason")
    }

    /**
     * Atomically claims the next item, attaches its worker and publishes Running.
     * The sequence protects a later retry from callbacks belonging to an older attempt.
     */
    private fun takeNext(job: Job): DesktopDownloadItem? = synchronized(lock) {
        val entry = pending.entries.firstOrNull() ?: return null
        pending.remove(entry.key)
        running[entry.key] = RunningDownload(entry.value.sequence, job)
        _active.update { current ->
            if (current[entry.key]?.sequence != entry.value.sequence) current
            else current + (entry.key to entry.value.copy(state = DesktopDownloadState.Running(0f)))
        }
        entry.value
    }

    private fun onIdle(videoId: String, sequence: Long) {
        synchronized(lock) {
            if (running[videoId]?.sequence == sequence) running.remove(videoId)
        }
    }

    private suspend fun currentJob(): Job? = kotlin.coroutines.coroutineContext[Job]
}
