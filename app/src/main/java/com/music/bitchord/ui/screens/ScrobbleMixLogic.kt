package com.music.bitchord.ui.screens

import com.music.bitchord.data.ListeningHistory
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.SearchResult

/** YouTube's radio and artist catalogue endpoints require an 11-character video id. */
private val YOUTUBE_VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

internal fun isYouTubeCatalogTrack(song: Song): Boolean =
    YOUTUBE_VIDEO_ID.matches(song.videoId)

/** Stable identity across local history rows, synced history and queue snapshots. */
internal fun songIdentity(song: Song): String =
    song.localUri?.takeIf(String::isNotBlank)?.let { "local:$it" }
        ?: song.localPath?.takeIf(String::isNotBlank)?.let { "path:$it" }
        ?: song.videoId.takeIf(String::isNotBlank)?.let { "video:$it" }
        ?: "${song.title.lowercase()}|${song.artist.lowercase()}"

/**
 * Rediscover only counts completed plays from ListeningStats, not raw history
 * rows: a two-second skip is recorded in ListeningHistory but is not a play.
 */
internal fun buildRediscoverMix(
    listens: List<ListeningHistory.Entry>,
    allTimePlayCounts: Map<String, Int>,
    nowMillis: Long,
    quietPeriodMillis: Long = 60L * 24 * 60 * 60 * 1000,
    minimumPlays: Int = 3,
    limit: Int = 25,
): List<Song> {
    val cutoff = nowMillis - quietPeriodMillis
    return listens.groupBy { songIdentity(it.song) }
        .mapNotNull { (identity, events) ->
            val latest = events.maxByOrNull { it.startedAt } ?: return@mapNotNull null
            val completedPlays = allTimePlayCounts[identity] ?: 0
            if (completedPlays < minimumPlays || latest.startedAt > cutoff) {
                null
            } else {
                latest
            }
        }
        // Rediscover the tracks that have been absent the longest first.
        // Group insertion order depends on the history file's storage order,
        // which is not a meaningful ranking and can otherwise hide older picks.
        .sortedBy { it.startedAt }
        .distinctBy { songIdentity(it.song) }
        .take(limit.coerceAtLeast(0))
        .map { it.song }
}


/**
 * Tag generation uses Mylo's real YouTube Music song search. Browse cards are
 * deliberately ignored: this generator must return playable tracks only.
 */
internal fun buildTagMix(
    results: List<SearchResult>,
    limit: Int = 25,
): List<Song> = results.asSequence()
    .mapNotNull { result ->
        when (result) {
            is SearchResult.TopTrack -> result.song
            is SearchResult.Track -> result.song
            is SearchResult.Browse -> null
        }
    }
    .filter(::isYouTubeCatalogTrack)
    .distinctBy(::songIdentity)
    .take(limit.coerceAtLeast(0))
    .toList()

/**
 * Library generation combines Liked Music and tracks added to the YouTube
 * Music library, preserving the repository's source order and removing the
 * overlap between those feeds.
 */
internal fun buildLibraryMix(
    library: LibraryPage,
    limit: Int = 25,
): List<Song> = (library.likedSongs + library.librarySongs).asSequence()
    .filter(::isYouTubeCatalogTrack)
    .distinctBy(::songIdentity)
    .take(limit.coerceAtLeast(0))
    .toList()

/** Only catalogue tracks absent from the supplied listening history qualify as new-to-you. */
internal fun buildNeverHeardMix(
    candidates: List<Song>,
    heardVideoIds: Set<String>,
    limit: Int = 25,
): List<Song> = candidates.asSequence()
    .filter { it.videoId.isNotBlank() && it.videoId !in heardVideoIds }
    .distinctBy(::songIdentity)
    .take(limit.coerceAtLeast(0))
    .toList()
