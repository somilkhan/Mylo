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

internal data class SyncedHistoryAlbum(
    val name: String,
    val artist: String,
    val plays: Int,
    val artworkUrl: String?,
)

internal data class SyncedHistorySummary(
    val totalPlays: Int,
    val distinctTracks: Int,
    val distinctArtists: Int,
    val distinctAlbums: Int,
    val topTracks: List<SyncedHistoryTrack>,
    val topArtists: List<SyncedHistoryArtist>,
    val topAlbums: List<SyncedHistoryAlbum>,
)

/** Repeated rows are separate plays in YouTube Music history; keep their count. */
/**
 * Combines device playback aggregates with account history without double-counting
 * a track present in both sources. The larger source count is used for ranking;
 * local listening time breaks ties and local-only tracks remain eligible.
 */
internal fun buildCombinedFavouriteTracks(
    local: List<com.music.bitchord.data.stats.RankedSong>,
    synced: List<SyncedHistoryTrack>,
    limit: Int = 25,
): List<com.music.bitchord.data.model.Song> {
    if (limit <= 0) return emptyList()
    val localByKey = local.associateBy { songIdentity(it.song) }
    val syncedByKey = synced.associateBy { songIdentity(it.song) }
    val songs = LinkedHashMap<String, com.music.bitchord.data.model.Song>()
    local.forEach {
        val key = songIdentity(it.song)
        if (key !in songs) songs[key] = it.song
    }
    synced.forEach {
        val key = songIdentity(it.song)
        if (key !in songs) songs[key] = it.song
    }

    return songs.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, com.music.bitchord.data.model.Song>> {
                maxOf(localByKey[it.key]?.plays ?: 0, syncedByKey[it.key]?.plays ?: 0)
            }.thenByDescending { localByKey[it.key]?.ms ?: 0L }
                .thenBy { it.value.title.lowercase() },
        )
        .take(limit)
        .map { it.value }
}

internal fun buildSyncedHistorySummary(
    history: List<com.music.bitchord.data.model.Song>,
): SyncedHistorySummary {
    val tracks = LinkedHashMap<String, Pair<com.music.bitchord.data.model.Song, Int>>()
    val artists = LinkedHashMap<String, Pair<String, Int>>()
    val albums = LinkedHashMap<String, SyncedHistoryAlbum>()

    history.forEach { song ->
        val trackKey = song.videoId.takeIf(String::isNotBlank)
            ?: "${song.title.trim()}|${song.artist.trim()}".lowercase()
        val existingTrack = tracks[trackKey]
        tracks[trackKey] = (existingTrack?.first ?: song) to ((existingTrack?.second ?: 0) + 1)

        val primaryArtist = com.music.bitchord.data.stats.ListeningStats.primaryArtist(song.artist)
            ?.trim()?.takeIf(String::isNotBlank)
        primaryArtist?.let { artist ->
            val artistKey = artist.lowercase()
            val existingArtist = artists[artistKey]
            artists[artistKey] = (existingArtist?.first ?: artist) to ((existingArtist?.second ?: 0) + 1)
        }
        song.albumName?.trim()?.takeIf(String::isNotBlank)?.let { album ->
            val artist = primaryArtist ?: song.artist.trim()
            val albumKey = "${artist.lowercase()}|${album.lowercase()}"
            val existingAlbum = albums[albumKey]
            albums[albumKey] = SyncedHistoryAlbum(
                name = existingAlbum?.name ?: album,
                artist = existingAlbum?.artist ?: artist,
                plays = (existingAlbum?.plays ?: 0) + 1,
                artworkUrl = existingAlbum?.artworkUrl ?: song.thumbnailUrl,
            )
        }
    }

    val topTracks = tracks.values
        .map { (song, plays) -> SyncedHistoryTrack(song, plays) }
        .sortedWith(compareByDescending<SyncedHistoryTrack> { it.plays }.thenBy { it.song.title.lowercase() })
    val topArtists = artists.values
        .map { (name, plays) -> SyncedHistoryArtist(name, plays) }
        .sortedWith(compareByDescending<SyncedHistoryArtist> { it.plays }.thenBy { it.name.lowercase() })
    val topAlbums = albums.values
        .sortedWith(compareByDescending<SyncedHistoryAlbum> { it.plays }.thenBy { it.name.lowercase() })

    return SyncedHistorySummary(
        totalPlays = history.size,
        distinctTracks = tracks.size,
        distinctArtists = artists.size,
        distinctAlbums = albums.size,
        topTracks = topTracks,
        topArtists = topArtists,
        topAlbums = topAlbums,
    )
}
