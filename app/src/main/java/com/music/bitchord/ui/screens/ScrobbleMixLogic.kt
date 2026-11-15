package com.music.bitchord.ui.screens

import com.music.bitchord.data.ListeningHistory
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.SearchResult
import kotlin.math.roundToInt

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
 * LastWave GeneratedTrack.normalizeTrackText/key port. Keep upstream whitespace
 * semantics while applying them to Mylo's Song model.
 */
private fun String.normalizeLastWaveTrackText(): String {
    val trimmed = trim()
    var needsWork = false
    for (i in trimmed.indices) {
        val c = trimmed[i]
        if (c.isLastWaveRegexWhitespace() &&
            (c != ' ' || (i + 1 < trimmed.length && trimmed[i + 1].isLastWaveRegexWhitespace()))
        ) {
            needsWork = true
            break
        }
    }
    if (!needsWork) return trimmed
    val out = StringBuilder(trimmed.length)
    var inRun = false
    for (c in trimmed) {
        if (c.isLastWaveRegexWhitespace()) {
            if (!inRun) out.append(' ')
            inRun = true
        } else {
            out.append(c)
            inRun = false
        }
    }
    return out.toString()
}

private fun Char.isLastWaveRegexWhitespace(): Boolean =
    this == ' ' || this == '\t' || this == '\n' || this == '\u000B' ||
        this == '\u000C' || this == '\r'

/** Match String.equals(ignoreCase=true), including Turkish I and Greek sigma. */
private fun String.lastWaveFoldCase(): String {
    val out = CharArray(length)
    for (i in indices) out[i] = Character.toLowerCase(Character.toUpperCase(this[i]))
    return String(out)
}

/** Same normalized title+artist identity used by LastWave GeneratedTrack.key. */
internal fun recommendationIdentity(song: Song): String =
    "${song.title.normalizeLastWaveTrackText().lowercase()}|${song.artist.normalizeLastWaveTrackText().lowercase()}"

/** Direct port of LastWave GeneratedTrack.distinctSongs(), adapted to Song. */
internal fun distinctRecommendationSongs(candidates: List<Song>): List<Song> {
    if (candidates.size < 2) return candidates.filter {
        it.title.isNotBlank() && it.artist.isNotBlank() && it.videoId.isNotBlank()
    }
    val out = ArrayList<Song>(candidates.size)
    val videoIds = HashSet<String>(candidates.size * 2)
    val keys = HashSet<String>(candidates.size * 2)
    val namePairs = HashSet<Pair<String, String>>(candidates.size * 2)
    for (song in candidates) {
        if (song.title.isBlank() || song.artist.isBlank() || song.videoId.isBlank()) continue
        val key = recommendationIdentity(song)
        val namePair = song.title.trim().lastWaveFoldCase() to song.artist.trim().lastWaveFoldCase()
        if (song.videoId in videoIds) continue
        if (key in keys || namePair in namePairs) continue
        out += song
        videoIds += song.videoId
        keys += key
        namePairs += namePair
    }
    return out
}

private fun recommendationArtistKey(artist: String): String =
    (com.music.bitchord.data.stats.ListeningStats.primaryArtist(artist) ?: artist)
        .trim().replace(Regex("\\s+"), " ").lowercase()

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
 * Discovery is deliberately different from Never Heard: it uses whatever
 * discovery feed is available, but still rejects previously heard tracks and
 * alternate catalogue uploads by title/artist, not only by video ID.
 */
/** Last.fm-only metadata used by LastWave's hidden-gem/community/match score. */
internal data class LastWaveTrackSignals(
    val listeners: Long? = null,
    val playCount: Long? = null,
    val match: Double? = null,
    val album: String? = null,
)

private fun LastWaveTrackSignals.gemScore(): Int {
    val count = listeners ?: return 0
    return when {
        count < 3_000 -> 4
        count < 10_000 -> 7
        count < 30_000 -> 9
        count < 100_000 -> 6
        count < 400_000 -> 2
        count < 1_500_000 -> 0
        else -> -2
    }
}

