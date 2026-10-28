package com.music.bitchord.data.generator

import java.io.File
import java.time.ZoneId

/**
 * Append-only local file of plays, one per line, tab separated. Plays are
 * deduplicated on write, so importing the same history twice adds nothing.
 *
 * The file is plain text on purpose: it can be read, backed up and deleted
 * without a schema. [forget] removes everything, which is the Settings button.
 */
class PlayHistoryStore(private val file: File, private val zone: ZoneId = ZoneId.systemDefault()) {

    /** Every stored play, oldest first. Lines that do not parse are skipped. */
    fun readAll(): List<Play> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull(::decode)
    }

    /** Adds [incoming], keeping one play per song per day. Returns how many were new. */
    @Synchronized
    fun append(incoming: List<Play>): Int {
        val before = readAll()
        val after = dedupePlays(before + incoming, zone)
        val added = after.size - dedupePlays(before, zone).size
        file.parentFile?.mkdirs()
        file.writeText(after.joinToString("\n", postfix = "\n") { encode(it) })
        return added
    }

    /** Removes everything. */
    @Synchronized
    fun forget() {
        if (file.exists()) file.delete()
    }

    private fun encode(p: Play): String =
        listOf(
            p.videoId, p.title, p.artist, p.source.name, p.atMillis.toString(), p.exact.toString(),
        ).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

    private fun decode(line: String): Play? {
        val parts = line.split('\t')
        if (parts.size != 6) return null
        val source = runCatching { PlaySource.valueOf(parts[3]) }.getOrNull() ?: return null
        val at = parts[4].toLongOrNull() ?: return null
        return Play(parts[0], parts[1], parts[2], source, at, parts[5] == "true")
    }
}
