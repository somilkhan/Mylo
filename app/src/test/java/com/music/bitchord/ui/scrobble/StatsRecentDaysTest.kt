package com.music.bitchord.ui.scrobble

import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.scrobbling.LastFmActivityBucket
import com.music.bitchord.ui.scrobble.stats.activityBucketDates
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class StatsRecentDaysTest {
    private val zone: ZoneId = ZoneOffset.ofHoursMinutes(5, 30)
    private val today = LocalDate.of(2026, 11, 28)

    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toEpochSecond()

    @Test
    fun scrobblesGoUnderTheDayTheyWerePlayedOnInTheListenersZone() {
        val tracks = listOf(
            LastFM.RecentTrack("Playing", "A", null, 0L, nowPlaying = true),
            LastFM.RecentTrack("Morning", "A", null, at(today, 1)),
            LastFM.RecentTrack("Late", "A", null, at(today.minusDays(1), 23)),
            LastFM.RecentTrack("Early", "A", null, at(today.minusDays(1), 0)),
            LastFM.RecentTrack("Old", "A", null, at(today.minusDays(9), 12)),
        )

        val entries = groupRecentByDay(tracks, zone, today)

        val shape = entries.map {
            when (it) {
                is RecentEntry.Day -> "# ${it.date}"
                is RecentEntry.Scrobble -> it.track.name
            }
        }
        assertEquals(
            listOf("# 2026-11-28", "Playing", "Morning", "# 2026-11-27", "Late", "Early", "# 2026-11-19", "Old"),
            shape,
        )
    }

    @Test
    fun keysStayUniqueWhenTheSameTrackIsPlayedTwice() {
        val tracks = listOf(
            LastFM.RecentTrack("Same", "A", null, at(today, 3)),
            LastFM.RecentTrack("Same", "A", null, at(today, 3)),
        )
        val keys = groupRecentByDay(tracks, zone, today).map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun pageOverlapUsesArtistAndAlbumNotJustTitleAndTimestamp() {
        val first = LastFM.RecentTrack("Same", "A", null, at(today, 3), album = "One")
        val otherArtist = first.copy(artist = "B")
        val otherAlbum = first.copy(album = "Two")
        assertEquals(listOf(first, otherArtist, otherAlbum), mergeRecentTracks(listOf(first), listOf(first, otherArtist, otherAlbum)))
    }

    @Test
    fun deduplicationPreservesRepeatedOccurrencesInsideTheNewPage() {
        val track = LastFM.RecentTrack("Same", "A", null, at(today, 3))
        assertEquals(listOf(track, track), mergeRecentTracks(listOf(track), listOf(track, track)))
    }

    @Test
    fun outOfOrderBackfillCannotCreateDuplicateDayKeys() {
        val tracks = listOf(
            LastFM.RecentTrack("New", "A", null, at(today, 3)),
            LastFM.RecentTrack("Old", "A", null, at(today.minusDays(1), 3)),
            LastFM.RecentTrack("Backfilled", "A", null, at(today, 2)),
        )
        val entries = groupRecentByDay(tracks, zone, today)
        assertEquals(entries.size, entries.map { it.key }.toSet().size)
        assertEquals(2, entries.filterIsInstance<RecentEntry.Day>().size)
    }

    @Test
    fun elapsedTimeBucketLabelsRetainBothDatesAcrossMidnightAndYearBoundary() {
        val start = at(LocalDate.of(2025, 12, 31), 12)
        val bucket = LastFmActivityBucket(start, start + 86_399L, 10L)
        assertEquals(LocalDate.of(2025, 12, 31) to LocalDate.of(2026, 1, 1), activityBucketDates(bucket, zone))
    }
}
