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
            assertTrue(LastFM.getTagTopTracks("rock").isFailure)
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
        assertEquals(emptyList<LastFM.DiscoveryTrack>(), parseLastFmTagTracks("""{"tracks":{}}"""))
    }
}
