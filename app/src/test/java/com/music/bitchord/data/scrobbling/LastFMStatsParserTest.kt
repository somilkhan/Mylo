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
    fun parsesSimilarArtistsFromArrayAndDeduplicatesNames() {
        val artists = parseLastFmSimilarArtists(
            """{"similarartists":{"artist":[
                {"name":"Artist A","match":"0.9"},
                {"name":"artist a","match":"0.7"},
                {"name":"Artist B","match":"0.5"}
            ]}}""",
        )

        assertEquals(listOf("Artist A", "Artist B"), artists)
    }

    @Test
    fun parsesSingleSimilarArtistAndRejectsMalformedPayloads() {
        assertEquals(
            listOf("Artist A"),
            parseLastFmSimilarArtists("""{"similarartists":{"artist":{"name":"Artist A"}}}"""),
        )
        assertTrue(parseLastFmSimilarArtists("not-json").isEmpty())
        assertTrue(parseLastFmSimilarArtists("""{"similarartists":{}}""").isEmpty())
    }

    @Test
    fun malformedOrMissingChartPayloadsReturnEmptyResults() {
        assertNull(parseLastFmUserStats("not-json"))
        assertTrue(parseLastFmTopTracks("""{"toptracks":{}}""").isEmpty())
        assertTrue(parseLastFmTopArtists("[]").isEmpty())
        assertTrue(parseLastFmTopAlbums("""{"topalbums":{"album":null}}""").isEmpty())
    }
    @Test
    fun parsesPagedTopTracksAndRecentScrobbles() {
        val top = parseLastFmTopTracksPage(
            """
            {
              "toptracks": {
                "track": [
                  {"name":"Track A","playcount":"42","artist":{"name":"Artist A"}}
                ],
                "@attr": {"page":"1","totalPages":"3"}
              }
            }
            """.trimIndent(),
        )
        assertEquals(3, top.totalPages)
        assertEquals("Track A", top.items.single().name)
        assertEquals(42L, top.items.single().playCount)

        val recent = parseLastFmRecentTracksPage(
            """
            {
              "recenttracks": {
                "track": [
                  {"name":"Track A","artist":{"#text":"Artist A"},"date":{"uts":"1795082400"}},
                  {"name":"Now Playing","artist":{"#text":"Artist B"},"@attr":{"nowplaying":"true"}},
                  {"name":"Missing Date","artist":{"#text":"Artist C"}}
                ],
                "@attr": {"page":"1","totalPages":"2"}
              }
            }
            """.trimIndent(),
        )
        assertEquals(2, recent.totalPages)
        assertEquals(1, recent.items.size)
        assertEquals("Artist A", recent.items.single().artist)
        assertEquals(1795082400L, recent.items.single().timestamp)
    }

    @Test
    fun malformedPagedHistoryResponseFailsInsteadOfLookingLikeAnEmptyPeriod() {
        assertTrue(runCatching { parseLastFmRecentTracksPage("""{"error":6,"message":"User not found"}""") }.isFailure)
    }


    @Test
    fun chartPagesCarryTheirTotalsAndDropLastFmsPlaceholderArt() {
        val artists = parseLastFmTopArtistsPage(
            """
            {"topartists":{"artist":[
              {"name":"Artist A","playcount":"40","image":[{"#text":"https://lastfm.freetls.fastly.net/i/u/300x300/2a96cbd8b46e442fc41c2b86b821562f.png"}]}
            ],"@attr":{"page":"1","totalPages":"7","total":"312"}}}
            """.trimIndent(),
        )
        assertEquals(312L, artists.total)
        assertNull(artists.items.single().artworkUrl)

        val albums = parseLastFmTopAlbumsPage(
            """
            {"topalbums":{"album":{"name":"Album","artist":{"name":"Artist A"},"playcount":"12","image":[{"#text":"https://img.test/album.jpg"}]},
             "@attr":{"total":"58"}}}
            """.trimIndent(),
        )
        assertEquals(58L, albums.total)
        assertEquals("https://img.test/album.jpg", albums.items.single().artworkUrl)
    }

    @Test
    fun recentScrobblesKeepTheNowPlayingRowFirstAndReadTheTotal() {
        val recent = parseLastFmRecentScrobbles(
            """
            {"recenttracks":{"track":[
              {"name":"Live","artist":{"#text":"Artist B"},"album":{"#text":"Record"},"@attr":{"nowplaying":"true"}},
              {"name":"Earlier","artist":{"#text":"Artist A"},"date":{"uts":"1795082400"}},
              {"name":"Undated","artist":{"#text":"Artist C"}}
            ],"@attr":{"total":"18234"}}}
            """.trimIndent(),
        )
        assertEquals(18_234L, recent.total)
        assertEquals(listOf("Live", "Earlier"), recent.items.map { it.name })
        assertTrue(recent.items.first().nowPlaying)
        assertEquals("Record", recent.items.first().album)
    }

    @Test
    fun profileReadsWhenTheAccountWasRegistered() {
        val profile = parseLastFmUserStats(
            """{"user":{"name":"zen","playcount":"1","registered":{"unixtime":"1420000000","#text":1420000000}}}""",
        )
        assertEquals(1_420_000_000L, profile?.registeredAtEpochSeconds)
    }
}
