package com.music.bitchord.data.stats

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleStatsParserTest {
    @Test
    fun parsesTrackArtistAlbumAndTagChartRows() {
        val track = Json.parseToJsonElement(
            """{"name":"Blue Monday","artist":{"name":"New Order"},"playcount":"120","listeners":"40","album":{"#text":"Power, Corruption & Lies"},"image":[{"size":"large","#text":"https://img.test/cover.jpg"}]}""",
        )
        val artist = Json.parseToJsonElement(
            """{"name":"New Order","playcount":"500","listeners":"200","image":[{"size":"extralarge","#text":"https://img.test/artist.jpg"}]}""",
        )
        val album = Json.parseToJsonElement(
            """{"name":"Power, Corruption & Lies","artist":{"name":"New Order"},"playcount":"90"}""",
        )
        val tag = Json.parseToJsonElement("""{"name":"post-punk","count":"12"}""")

        val parsedTrack = ScrobbleStatsRepository.parseTrack(track)!!
        val parsedArtist = ScrobbleStatsRepository.parseArtist(artist)!!
        val parsedAlbum = ScrobbleStatsRepository.parseAlbum(album)!!
        val parsedTag = ScrobbleStatsRepository.parseTag(tag)!!

        assertEquals("New Order", parsedTrack.artist)
        assertEquals("Power, Corruption & Lies", parsedTrack.album)
        assertEquals(120L, parsedTrack.playCount)
        assertEquals("https://img.test/cover.jpg", parsedTrack.artworkUrl)
        assertEquals(500L, parsedArtist.playCount)
        assertEquals("https://img.test/artist.jpg", parsedArtist.artworkUrl)
        assertEquals("New Order", parsedAlbum.artist)
        assertEquals(12L, parsedTag.playCount)
    }

    @Test
    fun ignoresMalformedRowsAndMissingArtwork() {
        val malformed = Json.parseToJsonElement("""{"artist":"No title"}""")
        val noImage = Json.parseToJsonElement("""{"name":"Unknown","playcount":"3"}""")

        assertNull(ScrobbleStatsRepository.parseArtist(malformed))
        assertNull(ScrobbleStatsRepository.parseArtist(noImage)?.artworkUrl)
        assertTrue(ScrobbleStatsRepository.supportedPeriods.contains("7day"))
    }
}
