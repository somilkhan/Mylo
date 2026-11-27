package com.music.bitchord.data.scrobbling

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Locale

/**
 * The windows the Stats tab offers: exactly the periods Last.fm charts by, so
 * every number on the screen is Last.fm's own, never one rebuilt on the phone
 * from pages of history.
 *
 * [days] is the length of the rolling window, used to count the scrobbles in
 * it; null for [OVERALL], whose count is the profile's play count.
 */
enum class LastFmStatsPeriod(val apiValue: String, val days: Int?) {
    WEEK("7day", 7),
    MONTH("1month", 30),
    QUARTER("3month", 90),
    HALF_YEAR("6month", 180),
    YEAR("12month", 365),
    OVERALL("overall", null),
}

/** Everything the Stats tab shows for one account and one period. */
internal data class LastFmStatsSnapshot(
    val profile: LastFM.UserStats?,
    /** Scrobbles in the period: the whole history for [LastFmStatsPeriod.OVERALL]. */
    val scrobbles: Long,
    val artistCount: Long,
    val albumCount: Long,
    val trackCount: Long,
    val topArtists: List<LastFM.RankedArtist>,
    val topTracks: List<LastFM.RankedTrack>,
    val topAlbums: List<LastFM.RankedAlbum>,
    /** Newest first; a now-playing row, if any, comes first. */
    val recent: List<LastFM.RecentTrack>,
) {
    /** Nothing scrobbled in the period at all, as opposed to a failed fetch. */
    val isEmpty: Boolean
        get() = scrobbles == 0L && topArtists.isEmpty() && topTracks.isEmpty() && topAlbums.isEmpty()
}

internal data class LastFmStatsCacheKey(
    val username: String,
    val endpoint: String,
    val period: LastFmStatsPeriod,
)

internal data class LastFmStatsCacheEntry(
    val snapshot: LastFmStatsSnapshot,
    val fetchedAtMillis: Long,
)

/** Snapshots for [TTL_MILLIS], so switching periods back and forth costs nothing. */
internal class LastFmStatsCache(
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val entries = mutableMapOf<LastFmStatsCacheKey, LastFmStatsCacheEntry>()

    @Synchronized
    fun get(key: LastFmStatsCacheKey): LastFmStatsCacheEntry? = entries[key]

    @Synchronized
    fun isFresh(key: LastFmStatsCacheKey): Boolean {
        val entry = entries[key] ?: return false
        val age = nowMillis() - entry.fetchedAtMillis
        return age >= 0L && age < TTL_MILLIS
    }

    @Synchronized
    fun put(key: LastFmStatsCacheKey, snapshot: LastFmStatsSnapshot) {
        entries[key] = LastFmStatsCacheEntry(snapshot, nowMillis())
    }

    companion object {
        const val TTL_MILLIS = 5 * 60 * 1_000L
    }
}

/** The Last.fm calls a snapshot is built from; a seam for tests. */
internal interface LastFmStatsApi {
    suspend fun profile(username: String): Result<LastFM.UserStats>
    suspend fun topArtists(username: String, period: String, limit: Int): Result<LastFM.PagedResult<LastFM.RankedArtist>>
    suspend fun topTracks(username: String, period: String, limit: Int): Result<LastFM.PagedResult<LastFM.RankedTrack>>
    suspend fun topAlbums(username: String, period: String, limit: Int): Result<LastFM.PagedResult<LastFM.RankedAlbum>>
    suspend fun recent(username: String, limit: Int, fromEpochSeconds: Long?): Result<LastFM.PagedResult<LastFM.RecentTrack>>
}

private object DefaultLastFmStatsApi : LastFmStatsApi {
    override suspend fun profile(username: String) = LastFM.getUserStats(username)
    override suspend fun topArtists(username: String, period: String, limit: Int) =
        LastFM.getTopArtistsChart(username, period, limit)
    override suspend fun topTracks(username: String, period: String, limit: Int) =
        LastFM.getTopTracksChart(username, period, limit)
    override suspend fun topAlbums(username: String, period: String, limit: Int) =
        LastFM.getTopAlbumsChart(username, period, limit)
    override suspend fun recent(username: String, limit: Int, fromEpochSeconds: Long?) =
        LastFM.getRecentScrobbles(username, limit, fromEpochSeconds)
}

