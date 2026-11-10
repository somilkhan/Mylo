package com.music.bitchord.data.sync

import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

/**
 * Identifies the one sync job allowed to publish shared loading state.
 * Starting or invalidating a job revokes ownership from every older job.
 */
class SyncJobGeneration {
    private val generation = AtomicLong(0L)

    fun begin(): Long = generation.incrementAndGet()

    fun invalidate() {
        generation.incrementAndGet()
    }

    fun owns(token: Long): Boolean = generation.get() == token
}

/** Never interpret an empty or partial remote response as an instruction to delete local data. */
fun mayRemoveMissing(complete: Boolean, remoteCount: Int): Boolean =
    complete && remoteCount > 0

/** A remote playlist may replace a populated local one only after a complete, non-empty read. */
fun mayReplacePlaylist(complete: Boolean, remoteCount: Int, hasLocalSongs: Boolean): Boolean =
    complete && (remoteCount > 0 || !hasLocalSongs)

/** Keep the last known-good category when a refresh unexpectedly returns no rows. */
fun <T> preserveNonEmptyOnEmptyRemote(local: List<T>, remote: List<T>): List<T> =
    if (remote.isEmpty() && local.isNotEmpty()) local else remote

/** Merge a complete remote category according to the Add-only / overwrite preference. */
fun <T, K> mergeRemoteRows(
    local: List<T>,
    remote: List<T>,
    overwriteRemote: Boolean,
    key: (T) -> K,
): List<T> {
    if (remote.isEmpty() && local.isNotEmpty()) return local
    if (overwriteRemote) return remote
    val remoteKeys = remote.mapTo(HashSet(), key)
    return remote + local.filter { key(it) !in remoteKeys }
}

/** Result shown by manual sync; a failed category must not be reported as a successful sync. */
enum class SyncResult {
    SYNCED, FAILED, BUSY, NOTHING;

    companion object {
        fun combine(results: Collection<SyncResult>): SyncResult = when {
            FAILED in results -> FAILED
            SYNCED in results -> SYNCED
            BUSY in results -> BUSY
            else -> NOTHING
        }
    }
}

/** Account-safe decisions for reconciling a local liked collection with YouTube Music. */
object LikedSync {
    data class LocalLike(val id: String, val likedDate: LocalDateTime?)

    fun idsToUnlike(
        localLikes: Collection<LocalLike>,
        remoteIds: Set<String>,
        complete: Boolean,
        snapshot: Set<String>?,
        addOnly: Boolean,
        now: LocalDateTime,
        graceDays: Long,
    ): Set<String> {
        if (addOnly || !complete || snapshot == null || remoteIds.isEmpty()) return emptySet()
        val graceStart = now.minusDays(graceDays)
        return localLikes.asSequence()
            .filter { it.id in snapshot && it.id !in remoteIds }
            .filterNot { it.likedDate?.isAfter(graceStart) == true }
            .map { it.id }
            .toSet()
    }

    fun readLooksComplete(readCount: Int, headerCount: Int?): Boolean {
        if (headerCount == null || headerCount <= 0) return true
        return readCount + maxOf(5, headerCount / 10) >= headerCount
    }

    fun parseSongCount(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val match = Regex("\\d(?:[\\d,.'\\u00A0\\u202F ]*\\d)?").find(text) ?: return null
        return match.value.filter(Char::isDigit).toIntOrNull()
    }

    /** DATASYNC_ID identifies the active YouTube identity more reliably than a delayed email. */
    fun accountKey(dataSyncId: String?, email: String?): String? {
        val id = dataSyncId?.takeIf { it.isNotBlank() && it != "null" }?.let {
            when {
                !it.contains("||") -> it
                it.endsWith("||") -> it.substringBefore("||")
                else -> it.substringAfter("||")
            }
        }?.takeIf(String::isNotBlank)
        if (id != null) return "dsid:$id"
        return email?.trim()?.lowercase()?.takeIf(String::isNotEmpty)?.let { "email:$it" }
    }
}

/** Last complete remote liked-id set, isolated by account and atomically replaced. */
class LikedSnapshotStore(private val directory: File) {
    private fun fileFor(accountKey: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(accountKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
        return File(directory, "liked-$hash.txt")
    }

    fun read(accountKey: String): Set<String>? {
        val file = fileFor(accountKey)
        if (!file.isFile) return null
        return runCatching {
            val lines = file.readLines()
            if (lines.firstOrNull() != accountKey) return null
            lines.drop(1).filter(String::isNotBlank).toSet()
        }.getOrNull()
    }

    fun write(accountKey: String, ids: Set<String>) {
        directory.mkdirs()
        val file = fileFor(accountKey)
        val temporary = File(directory, file.name + ".tmp")
        temporary.writeText(buildString {
            appendLine(accountKey)
            ids.forEach { appendLine(it) }
        })
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }
}
