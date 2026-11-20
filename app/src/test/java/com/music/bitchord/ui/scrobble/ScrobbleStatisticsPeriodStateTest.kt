package com.music.bitchord.ui.scrobble

import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.stats.ReplayPeriod
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrobbleStatisticsPeriodStateTest {
    @Test
    fun periodChangeKeepsPreviousSnapshotWithoutMixingNewPeriodCache() {
        val previous = MergedStatisticsTrack(
            identity = "old-track",
            title = "Old period track",
            artist = "Old artist",
            artworkUrl = null,
            song = null,
            localPlays = 4L,
            lastFmPlays = 0L,
            plays = 4L,
            localListeningMs = 120_000L,
        )
        val state = ScrobbleStatisticsUiState(
            period = ReplayPeriod.THIS_YEAR,
            tracks = listOf(previous),
            loadedPeriod = ReplayPeriod.THIS_YEAR,
        )
        val newPeriodCache = listOf(
            LastFM.RankedTrack("New period track", "New artist", 9L, null),
        )

        val selected = existingStatisticsTracksForPeriod(
            snapshot = state,
            period = ReplayPeriod.THIS_MONTH,
            cachedTracks = newPeriodCache,
            connected = true,
        )

        assertEquals(listOf(previous), selected)
    }

    @Test
    fun firstLoadCanUseMatchingPeriodCacheBeforeLocalSummaryFinishes() {
        val cached = listOf(
            LastFM.RankedTrack("Cached track", "Cached artist", 7L, null),
        )
        val state = ScrobbleStatisticsUiState(period = ReplayPeriod.ALL_TIME)

        val selected = existingStatisticsTracksForPeriod(
            snapshot = state,
            period = ReplayPeriod.ALL_TIME,
            cachedTracks = cached,
            connected = true,
        )

        assertEquals(1, selected.size)
        assertEquals("Cached track", selected.single().title)
        assertEquals(7L, selected.single().plays)
    }
}
