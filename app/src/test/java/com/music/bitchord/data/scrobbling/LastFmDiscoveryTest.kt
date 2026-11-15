package com.music.bitchord.data.scrobbling

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFmDiscoveryTest {
    @Test
    fun parsesSimilarArtistArraysAndDeduplicatesNamesIgnoringCase() {
        val response = """
            {
              "similarartists": {
                "artist": [
                  {"name": "Artist One"},
                  {"name": "Artist Two"},
                  {"name": "artist one"},
                  {"name": ""}
                ]
              }
            }
        """.trimIndent()

        assertEquals(
            listOf("Artist One", "Artist Two"),
            parseLastFmSimilarArtists(response, limit = 8),
        )
    }

    @Test
    fun parsesSingleSimilarArtistObjectAndHonoursLimit() {
        val response = """{"similarartists":{"artist":{"name":"Solo Artist"}}}"""

        assertEquals(listOf("Solo Artist"), parseLastFmSimilarArtists(response))
        assertEquals(emptyList<String>(), parseLastFmSimilarArtists(response, limit = 0))
    }

    @Test
    fun parsesTagTracksWithObjectOrStringArtistAndSkipsIncompleteRows() {
        val response = """
            {
              "tracks": {
                "track": [
                  {"name":"Track One","artist":{"name":"Artist One"}},
                  {"name":"Track Two","artist":"Artist Two"},
                  {"name":"Track One","artist":{"name":"artist one"}},
                  {"name":"Missing artist","artist":{}},
                  {"artist":{"name":"Missing title"}}
                ]
              }
            }
        """.trimIndent()

        assertEquals(
            listOf(
                LastFM.DiscoveryTrack("Track One", "Artist One"),
                LastFM.DiscoveryTrack("Track Two", "Artist Two"),
            ),
            parseLastFmTagTracks(response, limit = 10),
        )
    }

    @Test
    fun parsesSimilarTrackArraysAndSingleObjectsWithDeduplication() {
        val response = """
            {
              "similartracks": {
                "track": [
                  {"name":"Track One","artist":{"name":"Artist One"}},
                  {"name":"Track Two","artist":"Artist Two"},
                  {"name":"Track One","artist":{"name":"artist one"}},
                  {"name":"Missing artist","artist":{}},
                  {"artist":{"name":"Missing title"}}
                ]
              }
            }
        """.trimIndent()

        assertEquals(
            listOf(
                LastFM.DiscoveryTrack("Track One", "Artist One"),
                LastFM.DiscoveryTrack("Track Two", "Artist Two"),
            ),
            parseLastFmSimilarTracks(response, limit = 10),
        )
        assertEquals(
            listOf(LastFM.DiscoveryTrack("Solo", "Artist")),
            parseLastFmSimilarTracks("""{"similartracks":{"track":{"name":"Solo","artist":"Artist"}}}"""),
        )
        assertEquals(emptyList<LastFM.DiscoveryTrack>(), parseLastFmSimilarTracks(response, limit = 0))
    }

    @Test
    fun discoveryEndpointsFailClearlyWithoutApiCredentials() = runBlocking {
        val previous = LastFM.currentConfig()
        try {
            LastFM.configure(
                endpoint = LastFM.DEFAULT_API_ENDPOINT,
                apiKey = "",
                secret = "",
                sessionKey = null,
            )

            assertTrue(LastFM.getSimilarArtists("Artist").isFailure)
            assertTrue(LastFM.getSimilarTracks("Track", "Artist").isFailure)
            assertTrue(LastFM.getTagTopTracks("rock").isFailure)
            assertTrue(LastFM.getArtistTopTracks("Artist").isFailure)
        } finally {
            LastFM.configure(
                endpoint = previous.endpoint,
                apiKey = previous.apiKey,
                secret = previous.secret,
                sessionKey = previous.sessionKey,
            )
        }
    }

    @Test
    fun missingDiscoveryNodesReturnEmptyLists() {
        assertEquals(emptyList<String>(), parseLastFmSimilarArtists("""{"similarartists":{}}"""))
        assertEquals(emptyList<LastFM.DiscoveryTrack>(), parseLastFmSimilarTracks("""{"similartracks":{}}"""))
        assertEquals(emptyList<LastFM.DiscoveryTrack>(), parseLastFmTagTracks("""{"tracks":{}}"""))
    }
    @Test
    fun parsesLastFmProfileTotalsAndArtwork() {
        val stats = parseLastFmUserStats(
            """{"user":{"playcount":"1200","track_count":"450","artist_count":"120","album_count":"90","image":[{"#text":"https://img/small.jpg","size":"small"},{"#text":"https://img/large.jpg","size":"large"}]}}""",
        )
        assertEquals(1200L, stats?.playCount)
        assertEquals(450L, stats?.trackCount)
        assertEquals(120L, stats?.artistCount)
        assertEquals(90L, stats?.albumCount)
        assertEquals("https://img/large.jpg", stats?.avatarUrl)
    }

    @Test
    fun parsesLastFmTopChartsForArrayAndSingleObjectPayloads() {
        val tracks = parseLastFmTopTracks(
            """{"toptracks":{"track":[{"name":"Track A","playcount":"12","artist":{"name":"Artist A"}},{"name":"Track B","playcount":"4","artist":"Artist B"}]}}""",
        )
        assertEquals(listOf("Track A", "Track B"), tracks.map { it.name })
        assertEquals(12L, tracks.first().playCount)

        val artists = parseLastFmTopArtists(
            """{"topartists":{"artist":{"name":"Artist A","playcount":"20","image":[{"#text":"https://img/a.jpg","size":"extralarge"}]}}}""",
        )
        assertEquals(1, artists.size)
        assertEquals("Artist A", artists.single().name)
        assertEquals("https://img/a.jpg", artists.single().artworkUrl)

        val albums = parseLastFmTopAlbums(
            """{"topalbums":{"album":{"name":"Album A","playcount":"8","artist":{"name":"Artist A"}}}}""",
        )
        assertEquals(1, albums.size)
        assertEquals("Artist A", albums.single().artist)
        assertEquals(8L, albums.single().playCount)
    }

    @Test
    fun malformedOrEmptyLastFmStatsPayloadsDoNotInventEntries() {
        assertEquals(null, parseLastFmUserStats("""{"error":6,"message":"User not found"}"""))
        assertEquals(emptyList<LastFM.RankedTrack>(), parseLastFmTopTracks("""{"toptracks":{"track":[]}}"""))
        assertEquals(emptyList<LastFM.RankedArtist>(), parseLastFmTopArtists("""{}"""))
        assertEquals(emptyList<LastFM.RankedAlbum>(), parseLastFmTopAlbums("""{"topalbums":{"album":{}}}"""))
    }
}
