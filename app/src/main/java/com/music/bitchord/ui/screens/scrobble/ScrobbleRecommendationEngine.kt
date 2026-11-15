package com.music.bitchord.ui.screens.scrobble

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.stats.ScrobbleChartEntry
import com.music.bitchord.data.stats.ScrobbleStatsRepository
import com.music.bitchord.data.stats.ScrobbleStatsSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

private const val LASTFM_SOURCE_TIMEOUT_MS = 6_000L
private const val YOUTUBE_RESOLVE_TIMEOUT_MS = 4_500L
private const val MAX_LASTFM_RESOLVE = 18

private data class SourcedTrack(
    val entry: ScrobbleChartEntry,
    val source: String,
    val weight: Int,
    val genreTag: String? = null,
    val primaryGenre: Boolean = false,
)

private data class MergedTrack(
    var entry: ScrobbleChartEntry,
    var weight: Int,
    val sources: MutableSet<String> = linkedSetOf(),
    val tags: MutableSet<String> = linkedSetOf(),
    var primaryGenre: Boolean = false,
)

/** LastWave's seven-source recommendation pipeline adapted to Mylo's Song and YT Music APIs. */
internal object ScrobbleRecommendationEngine {
    private val apiLimiter = Semaphore(4)
    private val searchLimiter = Semaphore(3)

    suspend fun generate(
        profile: ScrobbleStatsSnapshot?,
        seeds: List<Song>,
        heardSongs: List<Song>,
        total: Int,
        onProgress: (String) -> Unit = {},
    ): List<Song> = withTimeout(75_000L) {
        if (total <= 0) return@withTimeout emptyList()
        coroutineScope {
            val localRadio = async { ScrobbleLocalTasteEngine.discover(total) }
            onProgress("Building Last.fm discovery sources…")
            val sourced = if (profile == null) emptyList() else fetchSources(profile, seeds)
            onProgress("Matching recommendations to YouTube Music…")
            val resolved = resolvePlayable(mergeSources(sourced).take(MAX_LASTFM_RESOLVE), onProgress)
            val familiarKeys = ScrobblePlaylistStore.playlists.value
                .flatMap { playlist -> playlist.tracks.map { it.scrobbleIdentityKey } }.toSet()

            val remoteCandidates = resolved.map { (song, merged) ->
                val fresh = isScrobbleFreshCandidate(song, heardSongs, profile?.tracks.orEmpty())
                val familiar = song.scrobbleIdentityKey in familiarKeys
                ScrobbleRecommendationCandidate(
                    song = song, tags = merged.tags, fresh = fresh, familiar = familiar,
                    score = score(song, merged, profile, seeds, fresh, familiar),
                )
            }
            val localSongs = localRadio.await()
            val localCandidates = localSongs.map { song ->
                val fresh = isScrobbleFreshCandidate(song, heardSongs, profile?.tracks.orEmpty())
                val familiar = song.scrobbleIdentityKey in familiarKeys
                ScrobbleRecommendationCandidate(
                    song = song, tags = setOf("source:youtube-radio"), fresh = fresh,
                    familiar = familiar, score = scoreLocal(song, profile, seeds, fresh, familiar),
                )
            }
            onProgress("Applying freshness and diversity…")
            val selected = ScrobbleRecommendationSelection.select(remoteCandidates + localCandidates, total)
            val selectedKeys = selected.mapTo(HashSet()) { it.scrobbleIdentityKey }
            val fill = localSongs.filter { it.scrobbleIdentityKey !in selectedKeys }
            (selected + fill).distinctScrobbleSongs().take(total)
        }
    }

