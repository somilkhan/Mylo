package com.music.bitchord.data.generate

import android.content.Context
import androidx.annotation.StringRes
import com.music.bitchord.R
import com.music.bitchord.auth.SignedIn
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.scrobbling.LastFmStatsPeriod
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylistStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale

/** The generator modes, mapped onto Mylo's existing data sources and Last.fm. */
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
    FORGOTTEN_FAVORITES("forgotten-favorites"),
    TAG_TOP("tag-top"),
    TOP_ARTISTS_MIX("top-artists-mix"),
}

data class PlaylistGenerationRequest(
    val mode: PlaylistGenerationMode,
    val count: Int = 25,
    /** The Last.fm chart window for Top tracks and Top artists mix. */
    val period: LastFmStatsPeriod = LastFmStatsPeriod.OVERALL,
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
        val accounts = GenerationAccountSnapshot(appContext)
        val target = request.count.coerceIn(MIN_TRACKS, MAX_TRACKS)
        val raw = when (request.mode) {
            PlaylistGenerationMode.TOP -> {
                val username = lastFmUsernameOrThrow()
                onProgress(message(R.string.scrobble_gen_progress_top))
                val top = LastFM.getTopTracks(username, period = request.period.apiValue, limit = 100).getOrThrow()
                onProgress(message(R.string.scrobble_gen_progress_resolving))
                resolveLastFmTracks(top.take(LastFmGenerationRules.MAX_RESOLVE_CANDIDATES))
            }
            PlaylistGenerationMode.RECENT -> {
                val username = lastFmUsernameOrThrow()
                onProgress(message(R.string.scrobble_gen_progress_recent))
                val recent = lastFmRecentTracks(username, LastFmGenerationRules.MAX_RESOLVE_CANDIDATES)
                onProgress(message(R.string.scrobble_gen_progress_resolving))
                resolveLastFmTracks(recent)
            }
            PlaylistGenerationMode.SIMILAR_TRACKS -> {
                val seed = request.seedTrack
                    ?: throw GenerationException(R.string.scrobble_gen_error_seed)
                requireGeneration(PlaylistGenerationRules.canStartMixWithSong(seed), R.string.scrobble_gen_error_song_no_mix)
                similarTrackCandidates(seed, target, onProgress)
            }
            PlaylistGenerationMode.SIMILAR_ARTISTS -> {
                val artist = request.seedArtist.trim()
                requireGeneration(artist.isNotEmpty(), R.string.scrobble_gen_error_artist)
                onProgress(message(R.string.scrobble_gen_progress_similar_artist, artist))

                // LastWave's source is artist.getsimilar -> each similar artist's top tracks.
                // Mylo resolves those names through its existing YouTube Music artist pages,
                // so every returned row is already playable and retains Mylo's Song model.
                val similarArtists = if ((LastFM.isInitialized())) {
                    LastFM.getSimilarArtists(artist, limit = 20).getOrNull().orEmpty().take(8)
                } else {
                    emptyList()
                }
                val artistNames = similarArtists.ifEmpty { listOf(artist) }
                onProgress(message(R.string.scrobble_gen_progress_artists_loaded, artistNames.size))
                val tracks = PlaylistGenerationRules.interleave(artistPagePools(artistNames))
                if (tracks.isNotEmpty()) tracks else {
                    // A failed Last.fm graph or unresolvable similar artists must not
                    // break the old exact-artist path.
                    val exact = artistPagePools(listOf(artist)).flatten()
                    exact.ifEmpty { throw GenerationException(R.string.scrobble_gen_error_artist_not_found) }
                }
            }
            PlaylistGenerationMode.TAG -> {
                val tag = request.tag.trim()
                requireGeneration(tag.isNotEmpty(), R.string.scrobble_gen_error_tag)
                onProgress(message(R.string.scrobble_gen_progress_tag, tag))
                tagSongsFromYtMusic(tag)
            }
            PlaylistGenerationMode.TAG_TOP -> {
                val tag = request.tag.trim()
                requireGeneration(tag.isNotEmpty(), R.string.scrobble_gen_error_tag)
                onProgress(message(R.string.scrobble_gen_progress_tag_top, tag))
                requireGeneration(LastFM.isInitialized(), R.string.scrobble_gen_needs_lastfm)
                val lastFmTracks = LastFM.getTagTopTracks(tag, limit = LastFmGenerationRules.TAG_TRACK_LIMIT)
                    .getOrThrow()
                // A Last.fm chart must never silently turn into a different YouTube genre source.
                resolveLastFmTracks(lastFmTracks.take(LastFmGenerationRules.MAX_RESOLVE_CANDIDATES))
            }
            PlaylistGenerationMode.FORGOTTEN_FAVORITES -> {
                onProgress(message(R.string.scrobble_gen_progress_forgotten))
                forgottenFavoriteCandidates(onProgress)
            }
            PlaylistGenerationMode.TOP_ARTISTS_MIX -> {
                onProgress(message(R.string.scrobble_gen_progress_top_artists))
                topArtistsMixCandidates(request.period, onProgress)
            }
            PlaylistGenerationMode.MIX -> {
                onProgress(message(R.string.scrobble_gen_progress_mix))
                val signals = loadTasteSignals()
                val seeds = (
                    listOfNotNull(request.seedTrack) +
                        signals.lastFmSeeds.take(4) +
                        signals.recentSongs.take(4) +
                        signals.accountHistory.take(3) +
                        signals.likedSongs.take(3)
                    )
                    .filter { it.videoId.isNotBlank() && !it.isVideo && it.localUri == null }
                    .distinctBy { it.videoId }
                    .take(6)

                onProgress(message(R.string.scrobble_gen_progress_taste_radio))
                val discovery = radioFromSeeds(seeds)
                onProgress(message(R.string.scrobble_gen_progress_blending))
                val remote = supervisorScope {
                    val picks = async { YtMusicRepository.quickPicks().getOrNull().orEmpty() }
                    val home = async { homeSongs() }
                    picks.await() to home.await()
                }
                PlaylistGenerationRules.weightedBlend(
                    pools = listOf(
                        (discovery + remote.first) to 4,
                        (signals.likedSongs + signals.accountHistory + signals.lastFmSeeds + signals.recentSongs) to 3,
                        remote.second to 1,
                    ),
                    limit = target,
                    maxPerArtist = 3,
                )
            }
            PlaylistGenerationMode.RECOMMENDATIONS -> {
                onProgress(message(R.string.scrobble_gen_progress_recommendations))
                val signals = loadTasteSignals()
                val seeds = (
                    signals.lastFmSeeds.take(6) +
                        signals.recentSongs.take(6) +
                        signals.accountHistory.take(4) +
                        signals.likedSongs.take(4)
                    )
                    .filter { it.videoId.isNotBlank() && !it.isVideo && it.localUri == null }
                    .distinctBy { it.videoId }
                    .take(6)

                onProgress(message(R.string.scrobble_gen_progress_taste_related))
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
                val heard = signals.lastFmSeeds + signals.recentSongs +
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
                onProgress(message(R.string.scrobble_gen_progress_library))
                requireYouTubeAccount()
                supervisorScope {
                    val liked = async { completeLibraryFeed(YtMusicRepository.LIKED_MUSIC) }
                    val added = async { completeLibraryFeed(LIBRARY_SONGS_BROWSE_ID) }
                    liked.await() + added.await()
                }
            }
        }

        currentCoroutineContext().ensureActive()
        var candidates = PlaylistGenerationRules.deduplicate(raw)

        // NEVER_HEARD candidates were filtered against Last.fm and account history
        // before this point. Do not fetch the same histories a second time here.

        val libraryPage = if (request.excludeLiked || request.excludeLibrary) {
            onProgress(message(R.string.scrobble_gen_progress_exclusions))
            requireYouTubeAccount()
            generationExclusionRead {
                supervisorScope {
                    val liked = async {
                        completeLibraryFeed(YtMusicRepository.LIKED_MUSIC)
                    }
                    val added = async {
                        if (request.excludeLibrary) completeLibraryFeed(LIBRARY_SONGS_BROWSE_ID) else emptyList()
                    }
                    val likedSongs = liked.await()
                    val likedIds = likedSongs.mapTo(HashSet()) { it.videoId }
                    // Match LibraryPage: liked tracks are a separate opt-in, not library-only rows.
                    likedSongs to added.await().filterNot { it.videoId in likedIds }
                }
            }
        } else {
            null
        }
        val explicitExclusions = buildList {
            if (request.excludeSavedPlaylists) {
                requireGeneration(
                    LocalPlaylistStore.loadError.value == null,
                    R.string.scrobble_gen_error_exclusions_unavailable,
                )
                addAll(LocalPlaylistStore.playlists.value.flatMap { it.songs })
            }
            if (request.excludeLiked) addAll(libraryPage?.first.orEmpty())
            if (request.excludeLibrary) addAll(libraryPage?.second.orEmpty())
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
        // Last.fm charts/recent modes retain their source order rather than moving saved rows back.
        val savedSongs = if (
            request.mode in setOf(
                PlaylistGenerationMode.NEVER_HEARD, PlaylistGenerationMode.FORGOTTEN_FAVORITES,
                PlaylistGenerationMode.TOP, PlaylistGenerationMode.RECENT,
                PlaylistGenerationMode.TOP_ARTISTS_MIX, PlaylistGenerationMode.TAG_TOP,
            )
        ) {
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
            throw GenerationException(R.string.scrobble_gen_error_no_tracks)
        }
        accounts.ensureUnchanged()
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
        val accounts = GenerationAccountSnapshot(appContext)
        val target = count.coerceIn(MIN_TRACKS, MAX_TRACKS)
        val minSize = minOf(MIN_SIMILAR_MIX_SIZE, target)
        val sourceTracks = PlaylistGenerationRules.deduplicate(sourceSongs)
        requireGeneration(sourceTracks.isNotEmpty(), R.string.scrobble_gen_error_playlist_no_seeds)

        val sourceVideoIds = sourceSongs.mapTo(HashSet()) { it.videoId }
        val sourceTrackKeys = sourceSongs.mapTo(HashSet(), PlaylistGenerationRules::trackKey)
        val sourceArtists = sourceTracks.map { it.artist.trim() }
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(3)
        val seedTracks = sourceTracks.filter { it.title.isNotBlank() && it.artist.isNotBlank() }.take(4)
        onProgress(message(R.string.scrobble_gen_progress_playlist_related))

        val similarArtistNames = if ((LastFM.isInitialized())) {
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

        onProgress(message(R.string.scrobble_gen_progress_related_pools))
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

        val widenedPools = basePools.toMutableList()
        var candidates = curate(widenedPools)
        if (candidates.size < minSize) {
            onProgress(message(R.string.scrobble_gen_progress_widening))
            val remote = supervisorScope {
                val picks = async { YtMusicRepository.quickPicks().getOrNull().orEmpty() }
                val home = async { homeSongs() }
                picks.await() + home.await()
            }
            widenedPools += remote to 1
            candidates = curate(widenedPools)
        }

        if (candidates.size < minSize && (LastFM.isInitialized())) {
            onProgress(message(R.string.scrobble_gen_progress_more_artists))
            val extraNames = supervisorScope {
                sourceArtists.take(2).map { artist ->
                    async {
                        LastFM.getSimilarArtists(artist, limit = 20).getOrNull().orEmpty().drop(2).take(3)
                    }
                }.awaitAll().flatten().distinctBy { it.lowercase(Locale.ROOT) }
            }
            val extraTracks = PlaylistGenerationRules.interleave(artistPagePools(extraNames))
            widenedPools += extraTracks to 2
            candidates = curate(widenedPools)
        }

        if (candidates.size < minSize) {
            onProgress(message(R.string.scrobble_gen_progress_mood_pool))
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
            widenedPools += moodTracks to 1
            candidates = curate(widenedPools)
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
            throw GenerationException(R.string.scrobble_gen_error_similar_too_small, minSize)
        }
        accounts.ensureUnchanged()
        return result
    }

    /**
     * What the mixes start from: Last.fm's all-time top tracks and the latest
     * scrobbles, both found on YouTube Music, and when signed in the account's
     * history and likes. Nothing is read from Mylo's own play log.
     */
    private data class TasteSignals(
        val lastFmSeeds: List<Song>,
        val recentSongs: List<Song>,
        val accountHistory: List<Song>,
        val likedSongs: List<Song>,
    )

    private suspend fun loadTasteSignals(): TasteSignals {
        val lastFmSeeds = resolveLastFmTasteSeeds()
        val username = AppSettings.lastfmUsername.value.trim()
        val recent = if (username.isNotEmpty() && AppSettings.lastfmEnabled.value && LastFM.isInitialized()) {
            try {
                resolveLastFmTracks(lastFmRecentTracks(username, limit = 8))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                emptyList()
            }
        } else {
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
        return TasteSignals(lastFmSeeds, recent, history, liked)
    }

    /** The latest distinct scrobbled tracks, newest first, as chart rows to resolve. */
    private suspend fun lastFmRecentTracks(username: String, limit: Int): List<LastFM.RankedTrack> {
        val recent = LastFM.getRecentScrobbles(username, limit = 200).getOrThrow().items
        return recent
            .filter { !it.nowPlaying && it.timestamp > 0L && it.name.isNotBlank() && it.artist.isNotBlank() }
            .distinctBy { LastFmGenerationRules.trackKey(it.artist, it.name) }
            .take(limit)
            .map { LastFM.RankedTrack(it.name, it.artist, 0L, it.artworkUrl) }
    }

    private suspend fun resolveLastFmTasteSeeds(): List<Song> {
        val username = AppSettings.lastfmUsername.value.trim()
        if (username.isEmpty() || !AppSettings.lastfmEnabled.value || !LastFM.isInitialized()) return emptyList()
        val topTracks = LastFM.getTopTracks(username, period = "overall", limit = 20)
            .getOrNull().orEmpty().take(6)
        return resolveLastFmTracks(topTracks)
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
        onProgress(message(R.string.scrobble_gen_progress_seed_related, seed.title))
        val primary = radioFromSeeds(listOf(seed))
        val branchSeed = primary.firstOrNull { it.videoId != seed.videoId && !it.isVideo }
        val branch = if (primary.size < 40 && branchSeed != null) {
            onProgress(message(R.string.scrobble_gen_progress_expanding))
            radioFromSeeds(listOf(branchSeed))
        } else {
            emptyList()
        }
        val radioTracks = PlaylistGenerationRules.deduplicate(primary + branch)
        val lastFmTracks = if (radioTracks.size < target && (LastFM.isInitialized())) {
            onProgress(message(R.string.scrobble_gen_progress_more_tracks))
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
            throw GenerationException(R.string.scrobble_gen_error_no_similar, seed.title)
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
        return resolveLastFmTracks(similar.take(LastFmGenerationRules.MAX_RESOLVE_CANDIDATES))
    }

    private suspend fun tagSongsFromYtMusic(tag: String): List<Song> {
        val category = YtMusicRepository.moodAndGenres().getOrNull().orEmpty()
            .asSequence()
            .flatMap { it.items.asSequence() }
            .firstOrNull { it.title.equals(tag, ignoreCase = true) }
        val categorySongs = category?.let { mood ->
            YtMusicRepository.moodGenreShelves(mood.browseId, mood.params).getOrThrow()
                .flatMap { shelf -> shelf.items.mapNotNull(::shelfItemSong) }
        }.orEmpty()
        return if (categorySongs.isNotEmpty()) categorySongs else {
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

    private fun message(@StringRes resource: Int, vararg arguments: Any): String =
        appContext.getString(resource, *arguments)

    private fun requireYouTubeAccount() {
        requireGeneration(SignedIn.state(appContext).value, R.string.scrobble_gen_sign_in_to_use)
    }

    private suspend fun completeLibraryFeed(browseId: String): List<Song> = completeGenerationSongFeed(
        first = YtMusicRepository.browseSongs(browseId).getOrThrow(),
        loadNext = { YtMusicRepository.moreSongs(it).getOrThrow() },
    )

    private fun lastFmUsernameOrThrow(): String {
        val username = AppSettings.lastfmUsername.value.trim()
        requireGeneration(
            username.isNotEmpty() && AppSettings.lastfmEnabled.value && LastFM.isInitialized(),
            R.string.scrobble_gen_needs_lastfm,
        )
        return username
    }

    /** Your 12-month top tracks, minus anything scrobbled in the last 30 days. */
    private suspend fun forgottenFavoriteCandidates(onProgress: (String) -> Unit): List<Song> {
        val username = lastFmUsernameOrThrow()
        val top = LastFM.getTopTracks(
            username,
            period = LastFmGenerationRules.FORGOTTEN_PERIOD,
            limit = LastFmGenerationRules.FORGOTTEN_TRACK_LIMIT,
        ).getOrThrow()
        onProgress(message(R.string.scrobble_gen_progress_recent_window))
        val (from, to) = LastFmGenerationRules.recentWindowSeconds(System.currentTimeMillis())
        val recentKeys = recentTrackKeys(username, from, to)
        val forgotten = LastFmGenerationRules.excludeRecent(top, recentKeys) {
            LastFmGenerationRules.trackKey(it.artist, it.name)
        }
        onProgress(message(R.string.scrobble_gen_progress_resolving))
        return resolveLastFmTracks(forgotten.take(LastFmGenerationRules.MAX_RESOLVE_CANDIDATES))
    }

    private suspend fun recentTrackKeys(username: String, from: Long, to: Long): Set<String> {
        val keys = HashSet<String>()
        var page = 1
        var totalPages = 1
        while (page <= totalPages && page <= LastFmGenerationRules.MAX_RECENT_PAGES) {
            currentCoroutineContext().ensureActive()
            val result = LastFM.getRecentTracksPage(
                username,
                from = from,
                to = to,
                page = page,
                limit = LastFmGenerationRules.RECENT_PAGE_SIZE,
            ).getOrThrow()
            result.items.mapTo(keys) { LastFmGenerationRules.trackKey(it.artist, it.name) }
            totalPages = result.totalPages
            page++
        }
        requireGeneration(page > totalPages, R.string.scrobble_gen_error_history_incomplete)
        return keys
    }

    /** Last.fm top artists, their top tracks (at most 3 requests in flight), interleaved and deduped. */
    private suspend fun topArtistsMixCandidates(period: LastFmStatsPeriod, onProgress: (String) -> Unit): List<Song> {
        val username = lastFmUsernameOrThrow()
        val artists = LastFM.getTopArtists(
            username,
            period = period.apiValue,
            limit = LastFmGenerationRules.TOP_ARTIST_COUNT,
        ).getOrThrow().take(LastFmGenerationRules.TOP_ARTIST_COUNT)
        if (artists.isEmpty()) return emptyList()

        onProgress(message(R.string.scrobble_gen_progress_top_artist_tracks, artists.size))
        val requestGate = Semaphore(LastFmGenerationRules.ARTIST_REQUEST_CONCURRENCY)
        val pools = supervisorScope {
            artists.map { artist ->
                async {
                    requestGate.withPermit {
                        LastFM.getArtistTopTracks(artist.name, limit = LastFmGenerationRules.ARTIST_TRACKS_PER_ARTIST)
                            .getOrNull().orEmpty()
                    }
                }
            }.awaitAll()
        }
        val mixed = LastFmGenerationRules.interleaveUnique(
            pools = pools,
            key = { LastFmGenerationRules.trackKey(it.artist, it.name) },
            cap = LastFmGenerationRules.MIX_TRACK_CAP,
        )
        onProgress(message(R.string.scrobble_gen_progress_resolving))
        return resolveLastFmTracks(mixed)
    }

    /** Resolves Last.fm names to playable rows, keeping only exact YouTube Music title and artist matches. */
    private suspend fun resolveLastFmTracks(tracks: List<LastFM.RankedTrack>): List<Song> {
        if (tracks.isEmpty()) return emptyList()
        return supervisorScope {
            tracks.map { track ->
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
            .filter { it.videoId.isNotBlank() && !it.isVideo && it.localUri == null }
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
        val username = lastFmUsernameOrThrow()
        onProgress(message(R.string.scrobble_gen_progress_scrobbled))
        val heardKeys = heardTrackKeys(username)
        val account = if (SignedIn.state(appContext).value) {
            generationExclusionRead { YtMusicRepository.history().getOrThrow() }
        } else emptyList()
        val heardIds = account.mapTo(HashSet()) { it.videoId }
        account.mapTo(heardKeys, PlaylistGenerationRules::trackKey)
        currentCoroutineContext().ensureActive()
        onProgress(message(R.string.scrobble_gen_progress_unheard))
        val quickPicks = YtMusicRepository.quickPicks(heardIds).getOrNull().orEmpty()
        val home = homeSongs()
        return PlaylistGenerationRules.excludeHeard(
            candidates = quickPicks + home,
            heardVideoIds = heardIds,
            heardTrackKeys = heardKeys,
        )
    }

    /** Complete all-time Last.fm identity, bounded and fail-closed rather than a partial chart. */
    private suspend fun heardTrackKeys(username: String): HashSet<String> = supervisorScope {
        val first = generationExclusionRead {
            LastFM.getTopTracksPage(username, page = 1, limit = 100).getOrThrow()
        }
        requireGeneration(first.totalPages <= HEARD_CHART_PAGES, R.string.scrobble_gen_error_history_incomplete)
        val gate = Semaphore(LastFmGenerationRules.ARTIST_REQUEST_CONCURRENCY)
        val charts = (2..first.totalPages).map { page ->
            async {
                gate.withPermit {
                    generationExclusionRead {
                        LastFM.getTopTracksPage(username, page = page, limit = 100).getOrThrow()
                    }
                }
            }
        }
        val recent = async {
            generationExclusionRead { LastFM.getRecentScrobbles(username, limit = 200).getOrThrow().items }
        }
        val remaining = charts.awaitAll()
        val allTracks = first.items + remaining.flatMap { it.items }
        requireGeneration(
            remaining.all { it.totalPages == first.totalPages } && allTracks.size.toLong() >= first.total,
            R.string.scrobble_gen_error_history_incomplete,
        )
        val keys = HashSet<String>()
        allTracks.mapTo(keys) {
            PlaylistGenerationRules.trackKey(it.name, it.artist)
        }
        recent.await().mapTo(keys) { PlaylistGenerationRules.trackKey(it.name, it.artist) }
        keys
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

    companion object {
        const val MIN_TRACKS = 5
        private const val MIN_SIMILAR_MIX_SIZE = 20
        const val MAX_TRACKS = 35
        private const val HEARD_CHART_PAGES = 50
        private const val LIBRARY_SONGS_BROWSE_ID = "FEmusic_liked_videos"
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
            .filter { it.videoId.isNotBlank() && !it.isVideo && it.localUri == null }
            .distinctBy { it.videoId }
            .toList()

    /**
     * LastWave's weighted mix curation, adapted to Mylo's Last.fm and
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
            if (candidate.videoId == seed.videoId || title.isBlank() || title == seedTitle ||
                trackKey(candidate) == trackKey(seed)
            ) return@forEach
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

    fun trackKey(song: Song): String = trackKey(song.title, song.artist)

    fun trackKey(title: String, artist: String): String =
        "${normalize(title)}|${normalize(artist)}"

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}
