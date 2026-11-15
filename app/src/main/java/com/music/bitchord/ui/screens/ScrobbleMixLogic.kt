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
internal fun buildYouTubeFirstMix(
    youtube: List<Song>,
    lastFm: List<Song>,
    limit: Int,
): List<Song> {
    if (limit <= 0) return emptyList()
    val seen = mutableSetOf<String>()
    val youtubeTracks = youtube.distinctBy(::songIdentity)
    val lastFmTracks = lastFm.distinctBy(::songIdentity)
    val result = ArrayList<Song>(minOf(limit, youtubeTracks.size + lastFmTracks.size))
    var youtubeIndex = 0
    var lastFmIndex = 0
    while (result.size < limit && (youtubeIndex < youtubeTracks.size || lastFmIndex < lastFmTracks.size)) {
        repeat(2) {
            if (youtubeIndex < youtubeTracks.size && result.size < limit) {
                val track = youtubeTracks[youtubeIndex++]
                if (seen.add(songIdentity(track))) result += track
            }
        }
        if (lastFmIndex < lastFmTracks.size && result.size < limit) {
            val track = lastFmTracks[lastFmIndex++]
            if (seen.add(songIdentity(track))) result += track
        }
    }
    return result
}

/**
 * Port of LastWave's precheck: reject incomplete rows, deduplicate by track
 * identity and cap any one artist at three entries.
 */
internal fun buildPrecheckedMix(
    candidates: List<Song>,
    limit: Int = 25,
): List<Song> {
    if (limit <= 0) return emptyList()
    val valid = candidates.filter {
        it.title.isNotBlank() && it.artist.isNotBlank() && it.videoId.isNotBlank()
    }.distinctBy(::songIdentity)
    val artistCounts = mutableMapOf<String, Int>()
    return valid.filter { song ->
        val key = song.artist.trim().lowercase()
        val count = (artistCounts[key] ?: 0) + 1
        artistCounts[key] = count
        count <= 3
    }.take(limit)
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
    return buildPrecheckedMix(result, limit)
}

/**
 * Port of LastWave GenerateRepository.isSameSongOrEdit. A distinct YouTube ID
 * is not enough to make a different recommendation: radio can return the seed
 * again as an official video, lyric upload, remix or cover.
 */
internal fun isSameSongOrEdit(candidate: Song, seed: Song): Boolean {
    fun cleanTitle(title: String): String = title.lowercase()
        .replace(Regex("""\s*[({\[][^)\]]*[)}\]]"""), " ")
        .replace(
            Regex("""(?i)\b(official\s+video|official\s+audio|lyrics?|remix|mix|edit|bootleg|flip|vip|slowed|reverb|sped\s+up|speed\s+up|cover|version|live|acoustic|instrumental|feat\.?|ft\.?)\b.*"""),
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
 * Builds a radio queue from YouTube's seed-specific radio first, then fills
 * short responses from personal favourites, recent listens and discovery.
 * The seed itself and alternate uploads/remixes are excluded from every pool.
 */
internal fun buildRadioMix(
    radio: List<Song>,
    similar: List<Song>,
    favourites: List<Song>,
    recent: List<Song>,
    discovery: List<Song>,
    seed: Song,
    limit: Int,
): List<Song> {
    if (limit <= 0) return emptyList()
    val seen = mutableSetOf<String>()
    val candidates = ArrayList<Song>()
    listOf(radio, similar, favourites, recent, discovery).forEach { pool ->
        pool.forEach { candidate ->
            if (candidate.title.isNotBlank() &&
                candidate.artist.isNotBlank() &&
                candidate.videoId.isNotBlank() &&
                candidate.videoId != seed.videoId &&
                !isSameSongOrEdit(candidate, seed) &&
                seen.add(songIdentity(candidate))
            ) {
                candidates += candidate
            }
        }
    }

    // LastWave's track radio removes alternate uploads by normalized title
    // before applying its artist-diversity cap. Different video IDs alone
    // must not put the same recording into the queue twice.
    fun cleanTitle(title: String): String = title.lowercase()
        .replace(Regex("""\s*[({\[][^)\]]*[)}\]]"""), " ")
        .replace(
            Regex("""(?i)\b(official\s+video|official\s+audio|lyrics?|remix|mix|edit|bootleg|flip|vip|slowed|reverb|sped\s+up|speed\s+up|cover|version|live|acoustic|instrumental|feat\.?|ft\.?)\b.*"""),
            " ",
        )
        .replace(Regex("[^a-z0-9]"), "")
        .trim()
    val seenTitles = mutableSetOf<String>()
    val titleDistinct = candidates.filter { candidate ->
        val key = cleanTitle(candidate.title)
        key.isBlank() || seenTitles.add(key)
    }
    return buildArtistCappedMix(titleDistinct, seed.artist, limit)
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
