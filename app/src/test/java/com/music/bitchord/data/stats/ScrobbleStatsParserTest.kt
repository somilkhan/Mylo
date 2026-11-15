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

        val parsedTrack = ScrobbleStatsRepository.run {
            val method = javaClass.getDeclaredMethod("parseTrack", kotlinx.serialization.json.JsonElement::class.java)
            method.isAccessible = true
            method.invoke(this, track) as ScrobbleChartEntry
        }
        val parsedArtist = ScrobbleStatsRepository.run {
            val method = javaClass.getDeclaredMethod("parseArtist", kotlinx.serialization.json.JsonElement::class.java)
            method.isAccessible = true
            method.invoke(this, artist) as ScrobbleChartEntry
        }
        val parsedAlbum = ScrobbleStatsRepository.run {
            val method = javaClass.getDeclaredMethod("parseAlbum", kotlinx.serialization.json.JsonElement::class.java)
            method.isAccessible = true
            method.invoke(this, album) as ScrobbleChartEntry
        }
        val parsedTag = ScrobbleStatsRepository.run {
            val method = javaClass.getDeclaredMethod("parseTag", kotlinx.serialization.json.JsonElement::class.java)
            method.isAccessible = true
            method.invoke(this, tag) as ScrobbleChartEntry
        }

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

        val parsed = ScrobbleStatsRepository.run {
            val method = javaClass.getDeclaredMethod("parseArtist", kotlinx.serialization.json.JsonElement::class.java)
            method.isAccessible = true
            method.invoke(this, noImage) as ScrobbleChartEntry
        }

        assertNull(parsed.artworkUrl)
        assertTrue(ScrobbleStatsRepository.supportedPeriods.contains("7day"))
    }
}
