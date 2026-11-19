package com.music.bitchord.data.scrobbling

import com.music.bitchord.data.stats.ReplayPeriod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

class LastFmStatisticsRepositoryTest {
    private val now = Instant.parse("2026-11-19T10:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun calendarPeriodsUseLocalBoundariesAndAllTimeUsesTopTracks() {
        val repository = LastFmStatisticsRepository(
            source = FakeSource(),
            cache = LastFmStatsCache { 0L },
            now = { now },
            zone = { zone },
        )

        val week = repository.query(" Zen ", ReplayPeriod.THIS_WEEK)
        val weekStart = LocalDate.of(2026, 11, 16).atStartOfDay(zone).toEpochSecond()
        assertEquals(weekStart, week.range?.startInclusiveEpochSeconds)
        assertEquals(weekStart - 1L, week.range?.requestFromEpochSeconds)
        assertEquals(now.epochSecond, week.range?.endInclusiveEpochSeconds)
        assertEquals(now.epochSecond + 1L, week.range?.requestToEpochSeconds)

        val month = repository.query("zen", ReplayPeriod.THIS_MONTH)
        assertEquals(
            LocalDate.of(2026, 11, 1).atStartOfDay(zone).toEpochSecond(),
            month.range?.startInclusiveEpochSeconds,
        )
        val year = repository.query("zen", ReplayPeriod.THIS_YEAR)
        assertEquals(
            LocalDate.of(2026, 1, 1).atStartOfDay(zone).toEpochSecond(),
            year.range?.startInclusiveEpochSeconds,
        )
        val allTime = repository.query("zen", ReplayPeriod.ALL_TIME)
        assertEquals(null, allTime.range)
        assertEquals("zen", allTime.key.username)
    }

    @Test
    fun fiveMinuteCacheSkipsFreshFetchButRetainsExpiredData() = runBlocking {
        val clock = AtomicLong(1_000L)
        val source = FakeSource()
        val repository = LastFmStatisticsRepository(
            source = source,
            cache = LastFmStatsCache { clock.get() },
            now = { now },
            zone = { zone },
        )
        val query = repository.query("zen", ReplayPeriod.ALL_TIME)

        val first = repository.refresh("zen", query, force = false).getOrThrow()
        assertEquals(1, source.calls)
        assertEquals(first, repository.refresh("zen", query, force = false).getOrThrow())
        assertEquals(1, source.calls)
        assertTrue(repository.isFresh(query))

        clock.addAndGet(LastFmStatsCache.TTL_MILLIS)
        assertFalse(repository.isFresh(query))
        assertEquals(first, repository.cached(query)?.tracks)
    }

    @Test
    fun forcedRefreshBypassesFreshCacheAndFailurePreservesStaleTracks() = runBlocking {
        val clock = AtomicLong(1_000L)
        val source = FakeSource()
        val repository = LastFmStatisticsRepository(
            source = source,
            cache = LastFmStatsCache { clock.get() },
            now = { now },
            zone = { zone },
        )
        val query = repository.query("zen", ReplayPeriod.ALL_TIME)
        val first = repository.refresh("zen", query, force = false).getOrThrow()
        source.tracks = listOf(LastFM.RankedTrack("New", "Artist", 8L, null))

        repository.refresh("zen", query, force = true).getOrThrow()
        assertEquals(2, source.calls)
        val fresh = repository.cached(query)?.tracks
        assertEquals(source.tracks, fresh)

        clock.addAndGet(LastFmStatsCache.TTL_MILLIS)
        source.failure = IllegalStateException("offline")
        val result = repository.refresh("zen", query, force = true)
        assertTrue(result.isFailure)
        assertEquals(fresh, repository.cached(query)?.tracks)
        assertFalse(repository.isFresh(query))
        assertEquals(first.size, 1)
    }

    @Test
    fun paginatesAllTimeAndCalendarHistoryAndFiltersOutsideTimestamps() = runBlocking {
        val api = FakeApi()
        val source = LastFmApiStatsDataSource(api = api, pageDelayMillis = 0L)

        val allTime = source.fetch("zen", ReplayPeriod.ALL_TIME, null)
        assertEquals(listOf(1, 2), api.topPages)
        assertEquals(5L, allTime.single().playCount)

        val range = LastFmStatsPeriodRange(
            requestFromEpochSeconds = 999L,
            requestToEpochSeconds = 2_001L,
            startInclusiveEpochSeconds = 1_000L,
            endInclusiveEpochSeconds = 2_000L,
        )
        val calendar = source.fetch("zen", ReplayPeriod.THIS_WEEK, range)
        assertEquals(listOf(1, 2), api.recentPages)
        assertEquals(2, calendar.size)
        assertEquals(2L, calendar.first { it.name == "Track A" }.playCount)
        assertEquals(1L, calendar.first { it.name == "Track B" }.playCount)
    }

    private class FakeSource : LastFmStatsDataSource {
        var calls = 0
        var failure: Exception? = null
        var tracks = listOf(LastFM.RankedTrack("Track", "Artist", 4L, null))

        override suspend fun fetch(
            username: String,
            period: ReplayPeriod,
            range: LastFmStatsPeriodRange?,
        ): List<LastFM.RankedTrack> {
            calls++
            failure?.let { throw it }
            return tracks
        }
    }

    private class FakeApi : LastFmStatsApi {
        val topPages = mutableListOf<Int>()
        val recentPages = mutableListOf<Int>()

        override suspend fun topTracks(
            username: String,
            page: Int,
            limit: Int,
        ): Result<LastFM.PagedResult<LastFM.RankedTrack>> {
            topPages += page
            val items = if (page == 1) {
                listOf(LastFM.RankedTrack("Track A", "Artist A", 2L, null))
            } else {
                listOf(LastFM.RankedTrack("track-a", "artist a", 3L, null))
            }
            return Result.success(LastFM.PagedResult(items, 2))
        }

        override suspend fun recentTracks(
            username: String,
            from: Long,
            to: Long,
            page: Int,
            limit: Int,
        ): Result<LastFM.PagedResult<LastFM.RecentTrack>> {
            recentPages += page
            val items = when (page) {
                1 -> listOf(
                    LastFM.RecentTrack("Track A", "Artist A", null, 999L),
                    LastFM.RecentTrack("Track A", "Artist A", null, 1_000L),
                    LastFM.RecentTrack("Track B", "Artist B", null, 1_500L),
                )
                else -> listOf(
                    LastFM.RecentTrack("track-a", "artist a", null, 2_000L),
                    LastFM.RecentTrack("Outside", "Artist C", null, 2_001L),
                )
            }
            return Result.success(LastFM.PagedResult(items, 2))
        }
    }
}
