package com.music.bitchord.ui.scrobble

import com.music.bitchord.data.scrobbling.LastFmStatsPeriod
import com.music.bitchord.data.scrobbling.LastFmStatsSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleStatisticsUiStateTest {
    private val snapshot = LastFmStatsSnapshot(
        null, 10L, 1L, 1L, 1L, emptyList(), emptyList(), emptyList(), emptyList(),
    )

    @Test
    fun anOlderPeriodsSnapshotReadsAsSwitchingNotAsThisPeriodsData() {
        val state = ScrobbleStatisticsUiState(
            period = LastFmStatsPeriod.YEAR,
            connected = true,
            snapshot = snapshot,
            snapshotPeriod = LastFmStatsPeriod.WEEK,
        )
        assertTrue(state.isSwitchingPeriod)
        assertFalse(state.showSkeleton)
    }

    @Test
    fun skeletonOnlyWhileConnectedWithNothingToShowAndNoFailure() {
        assertTrue(ScrobbleStatisticsUiState(connected = true).showSkeleton)
        assertFalse(ScrobbleStatisticsUiState(connected = false).showSkeleton)
        assertFalse(ScrobbleStatisticsUiState(connected = true, failed = true).showSkeleton)
    }

    @Test
    fun aPeriodWithNoScrobblesIsEmptyRatherThanFailed() {
        val empty = snapshot.copy(scrobbles = 0L)
        assertTrue(empty.isEmpty)
        assertFalse(snapshot.isEmpty)
    }
}
