package com.music.bitchord.data.scrobbling

import android.content.Context
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** One finished listen, stored with everything needed to rebuild its submit body. */
@Serializable
internal data class ListenBrainzListenEntry(
    val videoId: String,
    val artist: String,
    val track: String,
    val listenedAt: Long,
    val startMs: Long,
    val endMs: Long,
    val album: String? = null,
    val durationMs: Long = 0L,
    val id: String = UUID.randomUUID().toString(),
)

/** HTTP status from ListenBrainz, with the server's Retry-After delay when it sent one. */
internal class ListenBrainzHttpException(
    val code: Int,
    val retryAfterMs: Long? = null,
) : Exception("ListenBrainz responded with HTTP $code")

/**
 * One process-wide owner for the durable ListenBrainz FIFO of finished listens.
 * Playing-now updates are not queued; they stay direct in [ListenBrainzManager].
 */
internal object ListenBrainzScrobbleQueue {
    private const val TAG = "ListenBrainzScrobbleQueue"
    private const val FILE_NAME = "listenbrainz-scrobble-queue.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var queue: DurableListenBrainzScrobbleQueue? = null

    @Synchronized
    fun init(context: Context) {
        if (queue != null) return

        val activeQueue = DurableListenBrainzScrobbleQueue(
            file = File(context.applicationContext.filesDir, FILE_NAME),
            canFlush = ::canFlush,
            send = { entry ->
                ListenBrainzManager.postFinished(AppSettings.listenBrainzToken.value, entry)
            },
            log = { message, error ->
                if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
            },
        )
        queue = activeQueue

        // Any connection or settings change clears an auth pause and retries once,
        // so a corrected token is picked up without an app restart.
        scope.launch {
            combine(
                AppSettings.meteredConnection,
                AppSettings.listenBrainzEnabled,
                AppSettings.listenBrainzToken,
            ) { metered, enabled, token ->
                QueueConnectionSnapshot(
                    networkAvailable = metered != null,
                    enabled = enabled,
                    tokenPresent = token.isNotBlank(),
                )
            }.distinctUntilChanged().collectLatest { snapshot ->
                activeQueue.resumeAfterSettingsChange()
                if (!snapshot.canFlush) return@collectLatest
                try {
                    activeQueue.flush()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Log.e(TAG, "Could not flush the durable ListenBrainz queue", error)
                }
            }
        }
    }

    /**
     * Persists a finished listen (skipping duplicates) and starts a flush.
     * Returns true when the entry was newly queued.
     */
    suspend fun submit(entry: ListenBrainzListenEntry): Boolean {
        val activeQueue = queue
        if (activeQueue == null) {
            Log.w(TAG, "ListenBrainz listen arrived before the durable queue was initialized")
            return false
        }
        val added = try {
            activeQueue.enqueue(entry)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "Could not persist a ListenBrainz listen", error)
            return false
        }
        // Outside the caller's scope so service teardown cannot drop the flush.
        scope.launch {
            try {
                activeQueue.flush()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Could not flush the ListenBrainz queue", error)
            }
        }
        return added
    }

    private fun canFlush(): Boolean =
        AppSettings.scrobblingAvailable &&
            AppSettings.meteredConnection.value != null &&
            AppSettings.listenBrainzEnabled.value &&
            AppSettings.listenBrainzToken.value.isNotBlank()

    private data class QueueConnectionSnapshot(
        val networkAvailable: Boolean,
        val enabled: Boolean,
        val tokenPresent: Boolean,
    ) {
        val canFlush: Boolean
            get() = networkAvailable && enabled && tokenPresent
    }
}

/**
 * File-backed FIFO separated from Android and HTTP so its rules are unit-testable.
 *
 * Failure handling:
 * - 401 / 403: pause. Entries are kept until [resumeAfterSettingsChange].
 * - 429, 408, 5xx, and any non-HTTP failure (IOException): retry the same head
 *   with exponential backoff (30 s doubling, capped at 30 min). A 429 Retry-After
 *   replaces the computed delay.
 * - Any other 4xx, including 400: permanent, drop the head and log it.
 */
