package com.music.bitchord.data.generate

import android.content.Context
import com.music.bitchord.auth.SignedIn
import com.music.bitchord.data.ListeningHistory
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

/** The nine generator modes, mapped onto Mylo's existing data sources. */
enum class PlaylistGenerationMode(val storageValue: String) {
    TOP("top"),
    RECENT("recent"),
    SIMILAR_TRACKS("similar-tracks"),
    SIMILAR_ARTISTS("similar-artists"),
    TAG("tag"),
    MIX("mix"),
    RECOMMENDATIONS("recommendations"),
    NEVER_HEARD("never-heard"),
    LIBRARY("library"),
}

data class PlaylistGenerationRequest(
    val mode: PlaylistGenerationMode,
    val count: Int = 25,
    val period: ReplayPeriod = ReplayPeriod.ALL_TIME,
    val seedTrack: Song? = null,
    val seedArtist: String = "",
    val tag: String = "",
    /** Explicit opt-ins; these are never silently applied to Never Heard. */
    val excludeSavedPlaylists: Boolean = false,
    val excludeLiked: Boolean = false,
    val excludeLibrary: Boolean = false,
)

/**
 * Builds real Mylo Song rows. It deliberately owns no playlist or player writes:
 * the caller can cancel freely, then perform one synchronous save only after
 * this suspend function has returned and its coroutine is still active.
 */
class PlaylistGenerationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val similarTrackSearchGate = Semaphore(5)

    suspend fun generate(
        request: PlaylistGenerationRequest,
        onProgress: (String) -> Unit = {},
    ): List<Song> {
        val target = request.count.coerceIn(MIN_TRACKS, MAX_TRACKS)
        val raw = when (request.mode) {
            PlaylistGenerationMode.TOP -> {
                onProgress("Reading your Mylo Replay…")
                ListeningStats.summary(request.period).songs.map { it.song }
            }
            PlaylistGenerationMode.RECENT -> {
                onProgress("Reading your recent listening…")
                withContext(Dispatchers.IO) {
                    ListeningHistory.read(appContext).map { it.song }
                }
            }
            PlaylistGenerationMode.SIMILAR_TRACKS -> {
                val seed = request.seedTrack
                    ?: throw IllegalArgumentException("Choose a track first")
                require(PlaylistGenerationRules.canStartMixWithSong(seed)) {
                    "This track cannot start a mix. Choose a playable YouTube Music song."
                }
                similarTrackCandidates(seed, target, onProgress)
            }
            PlaylistGenerationMode.SIMILAR_ARTISTS -> {
                val artist = request.seedArtist.trim()
                require(artist.isNotEmpty()) { "Enter an artist name" }
                onProgress("Finding artists similar to $artist…")

                // LastWave's source is artist.getsimilar -> each similar artist's top tracks.
                // Mylo resolves those names through its existing YouTube Music artist pages,
                // so every returned row is already playable and retains Mylo's Song model.
                val similarArtists = if (LastFM.isInitialized()) {
                    LastFM.getSimilarArtists(artist, limit = 20).getOrNull().orEmpty().take(8)
                } else {
                    emptyList()
                }
                val artistNames = similarArtists.ifEmpty { listOf(artist) }
                onProgress("Loading tracks from ${artistNames.size} artists…")
                val tracks = PlaylistGenerationRules.interleave(artistPagePools(artistNames))
                if (tracks.isNotEmpty()) tracks else {
                    // A failed Last.fm graph or unresolvable similar artists must not
                    // break the old exact-artist path.
                    val exact = artistPagePools(listOf(artist)).flatten()
                    exact.ifEmpty { throw IllegalStateException("Couldn't find that artist or similar artists") }
                }
            }
            PlaylistGenerationMode.TAG -> {
                val tag = request.tag.trim()
                require(tag.isNotEmpty()) { "Enter a mood or genre" }
                onProgress("Finding music for $tag…")
                val category = YtMusicRepository.moodAndGenres().getOrNull().orEmpty()
                    .asSequence()
                    .flatMap { it.items.asSequence() }
                    .firstOrNull { it.title.equals(tag, ignoreCase = true) }
                val categorySongs = category?.let { mood ->
                    YtMusicRepository.moodGenreShelves(mood.browseId, mood.params).getOrThrow()
                        .flatMap { shelf -> shelf.items.mapNotNull(::shelfItemSong) }
                }.orEmpty()
                if (categorySongs.isNotEmpty()) categorySongs else {
                    YtMusicRepository.search(tag, SearchFilter.SONGS).getOrThrow()
                        .mapNotNull { result ->
                            when (result) {
                                is SearchResult.TopTrack -> result.song
                                is SearchResult.Track -> result.song
                                is SearchResult.Browse -> null
                            }
                        }
                }
            }
            PlaylistGenerationMode.MIX -> {
                onProgress("Building a mix from your listening…")
                val signals = loadTasteSignals()
                val seeds = (
                    listOfNotNull(request.seedTrack) +
                        signals.lastFmSeeds.take(4) +
                        signals.replaySongs.take(4) +
                        signals.recentSongs.take(4) +
                        signals.accountHistory.take(3) +
                        signals.likedSongs.take(3)
                    )
                    .filter { it.videoId.isNotBlank() && !it.isVideo }
                    .distinctBy { it.videoId }
                    .take(6)

                onProgress("Finding radio from your listening…")
                val discovery = radioFromSeeds(seeds)
                onProgress("Blending recommendations and your library…")
                val remote = supervisorScope {
                    val picks = async { YtMusicRepository.quickPicks().getOrNull().orEmpty() }
                    val home = async { homeSongs() }
                    picks.await() to home.await()
                }
                PlaylistGenerationRules.weightedBlend(
                    pools = listOf(
                        (discovery + remote.first) to 4,
                        (signals.likedSongs + signals.accountHistory + signals.lastFmSeeds + signals.recentSongs + signals.replaySongs) to 3,
                        remote.second to 1,
                    ),
                    limit = target,
                    maxPerArtist = 3,
                )
            }
            PlaylistGenerationMode.RECOMMENDATIONS -> {
                onProgress("Building recommendations from your listening…")
                val signals = loadTasteSignals()
                val seeds = (
                    signals.lastFmSeeds.take(6) +
                        signals.replaySongs.take(6) +
                        signals.recentSongs.take(6) +
                        signals.accountHistory.take(4) +
                        signals.likedSongs.take(4)
                    )
                    .filter { it.videoId.isNotBlank() && !it.isVideo }
                    .distinctBy { it.videoId }
                    .take(6)

                onProgress("Finding tracks related to your taste…")
                val discovery = radioFromSeeds(seeds)
                val remote = supervisorScope {
                    val picks = async { YtMusicRepository.quickPicks().getOrNull().orEmpty() }
                    val home = async { homeSongs() }
                    picks.await() to home.await()
                }
                val candidates = PlaylistGenerationRules.weightedBlend(
                    pools = listOf(
                        (discovery + remote.first) to 4,
                        remote.second to 1,
                    ),
                    limit = target,
                    maxPerArtist = 2,
                )
                val heard = signals.lastFmSeeds + signals.replaySongs + signals.recentSongs +
                    signals.accountHistory + signals.likedSongs
                val fresh = PlaylistGenerationRules.excludeHeard(
                    candidates = candidates,
                    heardVideoIds = heard.mapTo(HashSet()) { it.videoId },
                    heardTrackKeys = heard.mapTo(HashSet(), PlaylistGenerationRules::trackKey),
                )
                // Keep the recommendation mode useful when a small account has
                // too little unseen radio; Never Heard remains the strict mode.
                if (fresh.size >= MIN_TRACKS) fresh else candidates
            }
            PlaylistGenerationMode.NEVER_HEARD -> neverHeardCandidates(onProgress)
            PlaylistGenerationMode.LIBRARY -> {
                onProgress("Reading your YouTube Music library…")
                val library = YtMusicRepository.library().getOrThrow()
                library.likedSongs + library.librarySongs
            }
        }

        currentCoroutineContext().ensureActive()
        var candidates = PlaylistGenerationRules.deduplicate(raw)

        // NEVER_HEARD candidates were filtered against local and account history
        // before this point. Do not fetch the same histories a second time here.

        val libraryPage = if (request.excludeLiked || request.excludeLibrary) {
            onProgress("Applying your selected library exclusions…")
            YtMusicRepository.library().getOrThrow()
        } else {
            null
        }
        val explicitExclusions = buildList {
            if (request.excludeSavedPlaylists) {
                addAll(LocalPlaylistStore.playlists.value.flatMap { it.songs })
            }
            if (request.excludeLiked) addAll(libraryPage?.likedSongs.orEmpty())
            if (request.excludeLibrary) addAll(libraryPage?.librarySongs.orEmpty())
        }
        if (explicitExclusions.isNotEmpty()) {
            candidates = PlaylistGenerationRules.excludeHeard(
                candidates = candidates,
                heardVideoIds = explicitExclusions.mapTo(HashSet()) { it.videoId },
                heardTrackKeys = explicitExclusions.mapTo(HashSet(), PlaylistGenerationRules::trackKey),
            )
        }

        currentCoroutineContext().ensureActive()
        // Never Heard must not silently bias against saved playlists. It only
        // excludes them when the explicit excludeSavedPlaylists option is set.
        val savedSongs = if (request.mode == PlaylistGenerationMode.NEVER_HEARD) {
            emptyList()
        } else {
            LocalPlaylistStore.playlists.value.flatMap { it.songs }
        }
        val result = PlaylistGenerationRules.prioritizeFresh(
            candidates = candidates,
            savedVideoIds = savedSongs.mapTo(HashSet()) { it.videoId },
            savedTrackKeys = savedSongs.mapTo(HashSet(), PlaylistGenerationRules::trackKey),
            limit = target,
        )
        if (result.isEmpty()) {
            throw IllegalStateException("No tracks found for this generation. Try another mode or seed.")
        }
        return result
    }

    /**
     * LastWave's playlist-taste regeneration, adapted to Mylo's Song and
     * YouTube Music sources. The source playlist is never edited or repeated.
     */
    suspend fun generateSimilar(
        sourceSongs: List<Song>,
        count: Int = 30,
        onProgress: (String) -> Unit = {},
    ): List<Song> {
        val target = count.coerceIn(MIN_TRACKS, MAX_TRACKS)
        val minSize = minOf(MIN_SIMILAR_MIX_SIZE, target)
        val sourceTracks = PlaylistGenerationRules.deduplicate(sourceSongs)
        require(sourceTracks.isNotEmpty()) { "This playlist has no playable tracks to use as seeds." }

        val sourceVideoIds = sourceSongs.mapTo(HashSet()) { it.videoId }
        val sourceTrackKeys = sourceSongs.mapTo(HashSet(), PlaylistGenerationRules::trackKey)
        val sourceArtists = sourceTracks.map { it.artist.trim() }
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(3)
        val seedTracks = sourceTracks.filter { it.title.isNotBlank() && it.artist.isNotBlank() }.take(4)
        onProgress("Finding artists and tracks related to this playlist…")

        val similarArtistNames = if (LastFM.isInitialized()) {
            supervisorScope {
                sourceArtists.map { artist ->
                    async {
                        LastFM.getSimilarArtists(artist, limit = 12).getOrNull().orEmpty().take(2)
                    }
                }.awaitAll().flatten().distinctBy { it.lowercase(Locale.ROOT) }
            }
        } else {
            emptyList()
        }

        onProgress("Building related-song radio and artist pools…")
        val similarArtistTracks = PlaylistGenerationRules.interleave(artistPagePools(similarArtistNames))
        val directArtistTracks = PlaylistGenerationRules.interleave(artistPagePools(sourceArtists))
        val radioTracks = radioFromSeeds(seedTracks.take(3))
        val basePools = listOf(
            radioTracks to 4,
            similarArtistTracks to 3,
            directArtistTracks to 2,
        )
        fun curate(pools: List<Pair<List<Song>, Int>>): List<Song> =
            PlaylistGenerationRules.weightedBlend(
                pools = pools.map { (tracks, weight) ->
                    PlaylistGenerationRules.excludeHeard(
                        tracks,
                        heardVideoIds = sourceVideoIds,
                        heardTrackKeys = sourceTrackKeys,
                    ) to weight
                },
                limit = target,
                maxPerArtist = 3,
            )

        var candidates = curate(basePools)
        var fallbackTracks = emptyList<Song>()
        if (candidates.size < minSize) {
            onProgress("Widening the mix with YouTube Music recommendations…")
            val remote = supervisorScope {
                val picks = async { YtMusicRepository.quickPicks().getOrNull().orEmpty() }
                val home = async { homeSongs() }
                picks.await() + home.await()
            }
            fallbackTracks = remote
            candidates = curate(basePools + (remote to 1))
        }

        if (candidates.size < minSize && LastFM.isInitialized()) {
            onProgress("Exploring more similar artists…")
            val extraNames = supervisorScope {
                sourceArtists.take(2).map { artist ->
                    async {
                        LastFM.getSimilarArtists(artist, limit = 20).getOrNull().orEmpty().drop(2).take(3)
                    }
                }.awaitAll().flatten().distinctBy { it.lowercase(Locale.ROOT) }
            }
            val extraTracks = PlaylistGenerationRules.interleave(artistPagePools(extraNames))
            candidates = curate(basePools + (extraTracks to 2) + (fallbackTracks to 1))
        }

        if (candidates.size < minSize) {
            onProgress("Adding a wider mood and genre discovery pool…")
            val moods = YtMusicRepository.moodAndGenres().getOrNull().orEmpty()
                .flatMap { it.items }
                .shuffled()
                .take(3)
            val moodTracks = supervisorScope {
                moods.map { mood ->
                    async {
                        YtMusicRepository.moodGenreShelves(mood.browseId, mood.params).getOrNull().orEmpty()
                            .flatMap { shelf -> shelf.items.mapNotNull(::shelfItemSong) }
                    }
                }.awaitAll().flatten()
            }
            candidates = curate(basePools + (moodTracks to 1) + (fallbackTracks to 1))
        }

        currentCoroutineContext().ensureActive()
        val savedSongs = LocalPlaylistStore.playlists.value.flatMap { it.songs }
        val result = PlaylistGenerationRules.prioritizeFresh(
            candidates = candidates,
            savedVideoIds = savedSongs.mapTo(HashSet()) { it.videoId },
            savedTrackKeys = savedSongs.mapTo(HashSet(), PlaylistGenerationRules::trackKey),
            limit = target,
        )
        if (result.size < minSize) {
            throw IllegalStateException(
                "Not enough new tracks found to build a ${minSize}-track mix. Try another source playlist.",
            )
        }
        return result
    }

    private data class TasteSignals(
        val lastFmSeeds: List<Song>,
        val replaySongs: List<Song>,
        val recentSongs: List<Song>,
        val accountHistory: List<Song>,
        val likedSongs: List<Song>,
    )

    private suspend fun loadTasteSignals(): TasteSignals {
        val lastFmSeeds = resolveLastFmTasteSeeds()
        val replay = try {
            ListeningStats.summary(ReplayPeriod.ALL_TIME).songs.take(12).map { it.song }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }
        val recent = try {
            withContext(Dispatchers.IO) { ListeningHistory.read(appContext).map { it.song }.take(30) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }
        val (history, liked) = if (SignedIn.state(appContext).value) {
            supervisorScope {
                val accountHistory = async { YtMusicRepository.history().getOrNull().orEmpty() }
                val accountLiked = async { YtMusicRepository.library().getOrNull()?.likedSongs.orEmpty() }
                accountHistory.await() to accountLiked.await()
            }
        } else {
            emptyList<Song>() to emptyList()
        }
        return TasteSignals(lastFmSeeds, replay, recent, history, liked)
    }

    private suspend fun resolveLastFmTasteSeeds(): List<Song> {
        val username = AppSettings.lastfmUsername.value.trim()
        if (username.isEmpty() || !LastFM.isInitialized()) return emptyList()
        val topTracks = LastFM.getTopTracks(username, period = "overall", limit = 20)
            .getOrNull().orEmpty().take(6)
        return supervisorScope {
            topTracks.map { track ->
                async {
                    similarTrackSearchGate.withPermit {
                        try {
                            val expected = PlaylistGenerationRules.trackKey(
                                Song(videoId = "", title = track.name, artist = track.artist, thumbnailUrl = track.artworkUrl),
                            )
                            YtMusicRepository.search("${track.name} ${track.artist}", SearchFilter.SONGS)
                                .getOrNull().orEmpty()
                                .mapNotNull { result ->
                                    when (result) {
                                        is SearchResult.TopTrack -> result.song
                                        is SearchResult.Track -> result.song
                                        is SearchResult.Browse -> null
                                    }
                                }
                                .firstOrNull { PlaylistGenerationRules.trackKey(it) == expected }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * LastWave's one-song radio flow: expand the tapped seed through YouTube
     * Music radio, branch once from a related result when the first pool is
     * thin, then use Last.fm similar tracks as a secondary source. Optional
     * sources are isolated so one failed request cannot discard other results.
     */
    private suspend fun similarTrackCandidates(
        seed: Song,
        target: Int,
        onProgress: (String) -> Unit,
    ): List<Song> = supervisorScope {
        onProgress("Finding tracks related to ${seed.title}…")
        val primary = radioFromSeeds(listOf(seed))
        val branchSeed = primary.firstOrNull { it.videoId != seed.videoId && !it.isVideo }
        val branch = if (primary.size < 40 && branchSeed != null) {
            onProgress("Expanding discovery from related tracks…")
            radioFromSeeds(listOf(branchSeed))
        } else {
            emptyList()
        }
        val radioTracks = PlaylistGenerationRules.deduplicate(primary + branch)
        val lastFmTracks = if (radioTracks.size < target && LastFM.isInitialized()) {
            onProgress("Looking for more similar tracks…")
            resolveLastFmSimilarTracks(seed, target)
        } else {
            emptyList()
        }
        val curated = PlaylistGenerationRules.curateSimilarTracks(
            candidates = radioTracks + lastFmTracks,
            seed = seed,
            limit = target,
        )
        if (curated.isEmpty()) {
            throw IllegalStateException("No similar tracks found for \"${seed.title}\". Try another song.")
        }
        curated
    }

    /** Resolve Last.fm names only when YouTube Music confirms the same title and artist. */
    private suspend fun resolveLastFmSimilarTracks(seed: Song, target: Int): List<Song> {
        val similar = LastFM.getSimilarTracks(
            track = seed.title,
            artist = seed.artist,
            limit = minOf(100, target * 3),
        ).getOrNull().orEmpty()
        if (similar.isEmpty()) return emptyList()
        return supervisorScope {
            similar.map { track ->
                async {
                    try {
                        val expected = PlaylistGenerationRules.trackKey(
                            Song(videoId = "", title = track.name, artist = track.artist, thumbnailUrl = track.artworkUrl),
                        )
                        YtMusicRepository.search("${track.name} ${track.artist}", SearchFilter.SONGS)
                            .getOrNull().orEmpty()
                            .mapNotNull { result ->
                                when (result) {
                                    is SearchResult.TopTrack -> result.song
                                    is SearchResult.Track -> result.song
                                    is SearchResult.Browse -> null
                                }
                            }
                            .firstOrNull { PlaylistGenerationRules.trackKey(it) == expected }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun artistPagePools(artists: List<String>): List<List<Song>> = supervisorScope {
        artists.distinctBy { it.trim().lowercase(Locale.ROOT) }.map { artist ->
            async {
                try {
                    val browseId = YtMusicRepository.findArtistPageId(artist).getOrNull()
                        ?: return@async emptyList()
                    YtMusicRepository.artistPage(browseId).getOrNull()?.songs.orEmpty()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }.awaitAll()
    }

    private suspend fun radioFromSeeds(seeds: List<Song>): List<Song> {
        val distinctSeeds = seeds.asSequence()
            .filter { it.videoId.isNotBlank() && !it.isVideo }
            .distinctBy { it.videoId }
            .take(6)
            .toList()
        val pools = supervisorScope {
            distinctSeeds.map { seed ->
                async {
                    try {
                        YtMusicRepository.radio(seed.videoId).getOrNull().orEmpty()
                            .filterNot { it.videoId == seed.videoId }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll()
        }
        return PlaylistGenerationRules.interleave(pools)
    }

    private suspend fun neverHeardCandidates(onProgress: (String) -> Unit): List<Song> {
        onProgress("Checking your listening history…")
        val heard = heardTracks()
        val heardIds = heard.mapTo(HashSet()) { it.videoId }
        val heardKeys = heard.mapTo(HashSet(), PlaylistGenerationRules::trackKey)
        currentCoroutineContext().ensureActive()
        onProgress("Finding music you haven't played…")
        val quickPicks = YtMusicRepository.quickPicks(heardIds).getOrNull().orEmpty()
        val home = homeSongs()
        return PlaylistGenerationRules.excludeHeard(
            candidates = quickPicks + home,
            heardVideoIds = heardIds,
            heardTrackKeys = heardKeys,
        )
    }

    /**
     * The local event log includes two-second skips; Replay adds counted plays.
     * When signed in, include the account's YouTube Music history as well.
     */
    private suspend fun heardTracks(): List<Song> {
        val local = withContext(Dispatchers.IO) {
            ListeningHistory.read(appContext).map { it.song }
        }
        val replay = ListeningStats.summary(ReplayPeriod.ALL_TIME).songs.map { it.song }
        val account = if (SignedIn.state(appContext).value) {
            YtMusicRepository.history().getOrThrow()
        } else {
            emptyList()
        }
        return local + replay + account
    }

    private suspend fun homeSongs(): List<Song> =
        YtMusicRepository.home().getOrNull()?.shelves.orEmpty()
            .flatMap { shelf -> shelf.items.mapNotNull(::shelfItemSong) }

    private fun shelfItemSong(item: com.music.bitchord.data.model.ShelfItem): Song? {
        val id = item.videoId?.takeIf(String::isNotBlank) ?: return null
        return Song(
            videoId = id,
            title = item.title,
            artist = InnertubeParser.artistFromSubtitle(item.subtitle),
            thumbnailUrl = item.thumbnailUrl,
        )
    }

    private companion object {
        const val MIN_TRACKS = 5
        const val MIN_SIMILAR_MIX_SIZE = 20
        const val MAX_TRACKS = 35
    }
}

/** Pure candidate rules, kept separate so identity/exclusion behaviour is unit-testable. */
internal object PlaylistGenerationRules {
    fun interleave(pools: List<List<Song>>): List<Song> {
        val longest = pools.maxOfOrNull { it.size } ?: return emptyList()
        return buildList {
            for (index in 0 until longest) {
                pools.forEach { pool -> pool.getOrNull(index)?.let(::add) }
            }
        }
    }

    fun deduplicate(candidates: List<Song>): List<Song> =
        candidates.asSequence()
            .filter { it.videoId.isNotBlank() && !it.isVideo }
            .distinctBy { it.videoId }
            .toList()

    /**
     * LastWave's weighted mix curation, adapted to Mylo's local Replay and
     * YouTube Music sources. The highest-weight source owns duplicate identity;
     * artist diversity is relaxed only when needed to fill the requested pool.
     */
    fun weightedBlend(
        pools: List<Pair<List<Song>, Int>>,
        limit: Int,
        maxPerArtist: Int = 3,
    ): List<Song> {
        if (limit <= 0) return emptyList()
        val bestByTrack = LinkedHashMap<String, Pair<Song, Int>>()
        pools.forEach { (tracks, weight) ->
            deduplicate(tracks).forEach songLoop@{ song ->
                if (song.title.isBlank() || song.artist.isBlank()) return@songLoop
                val key = trackKey(song)
                val current = bestByTrack[key]
                if (current == null || weight > current.second) {
                    bestByTrack[key] = song to weight
                }
            }
        }
        val ordered = bestByTrack.values.sortedByDescending { it.second }
        val picked = ArrayList<Song>(minOf(limit, ordered.size))
        val pickedKeys = HashSet<String>()
        val artistCounts = HashMap<String, Int>()
        val artistLimit = maxPerArtist.coerceAtLeast(1)

        ordered.forEach candidateLoop@{ (song, _) ->
            if (picked.size >= limit) return@candidateLoop
            val key = trackKey(song)
            val artist = song.artist.trim().lowercase(Locale.ROOT)
                .ifBlank { "unknown:${song.videoId}" }
            if (artistCounts.getOrDefault(artist, 0) >= artistLimit) return@candidateLoop
            if (pickedKeys.add(key)) {
                picked += song
                artistCounts[artist] = artistCounts.getOrDefault(artist, 0) + 1
            }
        }

        // A thin pool should still return the requested count when unique tracks exist.
        if (picked.size < limit) {
            ordered.forEach fillLoop@{ (song, _) ->
                if (picked.size >= limit) return@fillLoop
                if (pickedKeys.add(trackKey(song))) picked += song
            }
        }
        return picked
    }

    fun excludeHeard(
        candidates: List<Song>,
        heardVideoIds: Set<String>,
        heardTrackKeys: Set<String>,
    ): List<Song> = candidates.filterNot { song ->
        song.videoId in heardVideoIds || trackKey(song) in heardTrackKeys
    }

    /** Saved tracks move behind fresh tracks; they are retained if the pool is thin. */
    fun prioritizeFresh(
        candidates: List<Song>,
        savedVideoIds: Set<String>,
        savedTrackKeys: Set<String>,
        limit: Int,
    ): List<Song> {
        val (fresh, saved) = candidates.partition { song ->
            song.videoId !in savedVideoIds && trackKey(song) !in savedTrackKeys
        }
        return (fresh + saved).take(limit.coerceAtLeast(0))
    }

    /** A one-song mix needs a playable, identifiable YouTube Music seed. */
    fun canStartMixWithSong(song: Song): Boolean =
        song.videoId.isNotBlank() &&
            !song.isVideo &&
            song.localUri == null &&
            song.title.isNotBlank() &&
            song.artist.isNotBlank()

    /**
     * Mirrors LastWave's similar-track curation: remove the seed and its edits,
     * keep one result per normalized title, then relax artist limits only when
     * the pool cannot otherwise reach the requested size.
     */
    fun curateSimilarTracks(candidates: List<Song>, seed: Song, limit: Int): List<Song> {
        if (limit <= 0) return emptyList()
        val seedTitle = cleanSimilarTitle(seed.title)
        val distinctTitles = LinkedHashMap<String, Song>()
        deduplicate(candidates).forEach { candidate ->
            val title = cleanSimilarTitle(candidate.title)
            if (title.isBlank() || title == seedTitle || trackKey(candidate) == trackKey(seed)) return@forEach
            if (title !in distinctTitles) distinctTitles[title] = candidate
        }
        val unique = distinctTitles.values.toList()
        fun withArtistCaps(seedArtistLimit: Int, otherArtistLimit: Int): List<Song> {
            val counts = HashMap<String, Int>()
            return unique.filter { song ->
                val artist = normalize(song.artist).ifBlank { "unknown:${song.videoId}" }
                val cap = if (artist == normalize(seed.artist)) seedArtistLimit else otherArtistLimit
                val count = counts.getOrDefault(artist, 0)
                if (count >= cap) false else {
                    counts[artist] = count + 1
                    true
                }
            }
        }
        val firstPass = withArtistCaps(seedArtistLimit = 1, otherArtistLimit = 2)
        if (firstPass.size >= limit) return firstPass.take(limit)
        val relaxed = withArtistCaps(seedArtistLimit = 2, otherArtistLimit = 3)
        return (if (relaxed.size >= limit) relaxed else unique).take(limit)
    }

    private fun cleanSimilarTitle(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("""\s*[\(\[\{].*?[\)\]\}]"""), " ")
            .replace(
                Regex("""(?i)\b(official\s+video|official\s+audio|lyrics?|remix|mix|edit|bootleg|flip|vip|slowed|reverb|sped\s+up|speed\s+up|cover|version|live|acoustic|instrumental|feat\.?|ft\.?)\b.*$"""),
                " ",
            )
            .filter(Char::isLetterOrDigit)

    fun trackKey(song: Song): String =
        "${normalize(song.title)}|${normalize(song.artist)}"

    private fun normalize(value: String): String =
        value.lowercase().filter(Char::isLetterOrDigit)
}
