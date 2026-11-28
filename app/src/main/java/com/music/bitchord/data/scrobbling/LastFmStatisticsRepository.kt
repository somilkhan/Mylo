package com.music.bitchord.data.scrobbling

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The windows the Stats tab offers: exactly the periods Last.fm charts by, so
 * every number on the screen is Last.fm's own, never one rebuilt on the phone
 * from pages of history.
 *
 * [days] is the length of the rolling window, used to count the scrobbles in
 * it; null for [OVERALL], whose count is the profile's play count.
 */
enum class LastFmStatsPeriod(val apiValue: String, val days: Int?, val activityBuckets: Int) {
    WEEK("7day", 7, 7),
    MONTH("1month", 30, 10),
    QUARTER("3month", 90, 12),
    HALF_YEAR("6month", 180, 12),
    YEAR("12month", 365, 12),

    /** Bucketed by calendar year since the account was made, at most [activityBuckets]. */
    OVERALL("overall", null, 12),
}

/**
 * One bar of the activity chart: the scrobbles in `[start, end]`, in Unix
 * seconds. A rolling period's buckets tile it exactly, so they add up to the
 * period's own count.
 */
internal data class LastFmActivityBucket(
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
    val scrobbles: Long,
)

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
    /** Oldest first. Empty when any slice failed: a chart with holes would mislead. */
    val activity: List<LastFmActivityBucket> = emptyList(),
    /** Scrobbles in the window just before this one; null for all time or when unknown. */
    val previousScrobbles: Long? = null,
) {
    /** Nothing scrobbled in the period at all, as opposed to a failed fetch. */
    val isEmpty: Boolean
        get() = scrobbles == 0L && topArtists.isEmpty() && topTracks.isEmpty() && topAlbums.isEmpty()
}

internal data class LastFmStatsCacheKey(
    val username: String,
    val endpoint: String,
    val period: LastFmStatsPeriod,
    val configuration: String = "",
    val zoneId: String = "",
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
    suspend fun recent(
        username: String,
        limit: Int,
        fromEpochSeconds: Long?,
        toEpochSeconds: Long? = null,
        page: Int = 1,
    ): Result<LastFM.PagedResult<LastFM.RecentTrack>>
}

private object DefaultLastFmStatsApi : LastFmStatsApi {
    override suspend fun profile(username: String) = LastFM.getUserStats(username)
    override suspend fun topArtists(username: String, period: String, limit: Int) =
        LastFM.getTopArtistsChart(username, period, limit)
    override suspend fun topTracks(username: String, period: String, limit: Int) =
        LastFM.getTopTracksChart(username, period, limit)
    override suspend fun topAlbums(username: String, period: String, limit: Int) =
        LastFM.getTopAlbumsChart(username, period, limit)
    override suspend fun recent(username: String, limit: Int, fromEpochSeconds: Long?, toEpochSeconds: Long?, page: Int) =
        LastFM.getRecentScrobbles(username, limit, fromEpochSeconds, toEpochSeconds, page)
}

/**
 * Last.fm statistics for the Stats tab, one period at a time.
 *
 * A snapshot is a handful of small requests made together: the profile, the
 * top artists, tracks and albums for the period (whose totals are the
 * period's distinct counts), and one-row history
 * requests whose totals count the scrobbles in a window: the period itself,
 * the window before it, and each slice of the activity chart. The charts are
 * required; everything else only decorates the screen, so a failure there
 * leaves it out instead of failing the whole snapshot.
 */
