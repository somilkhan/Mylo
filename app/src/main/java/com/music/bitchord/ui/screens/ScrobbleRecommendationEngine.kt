package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.YtMusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Dependency-adapted port of LastWave RecommendationEngine's seven discovery
 * sources. Last.fm supplies taste graph/chart candidates; Mylo resolves every
 * result to a real playable YouTube Music Song and keeps its own Song contract.
 *
 * The source order intentionally keeps YouTube Music picks first. Network fan-out
 * is bounded, source failures are isolated, and cancellation is never swallowed.
 */
internal object ScrobbleRecommendationEngine {
    private data class SourceCandidate(
        val track: LastFM.DiscoveryTrack,
        val weight: Int,
        val tag: String,
        val primaryGenre: Boolean = false,
    )

    private const val TASTE_PROFILE_TTL_MILLIS = 60L * 60 * 1000
    private data class CachedTasteTags(val username: String, val loadedAtMillis: Long, val tags: List<String>)
    private val tasteProfileMutex = Mutex()
    private var cachedTasteTags: CachedTasteTags? = null

    private val genreNeighbors = mapOf(
        "indie rock" to "alternative rock", "alternative rock" to "indie rock",
        "house" to "deep house", "deep house" to "tech house", "tech house" to "techno", "techno" to "trance",
        "hip hop" to "rap", "hip-hop" to "boom bap", "rap" to "hip hop",
        "metal" to "heavy metal", "heavy metal" to "metal", "black metal" to "doom metal", "death metal" to "doom metal",
        "pop" to "indie pop", "indie pop" to "dream pop", "dream pop" to "shoegaze", "shoegaze" to "dream pop",
        "jazz" to "soul", "soul" to "r&b", "r&b" to "neo-soul", "funk" to "soul",
        "electronic" to "experimental", "ambient" to "chillwave", "lo-fi" to "chillwave", "chillwave" to "synthwave",
        "punk" to "post-punk", "post-punk" to "new wave", "new wave" to "synth-pop",
        "folk" to "indie folk", "indie folk" to "folk", "country" to "folk",
        "trap" to "drill", "drill" to "trap", "drum and bass" to "jungle", "jungle" to "drum and bass",
    )