private fun LastWaveTrackSignals.communityScore(): Int {
    val listenerCount = listeners ?: return 0
    val plays = playCount ?: return 0
    if (listenerCount == 0L) return 0
    val ratio = plays.toDouble() / listenerCount.toDouble()
    return when {
        ratio >= 6 -> 8
        ratio >= 3 -> 5
        ratio >= 1.5 -> 2
        else -> 0
    }
}

private fun Double?.orZero(): Double = this ?: 0.0

internal fun buildDiscoverMix(
    candidates: List<Song>,
    heardVideoIds: Set<String>,
    heardSongs: List<Song> = emptyList(),
    topArtistNames: Set<String> = emptySet(),
    recentArtistNames: Set<String> = emptySet(),
    artistAffinity: Map<String, Double> = emptyMap(),
    sourceWeights: Map<String, Int> = emptyMap(),
    sourceCounts: Map<String, Int> = emptyMap(),
    sourceTags: Map<String, Set<String>> = emptyMap(),
    primaryGenreIdentities: Set<String> = emptySet(),
    trackSignals: Map<String, LastWaveTrackSignals> = emptyMap(),
    limit: Int = 25,
): List<Song> {
    if (limit <= 0) return emptyList()
    val heardByArtist = heardSongs
        .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
        .groupBy { recommendationArtistKey(it.artist) }
    val fresh = distinctRecommendationSongs(candidates.filter { candidate ->
        if (!isYouTubeCatalogTrack(candidate) || candidate.videoId in heardVideoIds) {
            false
        } else {
            val artistKey = recommendationArtistKey(candidate.artist)
            heardByArtist[artistKey].orEmpty().none { heard ->
                recommendationIdentity(candidate) == recommendationIdentity(heard) ||
                    isSameSongOrEdit(candidate, heard)
            }
        }
    })
    if (fresh.size < 2) return fresh.take(limit)

    // LastWave RecommendationEngine.recoScore/selectFinal, adapted to Song.
    // Last.fm listeners/playcount/match metadata is not part of Mylo's Song model.
    val top = topArtistNames.mapTo(mutableSetOf(), ::recommendationArtistKey)
    val recent = recentArtistNames.mapTo(mutableSetOf(), ::recommendationArtistKey)
    val genreCap = maxOf(2, kotlin.math.ceil(limit * 0.35).toInt())
    val scored = fresh.mapIndexed { index, song ->
        val key = recommendationIdentity(song)
        val artist = recommendationArtistKey(song.artist)
        val tags = sourceTags[key].orEmpty()
        val signals = trackSignals[key] ?: LastWaveTrackSignals()
        val weight = sourceWeights[key] ?: 4
        val known = artist in top || artist in recent
        val base = when {
            weight >= 4 -> 50.0
            weight >= 3 -> 38.0
            weight >= 2 -> 26.0
            else -> 12.0
        }
        val score = base +
            (artistAffinity[artist] ?: 0.0).coerceIn(0.0, 1.0) * 30.0 +
            (if (artist in top) 16.0 else 0.0) +
            (if (artist in recent) 8.0 else 0.0) +
            (if (!known) 3.0 else 0.0) +
            (if ("yt-feed" in tags) 8.0 else 0.0) +
            (if ("yt-liked" in tags) 6.0 else 0.0) +
            (if ("yt-recent" in tags) 4.0 else 0.0) +
            (if (key in primaryGenreIdentities) 4.0 else 0.0) +
            (when (sourceCounts[key] ?: 1) { in 3..Int.MAX_VALUE -> 20.0; 2 -> 10.0; else -> 0.0 }) +
            signals.match?.let { (it * 34.0).roundToInt().toDouble() }.orZero() +
            signals.gemScore() +
            signals.communityScore() +
            6.0 // all candidates surviving strict heard filtering are fresh
        Triple(song, score, index)
    }.sortedWith(compareByDescending<Triple<Song, Double, Int>> { it.second }.thenBy { it.third })

    fun attempt(artistCap: Int, albumCapOn: Boolean, genreCapOn: Boolean): List<Song> {
        val artistCounts = mutableMapOf<String, Int>()
        val albumCounts = mutableMapOf<String, Int>()
        val genreCounts = mutableMapOf<String, Int>()
        val picked = mutableListOf<Song>()
        for ((song, _, _) in scored) {
            val key = recommendationIdentity(song)
            val artist = recommendationArtistKey(song.artist)
            val album = (trackSignals[key]?.album ?: song.albumName)?.trim()?.lowercase().orEmpty()
            val genreTag = sourceTags[key].orEmpty().firstOrNull { it.startsWith("tag:") }
            if ((artistCounts[artist] ?: 0) >= artistCap) continue
            if (albumCapOn && album.isNotBlank() && (albumCounts[album] ?: 0) >= 2) continue
            if (genreCapOn && genreTag != null && (genreCounts[genreTag] ?: 0) >= genreCap) continue
            artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
            if (album.isNotBlank()) albumCounts[album] = (albumCounts[album] ?: 0) + 1
            if (genreTag != null) genreCounts[genreTag] = (genreCounts[genreTag] ?: 0) + 1
            picked += song
            if (picked.size >= limit) break
        }
        return picked
    }

    // LastWave relaxes diversity only as much as needed to fill the queue.
    var selected = attempt(2, albumCapOn = true, genreCapOn = true)
    if (selected.size < limit) selected = attempt(3, albumCapOn = true, genreCapOn = true)
    if (selected.size < limit) selected = attempt(3, albumCapOn = true, genreCapOn = false)
    if (selected.size < limit) selected = attempt(3, albumCapOn = false, genreCapOn = false)
    if (selected.size < limit) selected = attempt(Int.MAX_VALUE, albumCapOn = false, genreCapOn = false)

    // Round-robin genre buckets to avoid adjacent tracks from one tag.
    val buckets = LinkedHashMap<String, ArrayDeque<Song>>()
    selected.forEach { song ->
        val label = sourceTags[recommendationIdentity(song)].orEmpty()
            .firstOrNull { it.startsWith("tag:") } ?: "other"
        buckets.getOrPut(label) { ArrayDeque() }.addLast(song)
    }
    val interleaved = mutableListOf<Song>()
    var previous: String? = null
    while (interleaved.size < selected.size) {
        val label = buckets.keys.firstOrNull { it != previous && !buckets[it].isNullOrEmpty() }
            ?: buckets.keys.firstOrNull { !buckets[it].isNullOrEmpty() }
            ?: break
        interleaved += buckets.getValue(label).removeFirst()
        previous = label
    }

    // LastWave places the strongest recommendation first and runner-up last.
    if (interleaved.size >= 3) {
        val scoreById = scored.associate { it.first.videoId to it.second }
        val opener = interleaved.maxByOrNull { scoreById[it.videoId] ?: 0.0 } ?: return interleaved
        val closer = interleaved.asSequence().filter { it.videoId != opener.videoId }
            .maxByOrNull { scoreById[it.videoId] ?: 0.0 } ?: return interleaved
        return listOf(opener) + interleaved.filter { it.videoId != opener.videoId && it.videoId != closer.videoId } + closer
    }
    return interleaved
}
/**
 * Tag generation uses Mylo's real YouTube Music song search. Browse cards are
 * deliberately ignored: this generator must return playable tracks only.
 */
