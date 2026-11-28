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
        assertEquals(168L, snapshot.scrobbles)
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
    fun aFailedChartFailsTheSnapshot() = runBlocking {
        val failingChart = LastFmStatisticsRepository(
            FakeApi(failTracks = true), LastFmStatsCache { 0L }, { now }, { "https://api" },
        )
        assertTrue(failingChart.load("zen", LastFmStatsPeriod.MONTH, false).isFailure)
    }

    @Test
    fun recentPagesAskForTheirPageAndOnlyTheFirstKeepsNowPlaying() = runBlocking {
        val api = FakeApi()
        val repository = LastFmStatisticsRepository(api, LastFmStatsCache { 0L }, { now }, { "https://api" })

        val first = repository.recentPage(" zen ", page = 1).getOrThrow()
        assertEquals(1, api.lastRecentPage)
        assertEquals(LastFmStatisticsRepository.RECENT_PAGE_SIZE, api.lastRecentLimit)
        assertTrue(first.items.first().nowPlaying)
        assertEquals(3, first.totalPages)

        val second = repository.recentPage("zen", page = 2).getOrThrow()
        assertEquals(2, api.lastRecentPage)
        assertTrue(second.items.none { it.nowPlaying })
        assertEquals(1, second.items.size)
    }

    @Test
    fun aFailedRecentPageFailsOnItsOwn() = runBlocking {
        val repository = LastFmStatisticsRepository(
            FakeApi(failRecentList = true), LastFmStatsCache { 0L }, { now }, { "https://api" },
        )
        assertTrue(repository.recentPage("zen", 1).isFailure)
        assertTrue(repository.load("zen", LastFmStatsPeriod.MONTH, false).isSuccess)
    }

    @Test
    fun cacheKeysSeparateAccountsEndpointsAndPeriods() {
        val repository = LastFmStatisticsRepository(
            FakeApi(), LastFmStatsCache { 0L }, { now }, { " https://api " }, configuration = { "" },
        )
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

    @Test
    fun rollingActivityTilesThePeriodWithoutOverlapAndComparesWithTheWindowBefore() = runBlocking {
        val api = FakeApi()
        val repository = LastFmStatisticsRepository(api, LastFmStatsCache { 0L }, { now }, { "https://api" })

        val snapshot = repository.load("zen", LastFmStatsPeriod.MONTH, false).getOrThrow()

        val bars = snapshot.activity
        assertEquals(10, bars.size)
        assertEquals(now - 30 * 86_400L, bars.first().startEpochSeconds)
        assertEquals(now - 1L, bars.last().endEpochSeconds)
        bars.zipWithNext { a, b -> assertEquals(a.endEpochSeconds + 1, b.startEpochSeconds) }
        assertEquals(30 * 24L, bars.sumOf { it.scrobbles })
        assertEquals(snapshot.scrobbles, bars.sumOf { it.scrobbles })
        // The previous 30 days end the second before this window starts.
        assertEquals((now - 60 * 86_400L)..(now - 30 * 86_400L - 1), api.windows.first { it.last < now - 30 * 86_400L })
        assertEquals(30 * 24L, snapshot.previousScrobbles)
    }

    @Test
    fun oneFailedSliceDropsTheWholeChartButNotTheSnapshot() = runBlocking {
        val repository = LastFmStatisticsRepository(
            FakeApi(failActivity = true), LastFmStatsCache { 0L }, { now }, { "https://api" },
        )
        val snapshot = repository.load("zen", LastFmStatsPeriod.WEEK, false).getOrThrow()
        assertTrue(snapshot.activity.isEmpty())
        assertEquals(168L, snapshot.scrobbles)
    }

    @Test
    fun allTimeActivityGoesByCalendarYearFromRegistration() {
        val zone = java.time.ZoneId.of("UTC")
        val registered = java.time.LocalDate.of(2021, 6, 15).atStartOfDay(zone).toEpochSecond()
        val nowUtc = java.time.LocalDate.of(2026, 11, 28).atStartOfDay(zone).toEpochSecond()
        val years = activityWindows(LastFmStatsPeriod.OVERALL, nowUtc, registered, zone)
        assertEquals(6, years.size)
        assertEquals(registered, years.first().first)
        assertEquals(java.time.LocalDate.of(2022, 1, 1).atStartOfDay(zone).toEpochSecond() - 1, years.first().last)
        assertEquals(nowUtc, years.last().last)
        assertTrue(activityWindows(LastFmStatsPeriod.OVERALL, nowUtc, 0L, zone).isEmpty())
    }

    @Test
    fun changedConfigurationCannotReuseOrPublishTheOldSnapshot() = runBlocking {
        var config = "first"
        val cache = LastFmStatsCache { 0L }
        val api = object : LastFmStatsApi by FakeApi() {
            override suspend fun profile(username: String): Result<LastFM.UserStats> {
                config = "second"
                return FakeApi().profile(username)
            }
        }
        val repository = LastFmStatisticsRepository(api, cache, { now }, { "https://api" }, configuration = { config })
        val oldKey = repository.key("zen", LastFmStatsPeriod.MONTH)
        assertTrue(repository.load("zen", LastFmStatsPeriod.MONTH, false).isFailure)
        assertNull(repository.cached(oldKey))
        assertTrue(oldKey != repository.key("zen", LastFmStatsPeriod.MONTH))
    }

    @Test
    fun credentialsWithTheSameJvmHashStillHaveDifferentCacheIdentities() {
        val first = LastFM.RuntimeConfig("https://api", "Aa", "secret", "session")
        val second = first.copy(apiKey = "BB")
        assertEquals(first.apiKey.hashCode(), second.apiKey.hashCode())
        assertTrue(statsConfigurationFingerprint(first) != statsConfigurationFingerprint(second))
        assertTrue(statsConfigurationFingerprint(first) != statsConfigurationFingerprint(first.copy(sessionKey = "other")))
    }

    @Test
    fun everyRollingPeriodHasExactlyItsDurationAndNoSharedSecond() {
        LastFmStatsPeriod.entries.filter { it.days != null }.forEach { period ->
            val windows = activityWindows(period, now, 0L, java.time.ZoneId.of("America/New_York"))
            assertEquals(period.days!! * 86_400L, windows.sumOf { it.last - it.first + 1L })
            assertEquals(now - 1L, windows.last().last)
            windows.zipWithNext { left, right -> assertEquals(left.last + 1L, right.first) }
        }
    }

    private fun emptySnapshot() = LastFmStatsSnapshot(
        null, 0L, 0L, 0L, 0L, emptyList(), emptyList(), emptyList(), emptyList(),
    )

    private class FakeApi(
        val failTracks: Boolean = false,
        val failRecentList: Boolean = false,
        val failActivity: Boolean = false,
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

        val windows = java.util.concurrent.ConcurrentLinkedQueue<LongRange>()

        @Volatile var lastRecentPage = 0

        @Volatile var lastRecentLimit = 0

        override suspend fun recent(
            username: String,
            limit: Int,
            fromEpochSeconds: Long?,
            toEpochSeconds: Long?,
            page: Int,
        ): Result<LastFM.PagedResult<LastFM.RecentTrack>> {
            if (fromEpochSeconds != null && toEpochSeconds != null) {
                windows += fromEpochSeconds..toEpochSeconds
                if (toEpochSeconds == now() - 1L && fromEpochSeconds <= now() - 7 * 86_400L) countFrom = fromEpochSeconds
                if (failActivity && toEpochSeconds == now() - 1L && toEpochSeconds - fromEpochSeconds < 2 * 86_400L) {
                    return Result.failure(IllegalStateException("429"))
                }
                // One scrobble per hour of the window, so slices sum to the period.
                val count = (toEpochSeconds - fromEpochSeconds + 1) / 3_600L
                return Result.success(LastFM.PagedResult(emptyList(), 1, count))
            }
            if (fromEpochSeconds != null) {
                countFrom = fromEpochSeconds
                return Result.success(LastFM.PagedResult(emptyList(), 412, 412L))
            }
            if (failRecentList) return Result.failure(IllegalStateException("offline"))
            lastRecentPage = page
            lastRecentLimit = limit
            // Last.fm puts the playing track on top of every page.
            val playing = LastFM.RecentTrack("Now", "Artist A", null, 0L, nowPlaying = true)
            return Result.success(
                LastFM.PagedResult(listOf(playing, LastFM.RecentTrack("T", "Artist A", null, now() - page)), 3, 150L),
            )
        }

        private fun now() = 1_795_000_000L
    }
}
