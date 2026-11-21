package com.music.bitchord.data.scrobbling

import android.content.Context
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

@Serializable
internal data class LastFmScrobbleEntry(
    val artist: String,
    val track: String,
    val timestamp: Long,
    val album: String? = null,
    val trackNumber: Int? = null,
    val duration: Int? = null,
    val id: String = UUID.randomUUID().toString(),
)

/**
 * One process-wide owner for the durable Last.fm FIFO. The pending list is a
 * single JSON file; each replacement is written and synced to a sibling temp
 * file before an atomic same-filesystem rename.
 */
internal object LastFmScrobbleQueue {
    private const val TAG = "LastFmScrobbleQueue"
    private const val FILE_NAME = "lastfm-scrobble-queue.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var queue: DurableLastFmScrobbleQueue? = null

    @Synchronized
    fun init(context: Context) {
        if (queue != null) return

        val activeQueue = DurableLastFmScrobbleQueue(
            file = File(context.applicationContext.filesDir, FILE_NAME),
            canFlush = ::canFlush,
            send = { entry ->
                LastFM.scrobble(
                    artist = entry.artist,
                    track = entry.track,
                    timestamp = entry.timestamp,
                    album = entry.album,
                    trackNumber = entry.trackNumber,
                    duration = entry.duration,
                ).map { Unit }
            },
            log = { message, error ->
                if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
            },
        )
        queue = activeQueue

        // StateFlow emits its current value immediately, so this performs the
        // app-start flush and then reacts to network/account changes without polling.
        scope.launch {
            combine<Any?, QueueConnectionSnapshot>(
                AppSettings.meteredConnection,
                AppSettings.lastfmEnabled,
                AppSettings.lastfmScrobbleEnabled,
                AppSettings.lastfmSessionKey,
                AppSettings.lastfmApiKey,
                AppSettings.lastfmSecret,
                AppSettings.lastfmEndpoint,
            ) { values ->
                QueueConnectionSnapshot(
                    networkAvailable = values[0] != null,
                    lastfmEnabled = values[1] as Boolean,
                    scrobbleEnabled = values[2] as Boolean,
                    sessionKey = values[3] as String,
                    apiKey = values[4] as String,
                    secret = values[5] as String,
                    endpoint = values[6] as String,
                )
            }.distinctUntilChanged().collectLatest { snapshot ->
                if (!snapshot.canFlush) return@collectLatest
                try {
                    LastFM.configure(
                        endpoint = snapshot.endpoint.ifBlank { LastFM.DEFAULT_API_ENDPOINT },
                        apiKey = snapshot.apiKey,
                        secret = snapshot.secret,
                        sessionKey = snapshot.sessionKey,
                    )
                    activeQueue.flush()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Log.e(TAG, "Could not flush the durable Last.fm scrobble queue", error)
                }
            }
        }
    }

    /** Enqueue outside the playback-service scope so service teardown cannot lose a pending write. */
    fun submit(entry: LastFmScrobbleEntry) {
        val activeQueue = queue
        if (activeQueue == null) {
            Log.w(TAG, "Scrobble arrived before the durable queue was initialized")
            return
        }
        scope.launch {
            try {
                activeQueue.enqueue(entry)
                activeQueue.flush()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Could not persist or flush a Last.fm scrobble", error)
            }
        }
    }

    private fun canFlush(): Boolean =
        AppSettings.scrobblingAvailable &&
            AppSettings.meteredConnection.value != null &&
            AppSettings.lastfmEnabled.value &&
            AppSettings.lastfmScrobbleEnabled.value &&
            AppSettings.lastfmSessionKey.value.isNotBlank() &&
            AppSettings.lastfmApiKey.value.isNotBlank() &&
            AppSettings.lastfmSecret.value.isNotBlank() &&
            LastFM.isInitialized()

    private data class QueueConnectionSnapshot(
        val networkAvailable: Boolean,
        val lastfmEnabled: Boolean,
        val scrobbleEnabled: Boolean,
        val sessionKey: String,
        val apiKey: String,
        val secret: String,
        val endpoint: String,
    ) {
        val canFlush: Boolean
            get() = networkAvailable &&
                lastfmEnabled &&
                scrobbleEnabled &&
                sessionKey.isNotBlank() &&
                apiKey.isNotBlank() &&
                secret.isNotBlank()
    }
}

/** File-backed FIFO separated from Android and Last.fm so its rules are unit-testable. */
internal class DurableLastFmScrobbleQueue(
    private val file: File,
    private val canFlush: () -> Boolean,
    private val send: suspend (LastFmScrobbleEntry) -> Result<Unit>,
    private val log: (String, Throwable?) -> Unit,
) {
    private val storageMutex = Mutex()
    private val flushMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun enqueue(entry: LastFmScrobbleEntry) = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            val entries = readUnsafe()
            while (entries.size >= MAX_ENTRIES) {
                val dropped = entries.removeAt(0)
                log("Queue reached $MAX_ENTRIES entries; dropped oldest pending scrobble id=${dropped.id}", null)
            }
            entries += entry
            writeUnsafe(entries)
        }
    }

    suspend fun flush() {
        flushMutex.withLock {
            while (canFlush()) {
                val entry = readHead() ?: break
                val result = try {
                    send(entry)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Result.failure(error)
                }
                val failure = result.exceptionOrNull()
                when {
                    failure == null -> removeHead(entry.id)
                    failure is CancellationException -> throw failure
                    failure.isPermanentScrobbleFailure() -> {
                        removeHead(entry.id)
                        log("Dropped permanent Last.fm scrobble failure id=${entry.id}: ${failure.message}", failure)
                    }
                    else -> {
                        log("Retained retryable Last.fm scrobble failure at FIFO head id=${entry.id}", failure)
                        break
                    }
                }
            }
        }
    }

    internal suspend fun pendingEntries(): List<LastFmScrobbleEntry> = withContext(Dispatchers.IO) {
        storageMutex.withLock { readUnsafe().toList() }
    }

    private suspend fun readHead(): LastFmScrobbleEntry? = withContext(Dispatchers.IO) {
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

    private fun readUnsafe(): MutableList<LastFmScrobbleEntry> {
        if (!file.exists()) return mutableListOf()
        return try {
            json.decodeFromString<List<LastFmScrobbleEntry>>(file.readText()).toMutableList()
        } catch (error: SerializationException) {
            log("Discarded unreadable Last.fm scrobble queue JSON", error)
            mutableListOf()
        }
    }

    private fun writeUnsafe(entries: List<LastFmScrobbleEntry>) {
        val parent = file.parentFile ?: error("Scrobble queue has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) error("Could not create scrobble queue directory")
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

    private fun Throwable.isPermanentScrobbleFailure(): Boolean = when (this) {
        is LastFM.ScrobbleRejectedException -> !retryable
        is LastFM.LastFmException -> when (code) {
            8, 11, 16, 29, 408, 429 -> false
            in 500..599 -> false
            else -> true
        }
        else -> false
    }

    internal companion object {
        const val MAX_ENTRIES = 200
    }
}
