package com.music.bitchord.data.generator

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Where a play came from. Mylo's own plays are exact. Imported ones may be approximate. */
enum class PlaySource { MYLO, YOUTUBE_MUSIC, LAST_FM, LISTENBRAINZ }

/**
 * One listen.
 *
 * [atMillis] is when it happened, as far as the source can tell. [exact] is
 * false when the source only gave a day or an approximate time. The UI labels
 * those as approximate.
 */
data class Play(
    val videoId: String,
    val title: String,
    val artist: String,
    val source: PlaySource,
    val atMillis: Long,
    val exact: Boolean,
    /** Present for local files; lets a generated queue reopen the same device track. */
    val localUri: String? = null,
    /** Artwork URL captured with the play so history-generated mixes retain their cover art. */
    val thumbnailUrl: String? = null,
) {
    /** Stable identity for a catalogue track or local file, with text as the last resort. */
    fun songKey(): String = when {
        !localUri.isNullOrBlank() -> "local:$localUri"
        videoId.isNotBlank() -> "id:$videoId"
        else -> "tx:${normaliseText(title)}|${normaliseText(artist)}"
    }

    fun localDay(zone: ZoneId): LocalDate = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
}

/** Lowercase, with runs of spaces collapsed. */
internal fun normaliseText(s: String): String =
    s.trim().lowercase().split(" ").filter { it.isNotEmpty() }.joinToString(" ")

/**
 * Keeps one play per song per local day. When two sources report the same
 * song on the same day, the exact Mylo play is kept, so a listen is never
 * counted twice.
 */
fun dedupePlays(plays: List<Play>, zone: ZoneId): List<Play> {
    val chosen = LinkedHashMap<String, Play>()
    for (play in plays) {
        val key = "${play.songKey()}@${play.localDay(zone)}"
        val existing = chosen[key]
        if (existing == null || rank(play) > rank(existing)) chosen[key] = play
    }
    return chosen.values.sortedBy { it.atMillis }
}

private fun rank(play: Play): Int = when {
    play.source == PlaySource.MYLO && play.exact -> 3
    play.exact -> 2
    else -> 1
}
