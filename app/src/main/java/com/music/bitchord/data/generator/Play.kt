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
 * Reconciles overlapping sources without erasing genuine Mylo repeat listens.
 * Exact Mylo events retain their distinct timestamps; imported-only overlaps
 * collapse to the most trustworthy single row per track and local day.
 */
fun dedupePlays(plays: List<Play>, zone: ZoneId): List<Play> {
    val byTrackAndDay = plays.groupBy { "${it.songKey()}@${it.localDay(zone)}" }
    return byTrackAndDay.values.flatMap { sameDay ->
        // Mylo records a qualifying event for every completed listen. Keep
        // distinct event timestamps; collapsing these to one row per day made
        // repeat listens invisible to Top Tracks and Rediscover.
        val myloEvents = sameDay
            .filter { it.source == PlaySource.MYLO && it.exact }
            .distinctBy { it.atMillis }
        if (myloEvents.isNotEmpty()) {
            myloEvents
        } else {
            // Imported sources may overlap. Keep the most trustworthy single
            // row for that track/day so the same listen is not counted twice.
            listOf(sameDay.maxBy { rank(it) })
        }
    }.sortedBy { it.atMillis }
}

private fun rank(play: Play): Int = when {
    play.source == PlaySource.MYLO && play.exact -> 3
    play.exact -> 2
    else -> 1
}
