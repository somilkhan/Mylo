package com.music.bitchord.ui.screens

import kotlin.math.roundToInt

internal data class ListeningDayPart(
    val name: String,
    val timeRange: String,
    val listeningMs: Long,
) {
    fun percentageOf(totalListeningMs: Long): Int =
        if (totalListeningMs <= 0L) 0
        else ((listeningMs.toDouble() / totalListeningMs.toDouble()) * 100).roundToInt()
}

internal fun buildListeningDayParts(hourlyListeningMs: List<Long>): List<ListeningDayPart> {
    fun sumHours(startHour: Int, endExclusive: Int): Long =
        (startHour until endExclusive).sumOf { hour ->
            hourlyListeningMs.getOrNull(hour)?.coerceAtLeast(0L) ?: 0L
        }

    return listOf(
        ListeningDayPart("Night", "00–05", sumHours(0, 6)),
        ListeningDayPart("Morning", "06–11", sumHours(6, 12)),
        ListeningDayPart("Afternoon", "12–17", sumHours(12, 18)),
        ListeningDayPart("Evening", "18–23", sumHours(18, 24)),
    )
}


/** Aggregate rows returned by YouTube Music history sync. It has play rows,
 * but no trusted per-play duration or timestamp, so this model deliberately
 * does not invent listening-time or date-range values.
 */
internal data class SyncedHistoryTrack(
    val song: com.music.bitchord.data.model.Song,
    val plays: Int,
)

internal data class SyncedHistoryArtist(
    val name: String,
    val plays: Int,
)

internal data class SyncedHistorySummary(
    val totalPlays: Int,
    val distinctTracks: Int,
    val distinctArtists: Int,
    val distinctAlbums: Int,
    val topTracks: List<SyncedHistoryTrack>,
    val topArtists: List<SyncedHistoryArtist>,
)

/** Repeated rows are separate plays in YouTube Music history; keep their count. */
internal fun buildSyncedHistorySummary(
    history: List<com.music.bitchord.data.model.Song>,
): SyncedHistorySummary {
    val tracks = LinkedHashMap<String, Pair<com.music.bitchord.data.model.Song, Int>>()
    val artists = LinkedHashMap<String, Pair<String, Int>>()
    val albums = HashSet<String>()

    history.forEach { song ->
        val trackKey = song.videoId.takeIf(String::isNotBlank)
            ?: "${song.title.trim()}|${song.artist.trim()}".lowercase()
        val existingTrack = tracks[trackKey]
        tracks[trackKey] = (existingTrack?.first ?: song) to ((existingTrack?.second ?: 0) + 1)

        song.artist.trim().takeIf(String::isNotBlank)?.let { artist ->
            val artistKey = artist.lowercase()
            val existingArtist = artists[artistKey]
            artists[artistKey] = (existingArtist?.first ?: artist) to ((existingArtist?.second ?: 0) + 1)
        }
        song.albumName?.trim()?.takeIf(String::isNotBlank)?.let { album ->
            albums += "${song.artist.trim().lowercase()}|${album.lowercase()}"
        }
    }

    val topTracks = tracks.values
        .map { (song, plays) -> SyncedHistoryTrack(song, plays) }
        .sortedWith(compareByDescending<SyncedHistoryTrack> { it.plays }.thenBy { it.song.title.lowercase() })
    val topArtists = artists.values
        .map { (name, plays) -> SyncedHistoryArtist(name, plays) }
        .sortedWith(compareByDescending<SyncedHistoryArtist> { it.plays }.thenBy { it.name.lowercase() })

    return SyncedHistorySummary(
        totalPlays = history.size,
        distinctTracks = tracks.size,
        distinctArtists = artists.size,
        distinctAlbums = albums.size,
        topTracks = topTracks,
        topArtists = topArtists,
    )
}
