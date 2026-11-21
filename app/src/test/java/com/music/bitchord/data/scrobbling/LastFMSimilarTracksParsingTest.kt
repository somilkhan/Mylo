package com.music.bitchord.data.scrobbling

import org.junit.Assert.assertEquals
import org.junit.Test

class LastFMSimilarTracksParsingTest {

    @Test
    fun parsesSimilarTrackArrayAndIgnoresIncompleteRows() {
        val result = parseLastFmSimilarTracks(
            """
            {
              "similartracks": {
                "track": [
                  {
                    "name": "Related Song",
                    "artist": {"name": "Related Artist"},
                    "playcount": "12",
                    "image": [{"#text": "https://example.com/art.jpg"}]
                  },
                  {"name": "Missing Artist"}
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals(1, result.size)
        assertEquals("Related Song", result.single().name)
        assertEquals("Related Artist", result.single().artist)
        assertEquals(12L, result.single().playCount)
        assertEquals("https://example.com/art.jpg", result.single().artworkUrl)
    }

    @Test
    fun parsesSingleSimilarTrackObject() {
        val result = parseLastFmSimilarTracks(
            """{"similartracks":{"track":{"name":"One","artist":"Artist"}}}""",
        )

        assertEquals(1, result.size)
        assertEquals("One", result.single().name)
        assertEquals("Artist", result.single().artist)
    }

    @Test
    fun malformedSimilarTrackPayloadReturnsEmptyList() {
        assertEquals(emptyList<LastFM.RankedTrack>(), parseLastFmSimilarTracks("not-json"))
    }
}