internal fun buildTagMix(
    results: List<SearchResult>,
    limit: Int = 25,
): List<Song> = distinctRecommendationSongs(
    results.mapNotNull { result ->
        when (result) {
            is SearchResult.TopTrack -> result.song
            is SearchResult.Track -> result.song
            is SearchResult.Browse -> null
        }
    }.filter(::isYouTubeCatalogTrack),
).take(limit.coerceAtLeast(0))

/**
 * Library generation combines Liked Music and tracks added to the YouTube
 * Music library, preserving the repository's source order and removing the
 * overlap between those feeds.
 */
internal fun buildLibraryMix(
    library: LibraryPage,
    limit: Int = 25,
): List<Song> = distinctRecommendationSongs(
    (library.likedSongs + library.librarySongs).filter(::isYouTubeCatalogTrack),
).take(limit.coerceAtLeast(0))

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
    val seenVideoIds = mutableSetOf<String>()
    val seenNames = mutableSetOf<String>()
    val youtubeTracks = youtube.filter { it.title.isNotBlank() && it.artist.isNotBlank() && it.videoId.isNotBlank() }
        .distinctBy(::recommendationIdentity)
    val lastFmTracks = lastFm.filter { it.title.isNotBlank() && it.artist.isNotBlank() && it.videoId.isNotBlank() }
        .distinctBy(::recommendationIdentity)
    val result = ArrayList<Song>(minOf(limit, youtubeTracks.size + lastFmTracks.size))
    var youtubeIndex = 0
    var lastFmIndex = 0
    while (result.size < limit && (youtubeIndex < youtubeTracks.size || lastFmIndex < lastFmTracks.size)) {
        repeat(2) {
            if (youtubeIndex < youtubeTracks.size && result.size < limit) {
                val track = youtubeTracks[youtubeIndex++]
                if (seenVideoIds.add(track.videoId) && seenNames.add(recommendationIdentity(track))) result += track
            }
        }
        if (lastFmIndex < lastFmTracks.size && result.size < limit) {
            val track = lastFmTracks[lastFmIndex++]
            if (seenVideoIds.add(track.videoId) && seenNames.add(recommendationIdentity(track))) result += track
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
    // Same identity semantics as LastWave GeneratedTrack.distinctSongs().
    val valid = distinctRecommendationSongs(candidates)
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
    val distinctSongs = buildList<Song> {
        candidates.filter(::isYouTubeCatalogTrack).forEach { candidate ->
            val duplicate = any { existing ->
                existing.videoId == candidate.videoId ||
                    (recommendationArtistKey(existing.artist) == recommendationArtistKey(candidate.artist) &&
                        (recommendationIdentity(existing) == recommendationIdentity(candidate) ||
                            isSameSongOrEdit(candidate, existing)))
            }
            if (!duplicate) add(candidate)
        }
    }
    val seedKey = recommendationArtistKey(seedArtist)
    fun applyArtistCap(maxSeedArtist: Int, maxOtherArtist: Int): List<Song> {
        val counts = mutableMapOf<String, Int>()
        return distinctSongs.filter { song ->
            val artistKey = recommendationArtistKey(song.artist)
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

/**
 * Similar-artist mixes must not drift back to the seed artist just because
 * YouTube Radio includes same-artist tracks. Last.fm's artist graph supplies
 * the secondary pool; both sources are merged and artist-balanced.
 */
internal fun buildSimilarArtistMix(
    youtubeRadio: List<Song>,
    lastFmSimilar: List<Song>,
    seedArtist: String,
    limit: Int,
): List<Song> {
    if (limit <= 0) return emptyList()
    val seedKey = recommendationArtistKey(seedArtist)
    val youtubeCandidates = youtubeRadio.filter { recommendationArtistKey(it.artist) != seedKey }
    val lastFmCandidates = lastFmSimilar.filter { recommendationArtistKey(it.artist) != seedKey }
    // Preserve LastWave's YouTube-first 2:1 source blend, with enough headroom
    // for the subsequent artist cap to fill the requested queue.
    val blended = buildYouTubeFirstMix(
        youtube = youtubeCandidates,
        lastFm = lastFmCandidates,
        limit = limit * 3,
    )
    return buildArtistCappedMix(
        candidates = blended,
        seedArtist = seedArtist,
        targetSize = limit,
    )
}

/** Only catalogue tracks absent from the supplied listening history qualify as new-to-you. */
internal fun buildNeverHeardMix(
    candidates: List<Song>,
    heardVideoIds: Set<String>,
    heardSongs: List<Song> = emptyList(),
    limit: Int = 25,
): List<Song> {
    if (limit <= 0) return emptyList()

    // A track can be heard under several YouTube IDs (official audio, video,
    // lyric upload, remix). Match the normalized title/artist as well as the
    // ID, following LastWave's never-heard exclusion rather than treating a
    // new upload as a new song.
    val heardByArtist = heardSongs
        .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
        .groupBy { recommendationArtistKey(it.artist) }

    val unheard = candidates.filter { candidate ->
        if (!isYouTubeCatalogTrack(candidate) || candidate.videoId in heardVideoIds) {
            false
        } else {
            val artistKey = recommendationArtistKey(candidate.artist)
            heardByArtist[artistKey].orEmpty().none { heard ->
                recommendationIdentity(candidate) == recommendationIdentity(heard) ||
                    isSameSongOrEdit(candidate, heard)
            }
        }
    }
    return distinctRecommendationSongs(unheard).take(limit)
}
