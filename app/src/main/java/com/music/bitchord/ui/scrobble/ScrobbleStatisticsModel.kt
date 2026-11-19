package com.music.bitchord.ui.scrobble

import com.music.bitchord.data.generate.PlaylistGenerationRules
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.stats.RankedSong

internal data class MergedStatisticsTrack(
    val identity: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val song: Song?,
    val localPlays: Long,
    val lastFmPlays: Long,
    val plays: Long,
    val localListeningMs: Long,
)

internal fun mergeStatisticsTracks(
    localTracks: List<RankedSong>,
    lastFmTracks: List<LastFM.RankedTrack>,
    lastFmConnected: Boolean,
): List<MergedStatisticsTrack> {
    val localGroups = localTracks
        .filter { it.song.title.isNotBlank() && it.song.artist.isNotBlank() }
        .groupBy { PlaylistGenerationRules.trackKey(it.song) }
    val remoteGroups = if (lastFmConnected) {
        lastFmTracks
            .filter { it.name.isNotBlank() && it.artist.isNotBlank() }
            .groupBy { PlaylistGenerationRules.trackKey(it.name, it.artist) }
    } else {
        emptyMap()
    }
    val identities = linkedSetOf<String>().apply {
        addAll(localGroups.keys)
        addAll(remoteGroups.keys)
    }

    return identities.mapNotNull { identity ->
        val local = localGroups[identity].orEmpty()
        val remote = remoteGroups[identity].orEmpty()
        val localPlays = local.sumOf { it.plays.coerceAtLeast(0).toLong() }
        val lastFmPlays = remote.sumOf { it.playCount.coerceAtLeast(0L) }
        val plays = if (lastFmConnected) maxOf(localPlays, lastFmPlays) else localPlays
        if (plays <= 0L) return@mapNotNull null

        val bestLocal = local.maxByOrNull { it.ms }
        val bestRemote = remote.maxByOrNull { it.playCount }
        val title = bestLocal?.song?.title?.takeIf(String::isNotBlank) ?: bestRemote?.name.orEmpty()
        val artist = bestLocal?.song?.artist?.takeIf(String::isNotBlank) ?: bestRemote?.artist.orEmpty()
        MergedStatisticsTrack(
            identity = identity,
            title = title,
            artist = artist,
            artworkUrl = bestLocal?.song?.thumbnailUrl?.takeIf(String::isNotBlank)
                ?: bestRemote?.artworkUrl?.takeIf(String::isNotBlank),
            song = bestLocal?.song,
            localPlays = localPlays,
            lastFmPlays = if (lastFmConnected) lastFmPlays else 0L,
            plays = plays,
            localListeningMs = local.sumOf { it.ms.coerceAtLeast(0L) },
        )
    }.sortedWith(
        compareByDescending<MergedStatisticsTrack> { it.plays }
            .thenByDescending { it.localListeningMs }
            .thenBy { it.identity },
    )
}
