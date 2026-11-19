package com.music.bitchord.data.scrobbling

import com.music.bitchord.data.generate.PlaylistGenerationRules
import com.music.bitchord.data.stats.ReplayPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

internal data class LastFmStatsPeriodRange(
    val requestFromEpochSeconds: Long,
    val requestToEpochSeconds: Long,
    val startInclusiveEpochSeconds: Long,
    val endInclusiveEpochSeconds: Long,
)

internal data class LastFmStatsCacheKey(
    val username: String,
    val period: ReplayPeriod,
    val rangeStartEpochSeconds: Long?,
)

internal data class LastFmStatsQuery(
    val key: LastFmStatsCacheKey,
    val range: LastFmStatsPeriodRange?,
)

internal data class LastFmStatsCacheEntry(
    val tracks: List<LastFM.RankedTrack>,
    val fetchedAtMillis: Long,
)

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
    fun put(key: LastFmStatsCacheKey, tracks: List<LastFM.RankedTrack>) {
        entries[key] = LastFmStatsCacheEntry(tracks.toList(), nowMillis())
    }

    companion object {
        const val TTL_MILLIS = 5 * 60 * 1_000L
    }
}

internal interface LastFmStatsApi {
    suspend fun topTracks(
        username: String,
        page: Int,
        limit: Int,
    ): Result<LastFM.PagedResult<LastFM.RankedTrack>>

    suspend fun recentTracks(
        username: String,
        from: Long,
        to: Long,
        page: Int,
        limit: Int,
    ): Result<LastFM.PagedResult<LastFM.RecentTrack>>
}

private object DefaultLastFmStatsApi : LastFmStatsApi {
    override suspend fun topTracks(
        username: String,
        page: Int,
        limit: Int,
    ): Result<LastFM.PagedResult<LastFM.RankedTrack>> =
        LastFM.getTopTracksPage(username, page, limit)

    override suspend fun recentTracks(
        username: String,
        from: Long,
        to: Long,
        page: Int,
        limit: Int,
    ): Result<LastFM.PagedResult<LastFM.RecentTrack>> =
        LastFM.getRecentTracksPage(username, from, to, page, limit)
}

internal interface LastFmStatsDataSource {
    suspend fun fetch(
        username: String,
        period: ReplayPeriod,
        range: LastFmStatsPeriodRange?,
    ): List<LastFM.RankedTrack>
}

