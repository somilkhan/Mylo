package com.music.bitchord.ui.scrobble

import com.music.bitchord.data.scrobbling.LastFM
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    var current: LocalDate? = null
    tracks.forEachIndexed { index, track ->
        val day = if (track.nowPlaying || track.timestamp <= 0L) {
            today
        } else {
            Instant.ofEpochSecond(track.timestamp).atZone(zone).toLocalDate()
        }
        if (day != current) {
            entries += RecentEntry.Day(day)
            current = day
        }
        entries += RecentEntry.Scrobble(track, index)
    }
    return entries
}
