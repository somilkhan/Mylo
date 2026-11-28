package com.music.bitchord.ui.scrobble

import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.scrobbling.LastFmStatisticsRepository
import com.music.bitchord.data.scrobbling.LastFmStatsApi
import com.music.bitchord.data.scrobbling.LastFmStatsCache
import com.music.bitchord.data.scrobbling.LastFmStatsPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScrobbleStatisticsViewModelTest {
    @Test fun returningToFreshCachedPeriodCancelsTheObsoleteRequest() = runTest {
        withFixture { fixture ->
            fixture.vm.onScreenOpened()
            advanceUntilIdle()
            fixture.api.holdPeriod = LastFmStatsPeriod.YEAR.apiValue
            fixture.vm.setPeriod(LastFmStatsPeriod.YEAR)
            runCurrent()
            fixture.vm.setPeriod(LastFmStatsPeriod.MONTH)
            runCurrent()
            assertEquals(1, fixture.api.chartCancellations)
            fixture.api.chartGate.complete(Unit)
            advanceUntilIdle()
            assertEquals(LastFmStatsPeriod.MONTH, fixture.vm.state.value.snapshotPeriod)
            assertFalse(fixture.vm.state.value.isSwitchingPeriod)
            assertFalse(fixture.vm.state.value.failed)
        }
    }

    @Test fun sameUsernameConfigurationChangeClearsOldSnapshotAndRecentRows() = runTest {
        withFixture { fixture ->
            fixture.vm.onScreenOpened()
            advanceUntilIdle()
            assertTrue(fixture.vm.state.value.recent.items.isNotEmpty())
            fixture.api.holdPeriod = LastFmStatsPeriod.MONTH.apiValue
            fixture.account = fixture.account.copy(configuration = "second")
            fixture.vm.onAccountChanged()
            assertNull(fixture.vm.state.value.snapshot)
            assertTrue(fixture.vm.state.value.recent.items.isEmpty())
            runCurrent()
            fixture.api.chartGate.complete(Unit)
            advanceUntilIdle()
            assertFalse(fixture.vm.state.value.failed)
            fixture.account = fixture.account.copy(username = "other")
            fixture.vm.onAccountChanged()
            assertNull(fixture.vm.state.value.snapshot)
            advanceUntilIdle()
            assertEquals("other", fixture.vm.state.value.snapshot?.profile?.username)
        }
    }

    @Test fun closingCancelsRecentAndReopeningCanContinueTheSamePage() = runTest {
        withFixture { fixture ->
            fixture.vm.onScreenOpened()
            advanceUntilIdle()
            fixture.api.holdRecentPage = 2
            fixture.vm.loadMoreRecent()
            runCurrent()
            assertTrue(fixture.vm.state.value.recent.loading)
            fixture.vm.onScreenClosed()
            runCurrent()
            assertEquals(1, fixture.api.recentCancellations)
            assertFalse(fixture.vm.state.value.recent.loading)
            fixture.vm.onScreenOpened()
            fixture.api.recentGate.complete(Unit)
            fixture.vm.loadMoreRecent()
            advanceUntilIdle()
            assertEquals(2, fixture.vm.state.value.recent.page)
            assertEquals(fixture.api.cutoffs.first(), fixture.api.cutoffs.last())
        }
    }

    @Test fun failedFirstPageRefreshRetriesFirstPageNotTheNextHistoryPage() = runTest {
        withFixture { fixture ->
            fixture.vm.onScreenOpened()
            advanceUntilIdle()
            fixture.vm.loadMoreRecent()
            advanceUntilIdle()
            assertEquals(2, fixture.vm.state.value.recent.page)
            fixture.api.failFirstPage = true
            fixture.vm.refresh()
            advanceUntilIdle()
            assertEquals(1, fixture.vm.state.value.recent.failedPage)
            assertEquals(2, fixture.vm.state.value.recent.page)
            fixture.api.failFirstPage = false
            fixture.vm.loadMoreRecent()
            advanceUntilIdle()
            assertEquals(1, fixture.vm.state.value.recent.page)
            assertFalse(fixture.vm.state.value.recent.failed)
        }
    }

    private suspend fun TestScope.withFixture(block: suspend TestScope.(Fixture) -> Unit) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val fixture = Fixture(dispatcher)
        try {
            block(fixture)
        } finally {
            fixture.vm.onScreenClosed()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private class Fixture(dispatcher: kotlinx.coroutines.CoroutineDispatcher) {
        var account = StatsAccount("listener", true, "first")
        val api = FakeApi()
        val repository = LastFmStatisticsRepository(
            api, LastFmStatsCache { 0L }, { 1_795_000_000L }, { "https://api" }, configuration = { account.configuration },
        )
        val vm = ScrobbleStatisticsViewModel(repository, { account }, dispatcher)
    }

    private class FakeApi : LastFmStatsApi {
        var holdPeriod: String? = null
        val chartGate = CompletableDeferred<Unit>()
        var chartCancellations = 0
        var holdRecentPage: Int? = null
        val recentGate = CompletableDeferred<Unit>()
        var recentCancellations = 0
        var failFirstPage = false
        val cutoffs = mutableListOf<Long?>()

        override suspend fun profile(username: String) = Result.success(
            LastFM.UserStats(username, null, 1_000L, 10L, 10L, 10L, null, 1_420_000_000L),
        )
        override suspend fun topArtists(username: String, period: String, limit: Int) =
            Result.success(LastFM.PagedResult<LastFM.RankedArtist>(emptyList(), 1, 10L))
        override suspend fun topAlbums(username: String, period: String, limit: Int) =
            Result.success(LastFM.PagedResult<LastFM.RankedAlbum>(emptyList(), 1, 10L))
        override suspend fun topTracks(username: String, period: String, limit: Int): Result<LastFM.PagedResult<LastFM.RankedTrack>> {
            if (period == holdPeriod) try {
                chartGate.await()
            } catch (cancelled: CancellationException) {
                chartCancellations++
                throw cancelled
            }
            return Result.success(LastFM.PagedResult(emptyList(), 1, 10L))
        }
        override suspend fun recent(
            username: String, limit: Int, fromEpochSeconds: Long?, toEpochSeconds: Long?, page: Int,
        ): Result<LastFM.PagedResult<LastFM.RecentTrack>> {
            if (fromEpochSeconds != null) return Result.success(LastFM.PagedResult(emptyList(), 1, 0L))
            cutoffs += toEpochSeconds
            if (page == holdRecentPage) try {
                recentGate.await()
            } catch (cancelled: CancellationException) {
                recentCancellations++
                throw cancelled
            }
            if (page == 1 && failFirstPage) return Result.failure(IllegalStateException("offline"))
            return Result.success(LastFM.PagedResult(listOf(LastFM.RecentTrack("Track $page", username, null, 1_795_000_000L - page)), 2, 2L))
        }
    }
}
