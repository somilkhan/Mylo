package com.music.bitchord.data.generator

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
    fun append(incoming: List<Play>): Int {
        val before = readAll()
        val after = dedupePlays(before + incoming, zone)
        val added = after.size - dedupePlays(before, zone).size
        writeAtomically(after)
        return added
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
            p.videoId, p.title, p.artist, p.source.name, p.atMillis.toString(), p.exact.toString(), p.localUri.orEmpty(), p.thumbnailUrl.orEmpty(),
        ).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ') }

    private fun decode(line: String): Play? {
        val parts = line.split('\t')
        if (parts.size !in 6..8) return null
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
        )
    }
}


/**
 * Process-wide owner for Mylo's private listening-history file. Playback writes
 * through this shared instance on an IO dispatcher; UI readers use the same
 * store so they see the same persisted history.
 */
object ScrobbleHistory {
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

    fun append(incoming: List<Play>): Int = requireStore().append(incoming)

    fun readAll(): List<Play> = requireStore().readAll()

    fun forget() = requireStore().forget()

    fun forgetImported() = requireStore().forgetImported()

    private fun requireStore(): PlayHistoryStore =
        checkNotNull(store) { "ScrobbleHistory must be initialized by the application first" }
}