/**
 * Last.fm statistics for the Stats tab, one period at a time.
 *
 * A snapshot is six small requests made together: the profile, the top
 * artists, tracks and albums for the period (whose totals are the period's
 * distinct counts), the latest scrobbles, and for a rolling period one more
 * one-row request whose total is the number of scrobbles in it. The charts
 * are required; the profile and the recent list only decorate the screen, so
 * a failure there leaves them out instead of failing the whole snapshot.
 */
internal class LastFmStatisticsRepository(
    private val api: LastFmStatsApi = DefaultLastFmStatsApi,
    private val cache: LastFmStatsCache = LastFmStatsCache(),
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val endpoint: () -> String = { LastFM.currentConfig().endpoint },
) {
    fun key(username: String, period: LastFmStatsPeriod): LastFmStatsCacheKey {
        val normalized = username.trim().lowercase(Locale.ROOT)
        require(normalized.isNotEmpty()) { "Last.fm username is missing" }
        return LastFmStatsCacheKey(normalized, endpoint().trim(), period)
    }

    fun cached(key: LastFmStatsCacheKey): LastFmStatsSnapshot? = cache.get(key)?.snapshot

    fun isFresh(key: LastFmStatsCacheKey): Boolean = cache.isFresh(key)

    suspend fun load(
        username: String,
        period: LastFmStatsPeriod,
        force: Boolean,
    ): Result<LastFmStatsSnapshot> {
        val key = key(username, period)
        val existing = cache.get(key)
        if (!force && existing != null && cache.isFresh(key)) return Result.success(existing.snapshot)
        return try {
            val snapshot = fetch(username.trim(), period)
            cache.put(key, snapshot)
            Result.success(snapshot)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun fetch(username: String, period: LastFmStatsPeriod): LastFmStatsSnapshot = coroutineScope {
        val profile = async { api.profile(username) }
        val artists = async { api.topArtists(username, period.apiValue, CHART_LIMIT) }
        val tracks = async { api.topTracks(username, period.apiValue, CHART_LIMIT) }
        val albums = async { api.topAlbums(username, period.apiValue, CHART_LIMIT) }
        val recent = async { api.recent(username, RECENT_LIMIT, null) }
        val periodCount = period.days?.let { days ->
            async { api.recent(username, 1, nowEpochSeconds() - days * SECONDS_PER_DAY) }
        }

        val artistPage = artists.await().getOrThrow()
        val trackPage = tracks.await().getOrThrow()
        val albumPage = albums.await().getOrThrow()
        val user = profile.await().getOrNull()
        val scrobbles = if (periodCount == null) {
            // All time is the profile's own play count; without the profile
            // there is no honest figure to show, so that is a failure.
            user?.playCount ?: profile.await().getOrThrow().playCount
        } else {
            periodCount.await().getOrThrow().total
        }

        LastFmStatsSnapshot(
            profile = user,
            scrobbles = scrobbles,
            artistCount = if (period == LastFmStatsPeriod.OVERALL && user != null) {
                maxOf(user.artistCount, artistPage.total)
            } else {
                artistPage.total
            },
            albumCount = if (period == LastFmStatsPeriod.OVERALL && user != null) {
                maxOf(user.albumCount, albumPage.total)
            } else {
                albumPage.total
            },
            trackCount = if (period == LastFmStatsPeriod.OVERALL && user != null) {
                maxOf(user.trackCount, trackPage.total)
            } else {
                trackPage.total
            },
            topArtists = artistPage.items.filter { it.playCount > 0L },
            topTracks = trackPage.items.filter { it.playCount > 0L && it.artist.isNotBlank() },
            topAlbums = albumPage.items.filter { it.playCount > 0L },
            recent = recent.await().getOrNull()?.items.orEmpty().take(RECENT_LIMIT),
        )
    }

    companion object {
        const val CHART_LIMIT = 50
        const val RECENT_LIMIT = 10
        private const val SECONDS_PER_DAY = 86_400L
    }
}
