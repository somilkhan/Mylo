package com.music.bitchord.data.generate

import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.scrobbling.parseLastFmArtistTopTracks
import com.music.bitchord.data.scrobbling.parseLastFmTagTopTracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFmGenerationRulesTest {

    private data class Row(val artist: String, val title: String)

    private fun key(row: Row) = LastFmGenerationRules.trackKey(row.artist, row.title)

    @Test
    fun interleaveUniqueRoundRobinsAcrossPools() {
        val pools = listOf(
            listOf(Row("A", "a1"), Row("A", "a2"), Row("A", "a3")),
            listOf(Row("B", "b1"), Row("B", "b2")),
            listOf(Row("C", "c1")),
        )
        val result = LastFmGenerationRules.interleaveUnique(pools, ::key, cap = 100)
        assertEquals(
            listOf("a1", "b1", "c1", "a2", "b2", "a3"),
            result.map { it.title },
        )
    }

    @Test
    fun interleaveUniqueDedupesByArtistAndTitleIgnoringCaseAndSpacing() {
        val pools = listOf(
            listOf(Row("Artist", "Song"), Row("Other", "Track")),
            listOf(Row("  artist ", "SONG  "), Row("Other", "Other Track")),
        )
        val result = LastFmGenerationRules.interleaveUnique(pools, ::key, cap = 100)
        assertEquals(listOf("Song", "Track", "Other Track"), result.map { it.title })
    }

    @Test
    fun interleaveUniqueStopsAtCapAndHandlesEmptyInput() {
        val pools = listOf(
            (1..5).map { Row("A", "a$it") },
            (1..5).map { Row("B", "b$it") },
        )
        assertEquals(4, LastFmGenerationRules.interleaveUnique(pools, ::key, cap = 4).size)
        assertTrue(LastFmGenerationRules.interleaveUnique(emptyList<List<Row>>(), ::key, cap = 4).isEmpty())
        assertTrue(LastFmGenerationRules.interleaveUnique(pools, ::key, cap = 0).isEmpty())
    }

    @Test
    fun excludeRecentRemovesMatchesAndKeepsTopOrder() {
        val top = listOf(Row("Zed", "Zero"), Row("Amy", "Alpha"), Row("Bob", "Beta"), Row("Cy", "Gamma"))
        val recent = setOf(LastFmGenerationRules.trackKey("amy", "alpha"), LastFmGenerationRules.trackKey("Cy", "Gamma"))
        val result = LastFmGenerationRules.excludeRecent(top, recent, ::key)
        assertEquals(listOf("Zero", "Beta"), result.map { it.title })
    }

    @Test
    fun trackKeyNormalizesCaseAndWhitespace() {
        assertEquals(
            LastFmGenerationRules.trackKey("Radiohead", "Creep"),
            LastFmGenerationRules.trackKey("  radiohead ", "creep   "),
        )
        assertTrue(
            LastFmGenerationRules.trackKey("Radiohead", "Creep") !=
                LastFmGenerationRules.trackKey("Radiohead", "Karma Police"),
        )
    }

    @Test
    fun recentWindowIsThirtyDaysEndingNow() {
        val now = 1_700_000_000_123L
        val (from, to) = LastFmGenerationRules.recentWindowSeconds(now)
        assertEquals(1_700_000_000L, to)
        assertEquals(1_700_000_000L - 30L * 86_400L, from)
    }

    @Test
    fun parsesTagTopTracksFromSingleAndArrayRows() {
        val raw = """
            {"tracks":{"track":[
              {"name":"Teardrop","playcount":"900","artist":{"name":"Massive Attack"}},
              {"name":"Roads","playcount":"500","artist":{"name":"Portishead"}}
            ]},"@attr":{"tag":"trip hop"}}
        """.trimIndent()
        val tracks = parseLastFmTagTopTracks(raw)
        assertEquals(listOf("Teardrop", "Roads"), tracks.map { it.name })
        assertEquals("Portishead", tracks[1].artist)
        assertEquals(900L, tracks[0].playCount)
    }

    @Test
    fun parsesArtistTopTracksAndFallsBackToRequestedArtist() {
        val raw = """
            {"toptracks":{"track":{"name":"Solo Song","playcount":"42"}}}
        """.trimIndent()
        val tracks: List<LastFM.RankedTrack> = parseLastFmArtistTopTracks(raw, fallbackArtist = "Requested Artist")
        assertEquals(1, tracks.size)
        assertEquals("Requested Artist", tracks[0].artist)
        assertEquals(42L, tracks[0].playCount)
    }

    @Test
    fun emptyOrMissingChartsParseToEmptyLists() {
        assertTrue(parseLastFmTagTopTracks("""{"tracks":{"track":[]}}""").isEmpty())
        assertTrue(parseLastFmArtistTopTracks("""{}""", fallbackArtist = "X").isEmpty())
    }
}
