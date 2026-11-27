package com.music.bitchord.data.scrobbling

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LastFmStatisticsRepositoryTest {
    private val now = 1_795_000_000L

    @Test
    fun rollingPeriodCountsScrobblesSinceItsStartAndUsesChartTotals() = runBlocking {
        val api = FakeApi()
        val repository = LastFmStatisticsRepository(api, LastFmStatsCache { 0L }, { now }, { "https://api" })

        val snapshot = repository.load(" Zen ", LastFmStatsPeriod.WEEK, force = false).getOrThrow()

        assertEquals(now - 7 * 86_400L, api.countFrom)
        assertEquals(412L, snapshot.scrobbles)
        assertEquals(31L, snapshot.artistCount)
        assertEquals(58L, snapshot.albumCount)
        assertEquals(97L, snapshot.trackCount)
        assertEquals("7day", api.lastPeriod)
        assertEquals(listOf("Artist A"), snapshot.topArtists.map { it.name })
    }

    @Test
    fun allTimeUsesTheProfilePlayCountAndNeverAPeriodCountRequest() = runBlocking {
        val api = FakeApi()
        val repository = LastFmStatisticsRepository(api, LastFmStatsCache { 0L }, { now }, { "https://api" })

        val snapshot = repository.load("zen", LastFmStatsPeriod.OVERALL, force = false).getOrThrow()

        assertEquals(12_000L, snapshot.scrobbles)
        assertNull(api.countFrom)
        // The profile's distinct counts win over a chart total that is lower.
        assertEquals(420L, snapshot.artistCount)
    }

    @Test
    fun freshSnapshotIsServedFromCacheUntilForced() = runBlocking {
        val api = FakeApi()
        val repository = LastFmStatisticsRepository(api, LastFmStatsCache { 0L }, { now }, { "https://api" })

        repository.load("zen", LastFmStatsPeriod.MONTH, force = false)
        repository.load("ZEN", LastFmStatsPeriod.MONTH, force = false)
        assertEquals(1, api.chartCalls.get() / 3)

        repository.load("zen", LastFmStatsPeriod.MONTH, force = true)
        assertEquals(2, api.chartCalls.get() / 3)
    }

    @Test
    fun aFailedChartFailsTheSnapshotButAFailedRecentListOnlyHidesIt() = runBlocking {
        val failingChart = LastFmStatisticsRepository(
            FakeApi(failTracks = true), LastFmStatsCache { 0L }, { now }, { "https://api" },
        )
        assertTrue(failingChart.load("zen", LastFmStatsPeriod.MONTH, false).isFailure)

        val failingRecent = LastFmStatisticsRepository(
            FakeApi(failRecentList = true), LastFmStatsCache { 0L }, { now }, { "https://api" },
        )
        val snapshot = failingRecent.load("zen", LastFmStatsPeriod.MONTH, false).getOrThrow()
        assertTrue(snapshot.recent.isEmpty())
    }

    @Test
    fun cacheKeysSeparateAccountsEndpointsAndPeriods() {
        val repository = LastFmStatisticsRepository(FakeApi(), LastFmStatsCache { 0L }, { now }, { " https://api " })
        assertEquals(
            LastFmStatsCacheKey("zen", "https://api", LastFmStatsPeriod.YEAR),
            repository.key(" Zen ", LastFmStatsPeriod.YEAR),
        )
    }

    @Test
    fun cacheExpiresAfterItsTtl() {
        var clock = 0L
        val cache = LastFmStatsCache { clock }
        val key = LastFmStatsCacheKey("zen", "e", LastFmStatsPeriod.WEEK)
        cache.put(key, emptySnapshot())
        assertTrue(cache.isFresh(key))
        clock = LastFmStatsCache.TTL_MILLIS
        assertTrue(!cache.isFresh(key))
        assertEquals(emptySnapshot(), cache.get(key)?.snapshot)
    }

    private fun emptySnapshot() = LastFmStatsSnapshot(
        null, 0L, 0L, 0L, 0L, emptyList(), emptyList(), emptyList(), emptyList(),
    )

    private class FakeApi(
        val failTracks: Boolean = false,
        val failRecentList: Boolean = false,
    ) : LastFmStatsApi {
        val chartCalls = AtomicInteger()

        @Volatile var countFrom: Long? = null

        @Volatile var lastPeriod: String? = null

        override suspend fun profile(username: String) = Result.success(
            LastFM.UserStats("zen", null, 12_000L, 1_800L, 420L, 690L, null, 1_420_000_000L),
        )

        override suspend fun topArtists(username: String, period: String, limit: Int): Result<LastFM.PagedResult<LastFM.RankedArtist>> {
            chartCalls.incrementAndGet()
            lastPeriod = period
            return Result.success(
                LastFM.PagedResult(
                    listOf(LastFM.RankedArtist("Artist A", 40L, null), LastFM.RankedArtist("Silent", 0L, null)),
                    1,
                    31L,
                ),
            )
        }

        override suspend fun topTracks(username: String, period: String, limit: Int): Result<LastFM.PagedResult<LastFM.RankedTrack>> {
            chartCalls.incrementAndGet()
            if (failTracks) return Result.failure(IllegalStateException("offline"))
            return Result.success(LastFM.PagedResult(listOf(LastFM.RankedTrack("T", "Artist A", 9L, null)), 1, 97L))
        }

        override suspend fun topAlbums(username: String, period: String, limit: Int): Result<LastFM.PagedResult<LastFM.RankedAlbum>> {
            chartCalls.incrementAndGet()
            return Result.success(LastFM.PagedResult(listOf(LastFM.RankedAlbum("A", "Artist A", 5L, null)), 1, 58L))
        }

        override suspend fun recent(username: String, limit: Int, fromEpochSeconds: Long?): Result<LastFM.PagedResult<LastFM.RecentTrack>> {
            if (fromEpochSeconds != null) {
                countFrom = fromEpochSeconds
                return Result.success(LastFM.PagedResult(emptyList(), 412, 412L))
            }
            if (failRecentList) return Result.failure(IllegalStateException("offline"))
            return Result.success(LastFM.PagedResult(listOf(LastFM.RecentTrack("T", "Artist A", null, now())), 1, 1L))
        }

        private fun now() = 1_795_000_000L
    }
}
