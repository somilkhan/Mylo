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

/**
 * Alternates two recommendation sources while preserving their order.
 * The primary source owns overlapping identities so its canonical metadata
 * is never replaced by a duplicate row from the secondary source.
 */
internal fun buildBalancedSourceMix(
    primary: List<Song>,
    secondary: List<Song>,
    limit: Int,
): List<Song> {
    if (limit <= 0) return emptyList()
    val primaryTracks = primary.distinctBy(::songIdentity)
    val primaryIdentities = primaryTracks.mapTo(mutableSetOf(), ::songIdentity)
    val secondaryTracks = secondary.distinctBy(::songIdentity)
        .filterNot { songIdentity(it) in primaryIdentities }

    val result = ArrayList<Song>(limit)
    var primaryIndex = 0
    var secondaryIndex = 0
    while (result.size < limit && (primaryIndex < primaryTracks.size || secondaryIndex < secondaryTracks.size)) {
        if (primaryIndex < primaryTracks.size && result.size < limit) {
            result += primaryTracks[primaryIndex++]
        }
        if (secondaryIndex < secondaryTracks.size && result.size < limit) {
            result += secondaryTracks[secondaryIndex++]
        }
    }
    return result
}

/**
 * Weighted, deterministic blend for the Taste mix. Favourites and recent
 * listening remain the primary sources; discovery fills the remaining space.
 * A track appearing in multiple sources is attributed to its highest-priority
 * source and can never occupy two queue positions.
 */
internal fun buildTasteMix(
    favourites: List<Song>,
    recent: List<Song>,
    fresh: List<Song>,
    limit: Int = 20,
): List<Song> {
    if (limit <= 0) return emptyList()

    val seen = mutableSetOf<String>()
    val pools = listOf(favourites, recent, fresh).map { source ->
        source.filter { seen.add(songIdentity(it)) }
    }
    val weights = intArrayOf(2, 2, 1)
    val positions = IntArray(pools.size)
    val result = ArrayList<Song>(limit)

    while (result.size < limit) {
        var added = false
        pools.indices.forEach { sourceIndex ->
            repeat(weights[sourceIndex]) {
                val next = pools[sourceIndex].getOrNull(positions[sourceIndex]++) ?: return@repeat
                result += next
                added = true
            }
        }
        if (!added) break
    }
    return result
}

/**
 * Port of LastWave GenerateRepository.isSameSongOrEdit. A distinct YouTube ID
 * is not enough to make a different recommendation: radio can return the seed
 * again as an official video, lyric upload, remix or cover.
 */
internal fun isSameSongOrEdit(candidate: Song, seed: Song): Boolean {
    fun cleanTitle(title: String): String = title.lowercase()
        .replace(Regex("""\\s*[({\\[][^)\\]]*[)}\\]]"""), " ")
        .replace(
            Regex("""(?i)\\b(official\\s+video|official\\s+audio|lyrics?|remix|mix|edit|bootleg|flip|vip|slowed|reverb|sped\\s+up|speed\\s+up|cover|version|live|acoustic|instrumental|feat\\.?|ft\\.?)\\b.*"""),
            " ",
        )
        .replace(Regex("[^a-z0-9]"), "")
        .trim()

    val cleanSeed = cleanTitle(seed.title)
    val cleanCandidate = cleanTitle(candidate.title)
    if (cleanCandidate.isBlank() || cleanSeed.isBlank()) return false
    if (cleanCandidate == cleanSeed) return true

    val lowerCandidate = candidate.title.lowercase()
    val lowerSeed = seed.title.lowercase().trim()
    if (lowerSeed.length >= 3 && lowerCandidate.contains(lowerSeed)) {
        val editIndicators = listOf(
            "remix", "edit", "mix", "cover", "version", "flip", "bootleg",
            "vip", "slowed", "reverb", "sped", "speed", "acoustic", "instrumental",
            "tribute", "karaoke", "live", "rework", "dub", "mashup", "rendition",
        )
        if (editIndicators.any(lowerCandidate::contains)) return true
        val lowerArtist = seed.artist.lowercase().trim()
        if (lowerArtist.length >= 3 && lowerCandidate.contains(lowerArtist)) return true
    }
    if (cleanSeed.length >= 10 && cleanCandidate.contains(cleanSeed)) return true
    if (cleanCandidate.length >= 10 && cleanSeed.contains(cleanCandidate)) return true
    return false
}

/**
 * Port of LastWave GenerateRepository.applyArtistCap: cap the seed artist at
 * one and other artists at two, relax to two/three only when that would leave
 * the requested mix short, then preserve the full deduplicated pool as a final
 * fallback rather than returning an unexpectedly tiny playlist.
 */
internal fun buildArtistCappedMix(
    candidates: List<Song>,
    seedArtist: String,
    targetSize: Int,
): List<Song> {
    if (targetSize <= 0) return emptyList()
    val distinctSongs = candidates.distinctBy(::songIdentity)
    val seedKey = seedArtist.trim().lowercase()
    fun applyArtistCap(maxSeedArtist: Int, maxOtherArtist: Int): List<Song> {
        val counts = mutableMapOf<String, Int>()
        return distinctSongs.filter { song ->
            val artistKey = song.artist.trim().lowercase()
            val maxAllowed = if (artistKey == seedKey) maxSeedArtist else maxOtherArtist
            val current = counts[artistKey] ?: 0
            if (current < maxAllowed) {
                counts[artistKey] = current + 1
                true
            } else {
                false
            }
        }
    }

    var curated = applyArtistCap(maxSeedArtist = 1, maxOtherArtist = 2)
    if (curated.size < targetSize) {
        curated = applyArtistCap(maxSeedArtist = 2, maxOtherArtist = 3)
    }
    if (curated.size < targetSize) curated = distinctSongs
    return curated.take(targetSize)
}

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
