package com.music.bitchord.data.scrobbling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFMStatsParserTest {

    @Test
    fun parsesRemoteUserTotalsAndLargestAvailableAvatar() {
        val raw = """
            {
              "user": {
                "name": "zen",
                "realname": "Zen",
                "playcount": "12000",
                "track_count": "1800",
                "artist_count": "420",
                "album_count": "690",
                "image": [
                  {"size":"small","#text":"https://img.test/small.jpg"},
                  {"size":"large","#text":"https://img.test/large.jpg"},
                  {"size":"mega","#text":"https://img.test/mega.jpg"}
                ]
              }
            }
        """.trimIndent()

        val profile = parseLastFmUserStats(raw)

        assertEquals("zen", profile?.username)
        assertEquals("Zen", profile?.realName)
        assertEquals(12_000L, profile?.playCount)
        assertEquals(1_800L, profile?.trackCount)
        assertEquals(420L, profile?.artistCount)
        assertEquals(690L, profile?.albumCount)
        assertEquals("https://img.test/mega.jpg", profile?.avatarUrl)
    }

    @Test
    fun parsesTrackArtistAndAlbumChartsWithPlayCounts() {
        val tracks = parseLastFmTopTracks(
            """
                {"toptracks":{"track":[
                  {"name":"Track A","artist":{"name":"Artist A"},"playcount":"45","image":[{"#text":"https://img.test/a.jpg"}]},
                  {"name":"Track B","artist":"Artist B","playcount":"12"}
                ]}}
            """.trimIndent(),
        )
        val artists = parseLastFmTopArtists(
            """{"topartists":{"artist":{"name":"Artist A","playcount":"90"}}}""",
        )
        val albums = parseLastFmTopAlbums(
            """{"topalbums":{"album":[{"name":"Album A","artist":{"name":"Artist A"},"playcount":"22"}]}}""",
        )

        assertEquals(2, tracks.size)
        assertEquals("Artist A", tracks.first().artist)
        assertEquals(45L, tracks.first().playCount)
        assertEquals("https://img.test/a.jpg", tracks.first().artworkUrl)
        assertEquals("Artist A", artists.single().name)
        assertEquals(90L, artists.single().playCount)
        assertEquals("Album A", albums.single().name)
        assertEquals("Artist A", albums.single().artist)
        assertEquals(22L, albums.single().playCount)
    }

    @Test
    fun malformedOrMissingChartPayloadsReturnEmptyResults() {
        assertNull(parseLastFmUserStats("not-json"))
        assertTrue(parseLastFmTopTracks("""{"toptracks":{}}""").isEmpty())
        assertTrue(parseLastFmTopArtists("[]").isEmpty())
        assertTrue(parseLastFmTopAlbums("""{"topalbums":{"album":null}}""").isEmpty())
    }
}
