package com.music.bitchord.ui.screens

import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.RankedSong
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleStatsLogicTest {
    @Test
    fun lastSevenDaysUsesAnInclusiveRollingRangeAndOnlyLoadsOverlappingMonths() {
        val today = LocalDate.of(2026, 11, 15)
        val period = ReplayPeriod.LAST_7_DAYS

        assertEquals(today.minusDays(6) to today, period.range(today))
        assertEquals("Last 7 days", period.label(today))
        assertTrue(period.needsDayFilter())
        assertTrue(period.covers(YearMonth.of(2026, 11), today))
        assertFalse(period.covers(YearMonth.of(2026, 10), today))
    }

    @Test
    fun lastThirtyDaysIncludesBothSidesOfMonthBoundaryWithoutLeakingOlderDays() {
        val today = LocalDate.of(2026, 11, 5)
        val period = ReplayPeriod.LAST_30_DAYS

        assertEquals(today.minusDays(29) to today, period.range(today))
        assertEquals(LocalDate.of(2026, 10, 7) to today, period.range(today))
        assertEquals("Last 30 days", period.label(today))
        assertTrue(period.covers(YearMonth.of(2026, 10), today))
        assertTrue(period.covers(YearMonth.of(2026, 11), today))
        assertFalse(period.covers(YearMonth.of(2026, 9), today))
    }

    @Test
    fun groupsHourlyListeningIntoDaypartsAndPreservesTotal() {
        val parts = buildListeningDayParts((1L..24L).toList())

        assertEquals(listOf("Night", "Morning", "Afternoon", "Evening"), parts.map { it.name })
        assertEquals(listOf("00–05", "06–11", "12–17", "18–23"), parts.map { it.timeRange })
        assertEquals(listOf(21L, 57L, 93L, 129L), parts.map { it.listeningMs })
        assertEquals(300L, parts.sumOf { it.listeningMs })
    }

    @Test
    fun percentagesRepresentRecordedListeningTime() {
        val parts = buildListeningDayParts((1L..24L).toList())
        val total = parts.sumOf { it.listeningMs }

        assertEquals(listOf(7, 19, 31, 43), parts.map { it.percentageOf(total) })
    }

    @Test
    fun emptyAndShortHourlyDataAreSafe() {
        val empty = buildListeningDayParts(emptyList())
        assertEquals(listOf(0L, 0L, 0L, 0L), empty.map { it.listeningMs })
        assertEquals(listOf(0, 0, 0, 0), empty.map { it.percentageOf(0L) })

        val short = buildListeningDayParts(listOf(-5L, 5L, 10L))
        assertEquals(15L, short.first().listeningMs)
        assertEquals(listOf(0L, 0L, 0L), short.drop(1).map { it.listeningMs })
    }

    @Test
    fun rollingThreeSixAndTwelveMonthRangesIncludeTodayAndFilterOldMonths() {
        val today = LocalDate.of(2026, 11, 15)

        assertEquals(today.minusMonths(3).plusDays(1) to today, ReplayPeriod.LAST_3_MONTHS.range(today))
        assertEquals(today.minusMonths(6).plusDays(1) to today, ReplayPeriod.LAST_6_MONTHS.range(today))
        assertEquals(today.minusMonths(12).plusDays(1) to today, ReplayPeriod.LAST_12_MONTHS.range(today))

        assertEquals("Last 3 months", ReplayPeriod.LAST_3_MONTHS.label(today))
        assertEquals("Last 6 months", ReplayPeriod.LAST_6_MONTHS.label(today))
        assertEquals("Last 12 months", ReplayPeriod.LAST_12_MONTHS.label(today))

        assertTrue(ReplayPeriod.LAST_3_MONTHS.needsDayFilter())
        assertTrue(ReplayPeriod.LAST_3_MONTHS.covers(YearMonth.of(2026, 8), today))
        assertFalse(ReplayPeriod.LAST_3_MONTHS.covers(YearMonth.of(2026, 7), today))
        assertTrue(ReplayPeriod.LAST_6_MONTHS.covers(YearMonth.of(2026, 5), today))
        assertFalse(ReplayPeriod.LAST_6_MONTHS.covers(YearMonth.of(2026, 4), today))
        assertTrue(ReplayPeriod.LAST_12_MONTHS.covers(YearMonth.of(2025, 11), today))
        assertFalse(ReplayPeriod.LAST_12_MONTHS.covers(YearMonth.of(2025, 10), today))
    }


    @Test
    fun syncedHistoryCountsRepeatedRowsWithoutInventingDurations() {
        val first = com.music.bitchord.data.model.Song(
            videoId = "abcdefghijk",
            title = "First song",
            artist = "Artist A",
            thumbnailUrl = null,
            albumName = "Album A",
        )
        val second = com.music.bitchord.data.model.Song(
            videoId = "lmnopqrstuv",
            title = "Second song",
            artist = "Artist A",
            thumbnailUrl = null,
            albumName = "Album A",
        )
        val summary = buildSyncedHistorySummary(listOf(first, first, second))

        assertEquals(3, summary.totalPlays)
        assertEquals(2, summary.distinctTracks)
        assertEquals(1, summary.distinctArtists)
        assertEquals(1, summary.distinctAlbums)
        assertEquals(2, summary.topTracks.first().plays)
        assertEquals("First song", summary.topTracks.first().song.title)
        assertEquals(3, summary.topArtists.single().plays)
        assertEquals(null, summary.topArtists.single().artworkUrl)
    }

    @Test
    fun combinedFavouritesDeduplicateAcrossSourcesAndKeepLocalOnlyTracks() {
        val shared = com.music.bitchord.data.model.Song("abcdefghijk", "Shared", "Artist A", null)
        val localOnly = com.music.bitchord.data.model.Song("lmnopqrstuv", "Local only", "Artist B", null)
        val remoteOnly = com.music.bitchord.data.model.Song("zyxwvutsrqp", "Remote only", "Artist C", null)
        val result = buildCombinedFavouriteTracks(
            local = listOf(
                RankedSong(shared, ms = 900_000L, plays = 4),
                RankedSong(localOnly, ms = 600_000L, plays = 5),
            ),
            synced = listOf(
                SyncedHistoryTrack(shared, plays = 6),
                SyncedHistoryTrack(remoteOnly, plays = 3),
            ),
        )

        assertEquals(listOf(shared, localOnly, remoteOnly), result.take(3))
        assertEquals(3, result.distinctBy(::songIdentity).size)
        assertTrue(buildCombinedFavouriteTracks(emptyList(), emptyList()).isEmpty())
        assertTrue(buildCombinedFavouriteTracks(emptyList(), emptyList(), limit = 0).isEmpty())
    }

    @Test
    fun syncedHistoryUsesLeadArtistForCollaborationsAndRanksAlbums() {
        val first = com.music.bitchord.data.model.Song(
            videoId = "abcdefghijk",
            title = "First",
            artist = "Artist A, Artist B",
            thumbnailUrl = "https://example.com/album.jpg",
            albumName = "Shared album",
        )
        val second = com.music.bitchord.data.model.Song(
            videoId = "lmnopqrstuv",
            title = "Second",
            artist = "Artist A feat. Guest",
            thumbnailUrl = null,
            albumName = "Shared album",
        )

        val summary = buildSyncedHistorySummary(listOf(first, second))

        assertEquals(1, summary.distinctArtists)
        assertEquals("Artist A", summary.topArtists.single().name)
        assertEquals(2, summary.topArtists.single().plays)
        assertEquals("https://example.com/album.jpg", summary.topArtists.single().artworkUrl)
        assertEquals(1, summary.distinctAlbums)
        assertEquals("Shared album", summary.topAlbums.single().name)
        assertEquals("Artist A", summary.topAlbums.single().artist)
        assertEquals(2, summary.topAlbums.single().plays)
        assertEquals("https://example.com/album.jpg", summary.topAlbums.single().artworkUrl)
    }

    @Test
    fun syncedHistoryHandlesEmptyAndMissingAlbumMetadata() {
        val empty = buildSyncedHistorySummary(emptyList())
        assertEquals(0, empty.totalPlays)
        assertEquals(0, empty.distinctTracks)
        assertTrue(empty.topTracks.isEmpty())

        val noAlbum = com.music.bitchord.data.model.Song(
            videoId = "abcdefghijk",
            title = "No album",
            artist = "Unknown",
            thumbnailUrl = null,
        )
        val summary = buildSyncedHistorySummary(listOf(noAlbum))
        assertEquals(1, summary.totalPlays)
        assertEquals(0, summary.distinctAlbums)
    }

}