    private suspend fun fetchSources(profile: ScrobbleStatsSnapshot, seeds: List<Song>): List<SourcedTrack> =
        coroutineScope {
            // 1. Similar tracks.
            val trackSeeds = buildList {
                profile.tracks.take(2).forEach { row ->
                    row.artist?.takeIf(String::isNotBlank)?.let { add(row.name to it) }
                }
                seeds.firstOrNull()?.let { add(it.title to it.artist) }
            }.distinctBy {
                it.first.normalizeScrobbleText().lowercase(Locale.ROOT) + "|" +
                    it.second.normalizeScrobbleText().lowercase(Locale.ROOT)
            }
            val similarTracks = parallel(trackSeeds.take(3)) { (track, artist) ->
                fetchTrackSource("track.getsimilar", mapOf("track" to track, "artist" to artist),
                    "similartracks", "track", "similar-track", 3)
            }.flatten()

            // 2. Similar artists and 3. their top tracks.
            val similarArtists = parallel(profile.artists.take(2)) { fetchSimilarArtists(it.name) }
                .flatten().distinct().take(6)
            val artistTopTracks = parallel(similarArtists.take(2)) { artist ->
                fetchTrackSource("artist.gettoptracks", mapOf("artist" to artist),
                    "toptracks", "track", "similar-artist-top-tracks", 2)
            }.flatten()

            // 4. User tags and 5. neighbouring genre tags.
            val primaryTags = profile.tags.take(2)
            val tagTracks = parallel(primaryTags) { tag ->
                fetchTrackSource("tag.gettoptracks", mapOf("tag" to tag.name),
                    "tracks", "track", "user-tag", 3, tag.name, tag == primaryTags.firstOrNull())
            }.flatten()
            val neighbours = primaryTags.firstOrNull()?.let { neighbourTagsFor(it.name) }.orEmpty().take(1)
            val neighbourTracks = parallel(neighbours) { tag ->
                fetchTrackSource("tag.gettoptracks", mapOf("tag" to tag),
                    "tracks", "track", "neighbour-tag", 1, tag)
            }.flatten()

            // 6. Second-wave related artists.
            val relatedNames = similarArtists.firstOrNull()?.let { fetchSimilarArtists(it) }.orEmpty().take(1)
            val relatedTracks = parallel(relatedNames) { artist ->
                fetchTrackSource("artist.gettoptracks", mapOf("artist" to artist),
                    "toptracks", "track", "related-artist", 2)
            }.flatten()

            // 7. Broad chart as a low-weight refill source.
            val discovery = fetchTrackSource("chart.gettoptracks", emptyMap(),
                "tracks", "track", "discovery-chart", 1)
            similarTracks + artistTopTracks + tagTracks + neighbourTracks + relatedTracks + discovery
        }

    private suspend fun fetchSimilarArtists(artist: String): List<String> {
        val response = safeApi("artist.getsimilar", mapOf("artist" to artist))
        return response?.rows("similarartists", "artist").orEmpty()
            .mapNotNull { ScrobbleStatsRepository.parseArtist(it)?.name }
            .filter(String::isNotBlank).distinct()
    }

    private suspend fun fetchTrackSource(
        method: String, params: Map<String, String>, parent: String, child: String,
        source: String, weight: Int, genreTag: String? = null, primaryGenre: Boolean = false,
    ): List<SourcedTrack> {
        val response = safeApi(method, params)
        return response?.rows(parent, child).orEmpty().mapNotNull { element ->
            ScrobbleStatsRepository.parseTrack(element)?.let {
                SourcedTrack(it, source, weight, genreTag, primaryGenre)
            }
        }
    }

