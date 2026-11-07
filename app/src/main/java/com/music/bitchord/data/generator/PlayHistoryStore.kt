package com.music.bitchord.data.generator

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.ZoneId

/**
 * Local file of plays, one per line, tab separated. Exact Mylo events retain
 * distinct timestamps; overlapping imported rows are reconciled on write so
 * importing the same history twice adds nothing.
 *
 * The file is plain text on purpose: it can be inspected and deleted without
 * a schema. Android backup and device-transfer rules explicitly exclude it.
 * [forget] removes everything, which is the Settings button.
 */
class PlayHistoryStore(private val file: File, private val zone: ZoneId = ZoneId.systemDefault()) {

    /** Every stored play, oldest first. Lines that do not parse are skipped. */
    @Synchronized
    fun readAll(): List<Play> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull(::decode)
    }

    /** Adds [incoming], preserving distinct Mylo events and reconciling imported overlaps. */
    @Synchronized
    fun append(incoming: List<Play>): Int = appendWithResult(incoming).addedCount

    /**
     * Reports content replacement as a change even when the number of rows stays constant.
     * For example, an exact Mylo listen can replace an approximate imported row for the same
     * track and day; callers must refresh readers even though no additional row was added.
     */
    @Synchronized
    internal fun appendWithResult(incoming: List<Play>): PlayHistoryAppendResult {
        val before = readAll()
        val normalizedBefore = dedupePlays(before, zone)
        val after = dedupePlays(before + incoming, zone)
        val result = PlayHistoryAppendResult(
            addedCount = (after.size - normalizedBefore.size).coerceAtLeast(0),
            changed = after != before,
        )
        if (result.changed) writeAtomically(after)
        return result
    }

    /** Removes everything, including Mylo's own playback history. */
    @Synchronized
    fun forget() {
        if (file.exists()) file.delete()
    }

    /** Removes imported rows while preserving plays recorded by Mylo itself. */
    @Synchronized
    fun forgetImported() {
        if (!file.exists()) return
        val retained = readAll().filter { it.source == PlaySource.MYLO }
        if (retained.isEmpty()) {
            file.delete()
        } else {
            writeAtomically(retained)
        }
    }

    /** Replace the history file only after the complete new contents are written to a sibling file. */
    private fun writeAtomically(plays: List<Play>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.absolutePath + ".tmp")
        val contents = plays.joinToString("\n", postfix = "\n") { encode(it) }
        try {
            temporary.writeText(contents)
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                if (!temporary.renameTo(file)) {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } catch (error: Exception) {
            throw IOException("Could not safely replace Scrobble history", error)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun encode(p: Play): String =
        listOf(
            p.videoId, p.title, p.artist, p.source.name, p.atMillis.toString(), p.exact.toString(),
            p.localUri.orEmpty(), p.thumbnailUrl.orEmpty(), p.localPath.orEmpty(),
            p.durationText.orEmpty(), p.artistId.orEmpty(), p.albumId.orEmpty(), p.albumName.orEmpty(),
            p.isVideo.toString(), p.isVideoOrigin.toString(),
        ).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ') }

    private fun decode(line: String): Play? {
        val parts = line.split('\t')
        if (parts.size !in 6..15) return null
        val source = runCatching { PlaySource.valueOf(parts[3]) }.getOrNull() ?: return null
        val at = parts[4].toLongOrNull() ?: return null
        return Play(
            videoId = parts[0],
            title = parts[1],
            artist = parts[2],
            source = source,
            atMillis = at,
            exact = parts[5] == "true",
            localUri = parts.getOrNull(6)?.takeIf(String::isNotBlank),
            thumbnailUrl = parts.getOrNull(7)?.takeIf(String::isNotBlank),
            localPath = parts.getOrNull(8)?.takeIf(String::isNotBlank),
            durationText = parts.getOrNull(9)?.takeIf(String::isNotBlank),
            artistId = parts.getOrNull(10)?.takeIf(String::isNotBlank),
            albumId = parts.getOrNull(11)?.takeIf(String::isNotBlank),
            albumName = parts.getOrNull(12)?.takeIf(String::isNotBlank),
            isVideo = parts.getOrNull(13)?.toBooleanStrictOrNull() ?: false,
            isVideoOrigin = parts.getOrNull(14)?.toBooleanStrictOrNull()
                ?: (parts.getOrNull(13)?.toBooleanStrictOrNull() ?: false),
        )
    }
}


internal data class PlayHistoryAppendResult(
    val addedCount: Int,
    val changed: Boolean,
)

/**
 * Process-wide owner for Mylo's private listening-history file. Playback writes
 * through this shared instance on an IO dispatcher; UI readers use the same
 * store so they see the same persisted history.
 */
object ScrobbleHistory {
    private val _revision = MutableStateFlow(0L)

    /** Changes whenever a listen is appended or history is explicitly cleared. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    @Volatile
    private var store: PlayHistoryStore? = null

    @Synchronized
    fun init(context: Context) {
        if (store == null) {
            store = PlayHistoryStore(
                File(context.applicationContext.filesDir, "scrobble_history.tsv"),
            )
        }
    }

    fun append(incoming: List<Play>): Int {
        val result = requireStore().appendWithResult(incoming)
        if (result.changed) _revision.update { it + 1L }
        return result.addedCount
    }

    fun readAll(): List<Play> = requireStore().readAll()

    fun forget() {
        requireStore().forget()
        _revision.update { it + 1L }
    }

    fun forgetImported() {
        requireStore().forgetImported()
        _revision.update { it + 1L }
    }

    private fun requireStore(): PlayHistoryStore =
        checkNotNull(store) { "ScrobbleHistory must be initialized by the application first" }
}