internal class LastFmStatisticsRepository(
    private val api: LastFmStatsApi = DefaultLastFmStatsApi,
    private val cache: LastFmStatsCache = LastFmStatsCache(),
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val endpoint: () -> String = { LastFM.currentConfig().endpoint },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val configuration: () -> String = { statsConfigurationFingerprint(LastFM.currentConfig()) },
) {
    fun key(username: String, period: LastFmStatsPeriod): LastFmStatsCacheKey {
        val normalized = username.trim().lowercase(Locale.ROOT)
        require(normalized.isNotEmpty()) { "Last.fm username is missing" }
        return LastFmStatsCacheKey(
            normalized, endpoint().trim(), period, configuration(),
            if (period == LastFmStatsPeriod.OVERALL) zone().id else "",
        )
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
            currentCoroutineContext().ensureActive()
            // LastFM captures its global config per HTTP call. Never cache a
            // potentially mixed snapshot if configuration changed during the burst.
            check(key == key(username, period)) { "Last.fm configuration changed" }
            cache.put(key, snapshot)
            Result.success(snapshot)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun fetch(username: String, period: LastFmStatsPeriod): LastFmStatsSnapshot = coroutineScope {
        val now = nowEpochSeconds()
        val profile = async { api.profile(username) }
        val artists = async { api.topArtists(username, period.apiValue, CHART_LIMIT) }
        val tracks = async { api.topTracks(username, period.apiValue, CHART_LIMIT) }
        val albums = async { api.topAlbums(username, period.apiValue, CHART_LIMIT) }
        val periodCount = period.days?.let { days ->
            async { api.recent(username, 1, now - days * SECONDS_PER_DAY, now - 1L) }
        }
        val previousCount = period.days?.let { days ->
            async {
                api.recent(username, 1, now - 2L * days * SECONDS_PER_DAY, now - days * SECONDS_PER_DAY - 1)
            }
        }
        // Rolling windows are known now; all time waits for the profile's
        // registration date, so it starts once that has come back.
        val activity = async {
            val registered = if (period.days == null) {
                profile.await().getOrNull()?.registeredAtEpochSeconds ?: 0L
            } else {
                0L
            }
            countActivity(username, activityWindows(period, now, registered, zone()))
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
            // Rolling buckets cover precisely the count query. A remote
            // backfill/deletion can still change totals between HTTP calls.
            activity = activity.await().takeUnless { buckets ->
                period.days != null && buckets.sumOf { it.scrobbles } != scrobbles
            }.orEmpty(),
            previousScrobbles = previousCount?.await()?.getOrNull()?.total,
        )
    }

    /**
     * One page of the scrobble history, newest first, for the Recent list.
     * Only the first page keeps Last.fm's now-playing row; later pages repeat
     * it at their top, and it would show up again mid-list.
     */
    fun recentCutoff(): Long = nowEpochSeconds()

    suspend fun recentPage(
        username: String,
        page: Int,
        toEpochSeconds: Long? = null,
    ): Result<LastFM.PagedResult<LastFM.RecentTrack>> = try {
        val key = key(username, LastFmStatsPeriod.OVERALL)
        require(page > 0) { "Last.fm page must be positive" }
        val result = api.recent(username.trim(), RECENT_PAGE_SIZE, null, toEpochSeconds, page)
        currentCoroutineContext().ensureActive()
        check(key == key(username, LastFmStatsPeriod.OVERALL)) { "Last.fm configuration changed" }
        result.map { response ->
            if (page == 1) response else response.copy(items = response.items.filterNot { it.nowPlaying })
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun countActivity(
        username: String,
        windows: List<LongRange>,
    ): List<LastFmActivityBucket> = coroutineScope {
        if (windows.isEmpty()) return@coroutineScope emptyList()
        // A few at a time: Last.fm answers a burst of a dozen with rate-limit errors.
        val permits = Semaphore(ACTIVITY_PARALLELISM)
        val counts = windows.map { window ->
            async {
                permits.withPermit {
                    api.recent(username, 1, window.first, window.last).getOrNull()?.total
                }
            }
        }.map { it.await() }
        if (counts.any { it == null }) return@coroutineScope emptyList()
        windows.zip(counts) { window, count -> LastFmActivityBucket(window.first, window.last, count!!) }
    }

    companion object {
        const val CHART_LIMIT = 50
        const val RECENT_PAGE_SIZE = 50
        private const val ACTIVITY_PARALLELISM = 4
        private const val SECONDS_PER_DAY = 86_400L
    }
}

/**
 * The activity chart's slices as `start..end` Unix-second ranges, oldest first.
 *
 * A rolling period is cut into [LastFmStatsPeriod.activityBuckets] equal
 * slices that end now, so the bars add up to the period's count. All time
 * goes by calendar year in [zone], from the year the account was made (its
 * first bar starting at registration), keeping the latest years when there
 * are more than fit; without a registration date there is no chart.
 */
internal fun activityWindows(
    period: LastFmStatsPeriod,
    nowEpochSeconds: Long,
    registeredEpochSeconds: Long,
    zone: ZoneId,
): List<LongRange> {
    val days = period.days
    if (days != null) {
        val start = nowEpochSeconds - days * 86_400L
        val end = nowEpochSeconds - 1L
        val count = period.activityBuckets
        val width = (end - start + 1L) / count
        return List(count) { index ->
            val from = start + index * width
            // Inclusive second ranges: neighbours must not share a second.
            val to = if (index == count - 1) end else from + width - 1
            from..to
        }
    }
    if (registeredEpochSeconds <= 0L || registeredEpochSeconds >= nowEpochSeconds) return emptyList()
    val firstYear = Instant.ofEpochSecond(registeredEpochSeconds).atZone(zone).year
    val lastYear = Instant.ofEpochSecond(nowEpochSeconds).atZone(zone).year
    val years = (maxOf(firstYear, lastYear - period.activityBuckets + 1)..lastYear).toList()
    if (years.size < 2) return emptyList()
    return years.map { year ->
        val yearStart = LocalDate.of(year, 1, 1).atStartOfDay(zone).toEpochSecond()
        val nextYear = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toEpochSecond()
        maxOf(yearStart, registeredEpochSeconds)..minOf(nextYear - 1, nowEpochSeconds)
    }
}

/** Opaque, collision-resistant identity; credentials never appear in cache-key diagnostics. */
internal fun statsConfigurationFingerprint(config: LastFM.RuntimeConfig): String {
    val fields = listOf(config.endpoint.trim(), config.apiKey, config.secret, config.sessionKey.orEmpty())
    val encoded = fields.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
}