    private suspend fun safeApi(method: String, params: Map<String, String>): JsonObject? = try {
        withTimeoutOrNull(LASTFM_SOURCE_TIMEOUT_MS) {
            apiLimiter.withPermit { LastFM.apiCall(method, params + ("limit" to "25")) }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private fun JsonObject.rows(parent: String, child: String): List<JsonElement> =
        (this[parent] as? JsonObject)?.get(child).asElementList()

    private fun JsonElement?.asElementList(): List<JsonElement> = when (this) {
        is JsonArray -> toList()
        is JsonObject -> listOf(this)
        else -> emptyList()
    }

    private fun mergeSources(sources: List<SourcedTrack>): List<MergedTrack> {
        val merged = LinkedHashMap<String, MergedTrack>()
        for (source in sources) {
            val artist = source.entry.artist.orEmpty()
            if (source.entry.name.isBlank() || artist.isBlank()) continue
            val key = source.entry.name.normalizeScrobbleText().lowercase(Locale.ROOT) + "|" +
                artist.normalizeScrobbleText().lowercase(Locale.ROOT)
            val current = merged[key]
            if (current == null) {
                val tags = linkedSetOf("source:" + source.source)
                source.genreTag?.let { tags += "tag:" + it.lowercase(Locale.ROOT) }
                merged[key] = MergedTrack(source.entry, source.weight, linkedSetOf(source.source),
                    tags, source.primaryGenre)
            } else {
                current.weight = maxOf(current.weight, source.weight)
                current.sources += source.source
                current.tags += "source:" + source.source
                source.genreTag?.let { current.tags += "tag:" + it.lowercase(Locale.ROOT) }
                current.primaryGenre = current.primaryGenre || source.primaryGenre
                current.entry = current.entry.copy(
                    playCount = maxOf(current.entry.playCount, source.entry.playCount),
                    listeners = listOfNotNull(current.entry.listeners, source.entry.listeners).maxOrNull(),
                    match = listOfNotNull(current.entry.match, source.entry.match).maxOrNull(),
                    artworkUrl = current.entry.artworkUrl ?: source.entry.artworkUrl,
                )
            }
        }
        return merged.values.sortedByDescending {
            it.weight * 10 + it.sources.size * 4 + (it.entry.match ?: 0.0) * 10
        }
    }

    private suspend fun resolvePlayable(
        candidates: List<MergedTrack>, onProgress: (String) -> Unit,
    ): List<Pair<Song, MergedTrack>> = coroutineScope {
        val completed = AtomicInteger(0)
        candidates.map { candidate ->
            async {
                searchLimiter.withPermit {
                    val song = withTimeoutOrNull(YOUTUBE_RESOLVE_TIMEOUT_MS) { resolveSong(candidate.entry) }
                    val done = completed.incrementAndGet()
                    if (done % 5 == 0) onProgress("Matching tracks " + done + "/" + candidates.size)
                    song?.let { it to candidate }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun resolveSong(entry: ScrobbleChartEntry): Song? {
        val artist = entry.artist?.takeIf(String::isNotBlank) ?: return null
        val results = try {
            YtMusicRepository.search(artist + " " + entry.name, SearchFilter.SONGS).getOrDefault(emptyList())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }
        val songs = results.mapNotNull {
            when (it) {
                is SearchResult.Track -> it.song
                is SearchResult.TopTrack -> it.song
                else -> null
            }
        }
        val titleKey = entry.name.normalizeScrobbleText().lowercase(Locale.ROOT)
        val artistKey = artist.normalizeScrobbleText().lowercase(Locale.ROOT)
        return songs.firstOrNull {
            it.title.normalizeScrobbleText().lowercase(Locale.ROOT) == titleKey &&
                it.artist.normalizeScrobbleText().lowercase(Locale.ROOT) == artistKey
        } ?: songs.firstOrNull {
            it.title.normalizeScrobbleText().lowercase(Locale.ROOT) == titleKey &&
                (it.artist.normalizeScrobbleText().lowercase(Locale.ROOT).contains(artistKey) ||
                    artistKey.contains(it.artist.normalizeScrobbleText().lowercase(Locale.ROOT)))
        }
    }

    private fun score(
        song: Song, candidate: MergedTrack, profile: ScrobbleStatsSnapshot?,
        seeds: List<Song>, fresh: Boolean, familiar: Boolean,
    ): Double {
        val artistKey = song.artist.normalizeScrobbleText().lowercase(Locale.ROOT)
        val topArtist = profile?.artists?.firstOrNull {
            it.name.normalizeScrobbleText().lowercase(Locale.ROOT) == artistKey
        }
        val maxPlays = profile?.artists?.maxOfOrNull { it.playCount }?.coerceAtLeast(1L) ?: 1L
        val affinity = topArtist?.playCount?.toDouble()?.div(maxPlays) ?: 0.0
        val recentArtist = seeds.any {
            it.artist.normalizeScrobbleText().lowercase(Locale.ROOT) == artistKey
        }
        var value = when (candidate.weight) { 4 -> 50.0; 3 -> 38.0; 2 -> 26.0; else -> 12.0 }
        if (topArtist != null) value += 16.0 + affinity * 8.0
        if (recentArtist) value += 8.0
        if (candidate.primaryGenre) value += 10.0
        if (candidate.sources.size >= 3) value += 20.0 else if (candidate.sources.size >= 2) value += 10.0
        value += (candidate.entry.match ?: 0.0) * 34.0
        value += when {
            (candidate.entry.listeners ?: 0L) >= 10_000L -> 12.0
            (candidate.entry.listeners ?: 0L) >= 1_000L -> 6.0
            else -> 0.0
        }
        value += when {
            candidate.entry.playCount >= 100_000L -> 10.0
            candidate.entry.playCount >= 10_000L -> 5.0
            else -> 0.0
        }
        if (fresh) value += 6.0 else value -= 20.0
        if (familiar) value -= 22.0
        return value + Random.nextDouble(0.0, 2.0)
    }

    private fun scoreLocal(
        song: Song, profile: ScrobbleStatsSnapshot?, seeds: List<Song>,
        fresh: Boolean, familiar: Boolean,
    ): Double {
        val artistKey = song.artist.normalizeScrobbleText().lowercase(Locale.ROOT)
        val known = profile?.artists?.any {
            it.name.normalizeScrobbleText().lowercase(Locale.ROOT) == artistKey
        } == true
        var value = 48.0
        if (known) value += 16.0
        if (seeds.any { it.artist.normalizeScrobbleText().lowercase(Locale.ROOT) == artistKey }) value += 8.0
        if (fresh) value += 6.0
        if (familiar) value -= 22.0
        return value + Random.nextDouble(0.0, 2.0)
    }

    private suspend fun <T, R> parallel(items: List<T>, block: suspend (T) -> R): List<R> =
        coroutineScope { items.map { async { block(it) } }.awaitAll() }

    private fun neighbourTagsFor(tag: String): List<String> = when (tag.lowercase(Locale.ROOT)) {
        "rock" -> listOf("alternative rock", "indie rock", "classic rock")
        "pop" -> listOf("indie pop", "synthpop", "dance pop")
        "hip hop", "hip-hop" -> listOf("rap", "alternative hip hop", "trap")
        "electronic" -> listOf("house", "techno", "ambient")
        "indie" -> listOf("indie rock", "indie pop", "alternative")
        "metal" -> listOf("alternative metal", "heavy metal", "metalcore")
        "jazz" -> listOf("cool jazz", "bebop", "smooth jazz")
        else -> listOf("alternative", "indie")
    }
}

internal fun isScrobbleFreshCandidate(
    song: Song, heardSongs: List<Song>, heardCharts: List<ScrobbleChartEntry>,
): Boolean {
    val key = song.scrobbleIdentityKey
    val base = recommendationBaseTrackKey(song.title, song.artist)
    if (heardSongs.any {
            (it.videoId.isNotBlank() && it.videoId == song.videoId) ||
                it.scrobbleIdentityKey == key ||
                recommendationBaseTrackKey(it.title, it.artist) == base
        }
    ) return false
    return heardCharts.none {
        recommendationBaseTrackKey(it.name, it.artist.orEmpty()) == base
    }
}

private val recommendationEditSuffix = Regex(
    """\s*[\(\[\-]?\s*(official video|official audio|lyrics?|lyric video|audio|remastered|remaster|live version|live|radio edit|extended mix)\s*[\)\]]?\s*$""",
    RegexOption.IGNORE_CASE,
)

private fun recommendationBaseTrackKey(title: String, artist: String): String {
    var clean = title.normalizeScrobbleText()
    var previous: String
    do {
        previous = clean
        clean = clean.replace(recommendationEditSuffix, "").trim()
    } while (clean != previous)
    return clean.lowercase(Locale.ROOT) + "|" + artist.normalizeScrobbleText().lowercase(Locale.ROOT)
}
