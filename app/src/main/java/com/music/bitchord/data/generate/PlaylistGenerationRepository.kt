package com.music.bitchord.data.generate

import android.content.Context
import com.music.bitchord.auth.SignedIn
import com.music.bitchord.data.ListeningHistory
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
                require(seed.videoId.isNotBlank()) { "This track cannot start a YouTube Music radio" }
                onProgress("Finding tracks related to ${seed.title}…")
                YtMusicRepository.radio(seed.videoId).getOrThrow()
                    .filterNot { it.videoId == seed.videoId }
            }
            PlaylistGenerationMode.SIMILAR_ARTISTS -> {
                val artist = request.seedArtist.trim()
                require(artist.isNotEmpty()) { "Enter an artist name" }
                onProgress("Finding ${artist}'s music…")
                val browseId = YtMusicRepository.findArtistPageId(artist).getOrThrow()
                    ?: throw IllegalStateException("Couldn't find that exact artist")
                YtMusicRepository.artistPage(browseId).getOrThrow().songs
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
                onProgress("Building a balanced mix…")
                val radio = request.seedTrack?.videoId
                    ?.takeIf(String::isNotBlank)
                    ?.let { YtMusicRepository.radio(it).getOrNull().orEmpty() }
                    .orEmpty()
                val picks = YtMusicRepository.quickPicks().getOrNull().orEmpty()
                val home = homeSongs()
                radio + picks + home
            }
            PlaylistGenerationMode.RECOMMENDATIONS -> {
                onProgress("Loading your YouTube Music recommendations…")
                YtMusicRepository.quickPicks().getOrThrow()
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
     * Creates a fresh mix inspired by a saved playlist. The source playlist is
     * never modified or repeated; up to three distinct-artist seeds fan out to
     * YouTube Music radio, with Quick Picks/Home used only when radio is thin.
     */
    suspend fun generateSimilar(
        sourceSongs: List<Song>,
        count: Int = 30,
        onProgress: (String) -> Unit = {},
    ): List<Song> {
        val target = count.coerceIn(MIN_TRACKS, MAX_TRACKS)
        val sourceTracks = PlaylistGenerationRules.deduplicate(sourceSongs)
        require(sourceTracks.isNotEmpty()) { "This playlist has no playable tracks to use as seeds." }

        val seeds = (sourceTracks.distinctBy { it.artist.trim().lowercase(Locale.ROOT) } + sourceTracks)
            .distinctBy { it.videoId }
            .take(3)
        onProgress("Building a mix from ${seeds.size} playlist seeds…")

        val radioPools = supervisorScope {
            seeds.map { seed ->
                async {
                    try {
                        YtMusicRepository.radio(seed.videoId).getOrThrow()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll()
        }

        val sourceVideoIds = sourceSongs.mapTo(HashSet()) { it.videoId }
        val sourceTrackKeys = sourceSongs.mapTo(HashSet(), PlaylistGenerationRules::trackKey)
        var candidates = PlaylistGenerationRules.excludeHeard(
            candidates = PlaylistGenerationRules.deduplicate(PlaylistGenerationRules.interleave(radioPools)),
            heardVideoIds = sourceVideoIds,
            heardTrackKeys = sourceTrackKeys,
        )

        if (candidates.size < target) {
            onProgress("Widening the mix with YouTube Music recommendations…")
            val fallback = supervisorScope {
                val picks = async { YtMusicRepository.quickPicks().getOrNull().orEmpty() }
                val home = async { homeSongs() }
                picks.await() + home.await()
            }
            candidates = PlaylistGenerationRules.deduplicate(candidates + fallback)
            candidates = PlaylistGenerationRules.excludeHeard(
                candidates = candidates,
                heardVideoIds = sourceVideoIds,
                heardTrackKeys = sourceTrackKeys,
            )
        }

        currentCoroutineContext().ensureActive()
        val savedSongs = LocalPlaylistStore.playlists.value.flatMap { it.songs }
        val result = PlaylistGenerationRules.prioritizeFresh(
            candidates = candidates,
            savedVideoIds = savedSongs.mapTo(HashSet()) { it.videoId },
            savedTrackKeys = savedSongs.mapTo(HashSet(), PlaylistGenerationRules::trackKey),
            limit = target,
        )
        if (result.size < MIN_TRACKS) {
            throw IllegalStateException("Not enough new tracks found for this playlist. Try another source playlist.")
        }
        return result
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

    fun trackKey(song: Song): String =
        "${normalize(song.title)}|${normalize(song.artist)}"

    private fun normalize(value: String): String =
        value.lowercase().filter(Char::isLetterOrDigit)
}