internal class LastFmApiStatsDataSource(
    private val api: LastFmStatsApi = DefaultLastFmStatsApi,
    private val pageDelayMillis: Long = 120L,
) : LastFmStatsDataSource {
    override suspend fun fetch(
        username: String,
        period: ReplayPeriod,
        range: LastFmStatsPeriodRange?,
    ): List<LastFM.RankedTrack> =
        if (period == ReplayPeriod.ALL_TIME) {
            fetchAllTopTracks(username)
        } else {
            fetchAllRecentTracks(username, requireNotNull(range) { "A calendar period needs a date range" })
        }

    private suspend fun fetchAllTopTracks(username: String): List<LastFM.RankedTrack> {
        val first = api.topTracks(username, page = 1, limit = TOP_TRACKS_PAGE_SIZE).getOrThrow()
        val rows = first.items.toMutableList()
        for (page in 2..first.totalPages) {
            currentCoroutineContext().ensureActive()
            if (pageDelayMillis > 0L) delay(pageDelayMillis)
            rows += api.topTracks(username, page, TOP_TRACKS_PAGE_SIZE).getOrThrow().items
        }
        return rows
            .filter { it.name.isNotBlank() && it.artist.isNotBlank() }
            .groupBy { PlaylistGenerationRules.trackKey(it.name, it.artist) }
            .values
            .mapNotNull { group ->
                val representative = group.maxByOrNull { it.playCount } ?: return@mapNotNull null
                representative.copy(
                    playCount = group.sumOf { it.playCount.coerceAtLeast(0L) },
                    artworkUrl = group.mapNotNull { it.artworkUrl?.takeIf(String::isNotBlank) }.firstOrNull(),
                )
            }
            .filter { it.playCount > 0L }
            .sortedByDescending { it.playCount }
    }

    private suspend fun fetchAllRecentTracks(
        username: String,
        range: LastFmStatsPeriodRange,
    ): List<LastFM.RankedTrack> {
        val first = api.recentTracks(
            username,
            range.requestFromEpochSeconds,
            range.requestToEpochSeconds,
            page = 1,
            limit = RECENT_TRACKS_PAGE_SIZE,
        ).getOrThrow()
        val rows = first.items.toMutableList()
        for (page in 2..first.totalPages) {
            currentCoroutineContext().ensureActive()
            if (pageDelayMillis > 0L) delay(pageDelayMillis)
            rows += api.recentTracks(
                username,
                range.requestFromEpochSeconds,
                range.requestToEpochSeconds,
                page,
                RECENT_TRACKS_PAGE_SIZE,
            ).getOrThrow().items
        }
        return rows
            .filter {
                it.timestamp in range.startInclusiveEpochSeconds..range.endInclusiveEpochSeconds &&
                    it.name.isNotBlank() && it.artist.isNotBlank()
            }
            .groupBy { PlaylistGenerationRules.trackKey(it.name, it.artist) }
            .values
            .mapNotNull { group ->
                val representative = group.maxByOrNull { it.timestamp } ?: return@mapNotNull null
                LastFM.RankedTrack(
                    name = representative.name,
                    artist = representative.artist,
                    playCount = group.size.toLong(),
                    artworkUrl = group.mapNotNull { it.artworkUrl?.takeIf(String::isNotBlank) }.firstOrNull(),
                )
            }
            .sortedByDescending { it.playCount }
    }

    companion object {
        const val TOP_TRACKS_PAGE_SIZE = 100
        const val RECENT_TRACKS_PAGE_SIZE = 200
    }
}

internal class LastFmStatisticsRepository(
    private val source: LastFmStatsDataSource = LastFmApiStatsDataSource(),
    private val cache: LastFmStatsCache = LastFmStatsCache(),
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    fun query(username: String, period: ReplayPeriod): LastFmStatsQuery {
        val normalizedUsername = username.trim().lowercase(Locale.ROOT)
        require(normalizedUsername.isNotEmpty()) { "Last.fm username is missing" }
        val current = now()
        val zoneId = zone()
        val periodStart = period.range(current.atZone(zoneId).toLocalDate())?.first
        val range = periodStart?.let { startDate ->
            val startEpoch = startDate.atStartOfDay(zoneId).toEpochSecond()
            val endEpoch = current.epochSecond
            LastFmStatsPeriodRange(
                requestFromEpochSeconds = startEpoch - 1L,
                requestToEpochSeconds = endEpoch + 1L,
                startInclusiveEpochSeconds = startEpoch,
                endInclusiveEpochSeconds = endEpoch,
            )
        }
        return LastFmStatsQuery(
            key = LastFmStatsCacheKey(
                username = normalizedUsername,
                period = period,
                rangeStartEpochSeconds = range?.startInclusiveEpochSeconds,
            ),
            range = range,
        )
    }

    fun cached(query: LastFmStatsQuery): LastFmStatsCacheEntry? = cache.get(query.key)

    fun isFresh(query: LastFmStatsQuery): Boolean = cache.isFresh(query.key)

    suspend fun refresh(
        username: String,
        query: LastFmStatsQuery,
        force: Boolean,
    ): Result<List<LastFM.RankedTrack>> {
        val existing = cache.get(query.key)
        if (!force && existing != null && cache.isFresh(query.key)) {
            return Result.success(existing.tracks)
        }
        return try {
            val tracks = source.fetch(username.trim(), query.key.period, query.range)
            cache.put(query.key, tracks)
            Result.success(tracks)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
