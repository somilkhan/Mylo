package com.music.bitchord.data.spotify

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaylistExportTest {

    @Test
    fun csvEscapesCommasQuotesAndLineBreaks() {
        val song = Song(videoId = "video-1", title = "A, \"quoted\"\nTitle", artist = "Artist, One", thumbnailUrl = null, fromAutoplay = false)
        assertEquals(
            "Track,Artist\r\n\"A, \"\"quoted\"\"\nTitle\",\"Artist, One\"",
            LocalPlaylistExport.toCsv(listOf(song)),
        )
    }

    @Test
    fun m3uUsesStableYouTubeMusicLinksAndSafeSingleLineMetadata() {
        val songs = listOf(
            Song(videoId = "video_1-abc", title = "Track\nOne", artist = "Artist", thumbnailUrl = null, fromAutoplay = false, thumbnailUrl = null, fromAutoplay = false),
            Song(videoId = "video-2", title = "Second", artist = "Guest", thumbnailUrl = null, fromAutoplay = false),
        )
        assertEquals(
            "#EXTM3U\n#PLAYLIST:My Mix\n" +
                "#EXTINF:-1,Artist - Track One\nhttps://music.youtube.com/watch?v=video_1-abc\n" +
                "#EXTINF:-1,Guest - Second\nhttps://music.youtube.com/watch?v=video-2\n",
            LocalPlaylistExport.toM3u("My Mix", songs),
        )
    }

    @Test
    fun m3uReportsTracksWithoutPlayableVideoIdsInsteadOfWritingBrokenEntries() {
        val songs = listOf(
            Song(videoId = "", title = "Local only", artist = "Artist", thumbnailUrl = null, fromAutoplay = false, thumbnailUrl = null, fromAutoplay = false),
            Song(videoId = "video-2", title = "Playable", artist = "Artist", thumbnailUrl = null, fromAutoplay = false, thumbnailUrl = null, fromAutoplay = false),
        )
        val output = LocalPlaylistExport.toM3u("Mix", songs)
        assertFalse(output.contains("Local only"))
        assertTrue(output.contains("#Skipped 1 track without a playable video ID"))
        assertTrue(output.contains("https://music.youtube.com/watch?v=video-2"))
    }

    @Test
    fun filenameCannotEscapeTheExportDirectory() {
        val filename = LocalPlaylistExport.filename("../My:Mix?", LocalPlaylistExport.Format.CSV)
        assertFalse(filename.contains('/'))
        assertFalse(filename.contains('\\'))
        assertFalse(filename.contains(".."))
        assertTrue(filename.endsWith(".csv"))
    }

    @Test
    fun emptyCsvStillContainsItsHeader() {
        assertEquals("Track,Artist\r\n", LocalPlaylistExport.toCsv(emptyList()))
    }
}