    /** LastWave TasteProfileProvider's one-hour profile cache, narrowed to the
     * user-tag signal Mylo currently consumes. Failed reads are not cached. */
    private suspend fun loadTasteTags(username: String): List<String> {
        val cleanUsername = username.trim()
        if (cleanUsername.isBlank()) return emptyList()
        return tasteProfileMutex.withLock {
            val now = System.currentTimeMillis()
            cachedTasteTags?.takeIf {
                it.username.equals(cleanUsername, ignoreCase = true) &&
                    now - it.loadedAtMillis in 0 until TASTE_PROFILE_TTL_MILLIS
            }?.let { return@withLock it.tags }
            val fresh = try {
                LastFM.getTopTags(cleanUsername, limit = 12).getOrThrow().map { it.name }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            if (fresh.isNotEmpty()) {
                cachedTasteTags = CachedTasteTags(cleanUsername, now, fresh)
            }
            fresh
        }
    }

    suspend fun discover(
        seedTracks: List<Song>,
        seedArtists: List<String>,
        lastFmUsername: String,
        youtubeCandidates: List<Song>,
        heardVideoIds: Set<String>,
        heardSongs: List<Song>,
        limit: Int,
        onProgress: (String) -> Unit = {},
    ): List<Song> = coroutineScope {
        if (limit <= 0) return@coroutineScope emptyList()
        val seeds = seedTracks.filter { it.title.isNotBlank() && it.artist.isNotBlank() }
            .distinctBy(::recommendationIdentity).take(5)
        val artists = seedArtists.map(String::trim).filter(String::isNotBlank)
            .distinctBy { it.lowercase() }.take(8)
        val tags = loadTasteTags(lastFmUsername).take(8)
        val artistGraph = linkedSetOf<String>()
        val lastFmTracks = mutableListOf<SourceCandidate>()
        fun addSource(tracks: List<LastFM.DiscoveryTrack>, weight: Int, tag: String, primaryGenre: Boolean = false) {
            tracks.forEach { track ->
                if (track.name.isNotBlank() && track.artist.isNotBlank()) {
                    lastFmTracks += SourceCandidate(track, weight, tag, primaryGenre)
                }
            }
        }

        onProgress("Finding tracks close to your listening taste…")
        val similarTracks = seeds.map { seed ->
            async { safe { LastFM.getSimilarTracks(seed.title, seed.artist, 12).getOrThrow() } }
        }.awaitAll().filterNotNull().flatten()
        addSource(similarTracks, weight = 4, tag = "mood")

        onProgress("Following your similar-artist graph…")
        val similarArtistBatches = artists.take(4).map { artist ->
            async { safe { LastFM.getSimilarArtists(artist, 12).getOrThrow() } }
        }.awaitAll().filterNotNull()
        similarArtistBatches.flatten().forEach { name ->
            if (name.isNotBlank()) artistGraph += name.trim()
        }

        suspend fun artistCharts(names: List<String>, count: Int) {
            val charts = names.take(count).map { artist ->
                async { artist to safe { LastFM.getArtistTopTracks(artist, limit = 8).getOrThrow() } }
            }.awaitAll()
            charts.forEach { (artist, rows) ->
                addSource(
                    rows.orEmpty().map { LastFM.DiscoveryTrack(it.name, it.artist) },
                    weight = 2,
                    tag = "artist:${artist.trim().lowercase()}",
                )
            }
        }

        onProgress("Exploring related artists…")
        artistCharts(artistGraph.toList(), 4)

        onProgress("Matching your favourite genres…")
        val primaryTags = tags.take(4)
        val primaryTagBatches = primaryTags.map { tag ->
            async { tag to safe { LastFM.getTagTopTracks(tag, limit = 8, page = 2).getOrThrow() } }
        }.awaitAll()
        primaryTagBatches.forEach { (tag, tracks) ->
            tracks.orEmpty().forEach { track ->
                addSource(listOf(track), weight = 1, tag = "tag:$tag", primaryGenre = true)
            }
        }

        onProgress("Widening into neighbouring genres…")
        val neighborTags = primaryTags.mapNotNull { genreNeighbors[it.lowercase()] }
            .distinct().filterNot { candidate -> tags.any { it.equals(candidate, ignoreCase = true) } }
            .take(4)
        val neighborTagBatches = neighborTags.map { tag ->
            async { tag to safe { LastFM.getTagTopTracks(tag, limit = 6, page = 1).getOrThrow() } }
        }.awaitAll()
        neighborTagBatches.forEach { (tag, tracks) ->
            tracks.orEmpty().forEach { track ->
                addSource(listOf(track), weight = 1, tag = "tag:$tag")
            }
        }

        onProgress("Finding another layer of related artists…")
        val secondWave = artistGraph.take(4).map { artist ->
            async { safe { LastFM.getSimilarArtists(artist, 10).getOrThrow() } }
        }.awaitAll().filterNotNull().flatten()
            .distinctBy { it.trim().lowercase() }
            .filterNot { candidate -> artistGraph.any { it.equals(candidate, ignoreCase = true) } }
            .take(4)
        artistCharts(secondWave, 4)

        onProgress("Building a wider discovery pool…")
        artistCharts(artists.take(3), 3)

        // LastWave's pool is deduplicated before expensive catalogue resolution.
        // Keep YouTube Music picks first and cap Last.fm lookups to avoid request storms.
        val uniqueLastFm = lastFmTracks
            .groupBy { "${it.track.name.trim().lowercase()}|${it.track.artist.trim().lowercase()}" }
            .values
            .take((limit * 2).coerceIn(12, 30))
            .map { rows -> rows.first().track to rows }
        val resolverSlots = Semaphore(4)
        onProgress("Matching discoveries to playable YouTube Music tracks…")
        val resolvedPairs = uniqueLastFm.map { (candidate, sourceRows) ->
            async {
                resolverSlots.withPermit {
                    safe {
                        val rows = YtMusicRepository.search(
                            "${candidate.name} ${candidate.artist}",
                            SearchFilter.SONGS,
                        ).getOrThrow()
                        val songs = rows.mapNotNull { row ->
                            when (row) {
                                is SearchResult.Track -> row.song
                                is SearchResult.TopTrack -> row.song
                                is SearchResult.Browse -> null
                            }
                        }.filter(::isYouTubeCatalogTrack)
                        val match = songs.firstOrNull { song ->
                            recommendationIdentity(song) == recommendationIdentity(
                                Song(videoId = song.videoId, title = candidate.name, artist = candidate.artist, thumbnailUrl = null),
                            ) || isSameSongOrEdit(
                                song,
                                Song(videoId = song.videoId, title = candidate.name, artist = candidate.artist, thumbnailUrl = null),
                            )
                        }
                        match?.let { it to sourceRows }
                    }
                }
            }
        }.awaitAll().filterNotNull()

        val resolved = resolvedPairs.map { it.first }.distinctBy(::recommendationIdentity)
        val youtubeKeys = youtubeCandidates.mapTo(mutableSetOf(), ::recommendationIdentity)
        val sourceWeights = resolvedPairs.groupBy { recommendationIdentity(it.first) }
            .mapValues { (key, rows) ->
                if (key in youtubeKeys) 4 else rows.maxOf { row -> row.second.maxOf { it.weight } }
            }
        val sourceTags = mutableMapOf<String, Set<String>>()
        resolvedPairs.groupBy { recommendationIdentity(it.first) }.forEach { (key, rows) ->
            sourceTags[key] = rows.flatMap { it.second.map(SourceCandidate::tag) }.toSet() +
                if (key in youtubeKeys) setOf("yt-feed") else emptySet()
        }
        youtubeKeys.forEach { key -> sourceTags[key] = sourceTags[key].orEmpty() + "yt-feed" }
        val sourceCounts = sourceTags.mapValues { (_, tags) -> tags.size }
        val primaryGenres = resolvedPairs.filter { (_, rows) -> rows.any(SourceCandidate::primaryGenre) }
            .mapTo(mutableSetOf()) { recommendationIdentity(it.first) }

        // Strict exclusions are applied after all sources have been pooled;
        // neither an alternate upload nor a Last.fm fallback can re-add a heard song.
        buildDiscoverMix(
            candidates = youtubeCandidates + resolved,
            heardVideoIds = heardVideoIds,
            heardSongs = heardSongs,
            topArtistNames = artists.toSet(),
            recentArtistNames = seeds.map { it.artist }.toSet(),
            sourceWeights = sourceWeights,
            sourceCounts = sourceCounts,
            sourceTags = sourceTags,
            primaryGenreIdentities = primaryGenres,
            limit = limit,
        )
    }

    private suspend fun <T> safe(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
