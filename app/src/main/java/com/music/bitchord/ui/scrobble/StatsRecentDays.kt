package com.music.bitchord.ui.scrobble

import com.music.bitchord.data.scrobbling.LastFM
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private data class RecentIdentity(
    val timestamp: Long,
    val name: String,
    val artist: String,
    val album: String?,
    val nowPlaying: Boolean,
)

/** Remove cross-page overlap, not legitimate duplicate occurrences within one Last.fm page. */
internal fun mergeRecentTracks(
    existing: List<LastFM.RecentTrack>,
    incoming: List<LastFM.RecentTrack>,
): List<LastFM.RecentTrack> {
    fun LastFM.RecentTrack.identity() = RecentIdentity(timestamp, name, artist, album, nowPlaying)
    val overlap = existing.groupingBy { it.identity() }.eachCount().toMutableMap()
    return existing + incoming.filter { track ->
        val identity = track.identity()
        val remaining = overlap[identity] ?: 0
        if (remaining > 0) {
            overlap[identity] = remaining - 1
            false
        } else {
            true
        }
    }
}

/** A line of the Recent list: a day's heading, or a scrobble under it. */
internal sealed interface RecentEntry {
    val key: String

    data class Day(val date: LocalDate) : RecentEntry {
        override val key: String get() = "day:$date"
    }

    data class Scrobble(val track: LastFM.RecentTrack, val index: Int) : RecentEntry {
        override val key: String get() = "scrobble:$index:${track.timestamp}:${track.name}"
    }
}

/**
 * The history as it is listed: each day it was played on as a heading, its
 * scrobbles under it in the order Last.fm gave them. A now-playing row has no
 * time of its own and goes under today.
 */
internal fun groupRecentByDay(
    tracks: List<LastFM.RecentTrack>,
    zone: ZoneId,
    today: LocalDate,
): List<RecentEntry> {
    val entries = ArrayList<RecentEntry>(tracks.size + 8)
    val days = linkedMapOf<LocalDate, MutableList<RecentEntry.Scrobble>>()
    tracks.forEachIndexed { index, track ->
        val day = if (track.nowPlaying || track.timestamp <= 0L) {
            today
        } else {
            Instant.ofEpochSecond(track.timestamp).atZone(zone).toLocalDate()
        }
        days.getOrPut(day) { mutableListOf() } += RecentEntry.Scrobble(track, index)
    }
    // Even a backfilled/out-of-order page gets only one lazy-list key per day.
    days.forEach { (day, scrobbles) ->
        entries += RecentEntry.Day(day)
        entries += scrobbles
    }
    return entries
}