internal class DurableListenBrainzScrobbleQueue(
    private val file: File,
    private val canFlush: () -> Boolean,
    private val send: suspend (ListenBrainzListenEntry) -> Result<Unit>,
    private val log: (String, Throwable?) -> Unit,
    private val pause: suspend (Long) -> Unit = { waitMs -> delay(waitMs) },
) {
    private val storageMutex = Mutex()
    private val flushMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var authPaused = false

    // Guarded by flushMutex.
    private var consecutiveFailures = 0

    /** Returns false when an entry with the same videoId and listenedAt is already pending. */
    suspend fun enqueue(entry: ListenBrainzListenEntry): Boolean = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            val entries = readUnsafe()
            if (entries.any { it.videoId == entry.videoId && it.listenedAt == entry.listenedAt }) {
                log("Skipped duplicate ListenBrainz listen videoId=${entry.videoId} at=${entry.listenedAt}", null)
                return@withLock false
            }
            while (entries.size >= MAX_ENTRIES) {
                val dropped = entries.removeAt(0)
                log("Queue reached $MAX_ENTRIES entries; dropped oldest pending listen id=${dropped.id}", null)
            }
            entries += entry
            writeUnsafe(entries)
            true
        }
    }

    /** Clears an auth pause so the next flush sends again (new token or settings). */
    fun resumeAfterSettingsChange() {
        authPaused = false
    }

    suspend fun flush() {
        flushMutex.withLock {
            while (canFlush() && !authPaused) {
                val entry = readHead() ?: break
                val result = try {
                    send(entry)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Result.failure(error)
                }
                val failure = result.exceptionOrNull()
                if (failure == null) {
                    consecutiveFailures = 0
                    removeHead(entry.id)
                    continue
                }
                if (failure is CancellationException) throw failure
                val httpCode = (failure as? ListenBrainzHttpException)?.code
                when {
                    httpCode == 401 || httpCode == 403 -> {
                        authPaused = true
                        log("ListenBrainz rejected the token (HTTP $httpCode); queue paused, entries kept", failure)
                        break
                    }
                    httpCode == null || httpCode == 408 || httpCode == 429 || httpCode in 500..599 -> {
                        consecutiveFailures++
                        val waitMs = (failure as? ListenBrainzHttpException)?.retryAfterMs
                            ?.coerceIn(0L, MAX_BACKOFF_MS)
                            ?: backoffMs(consecutiveFailures)
                        log("Retrying ListenBrainz listen id=${entry.id} in ${waitMs}ms: ${failure.message}", failure)
                        pause(waitMs)
                    }
                    else -> {
                        removeHead(entry.id)
                        log("Dropped permanent ListenBrainz failure (HTTP $httpCode) id=${entry.id}: ${failure.message}", failure)
                    }
                }
            }
        }
    }

    internal suspend fun pendingEntries(): List<ListenBrainzListenEntry> = withContext(Dispatchers.IO) {
        storageMutex.withLock { readUnsafe().toList() }
    }

    private suspend fun readHead(): ListenBrainzListenEntry? = withContext(Dispatchers.IO) {
        storageMutex.withLock { readUnsafe().firstOrNull() }
    }

    private suspend fun removeHead(expectedId: String) = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            val entries = readUnsafe()
            if (entries.firstOrNull()?.id == expectedId) {
                entries.removeAt(0)
                writeUnsafe(entries)
            }
        }
    }

    private fun readUnsafe(): MutableList<ListenBrainzListenEntry> {
        if (!file.exists()) return mutableListOf()
        return try {
            json.decodeFromString<List<ListenBrainzListenEntry>>(file.readText()).toMutableList()
        } catch (error: SerializationException) {
            log("Discarded unreadable ListenBrainz queue JSON", error)
            mutableListOf()
        }
    }

    private fun writeUnsafe(entries: List<ListenBrainzListenEntry>) {
        val parent = file.parentFile ?: error("ListenBrainz queue has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) error("Could not create ListenBrainz queue directory")
        val temporary = File(parent, "${file.name}.tmp")
        val bytes = json.encodeToString(entries).toByteArray(Charsets.UTF_8)
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        Files.move(
            temporary.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    internal companion object {
        const val MAX_ENTRIES = 200
        const val BASE_BACKOFF_MS = 30_000L
        const val MAX_BACKOFF_MS = 1_800_000L

        /** 30 s for the first failure, doubling each time, capped at 30 min. */
        fun backoffMs(consecutiveFailures: Int): Long {
            val exponent = (consecutiveFailures - 1).coerceIn(0, 16)
            return minOf(BASE_BACKOFF_MS * (1L shl exponent), MAX_BACKOFF_MS)
        }
    }
}
