package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobblePlaylistExportTest {
    @Test
    fun csvEscapesCommasQuotesAndNewlinesAndIncludesTrackMetadata() {
        val playlist = SavedScrobblePlaylist(
            title = "CSV test",
            reason = "Test",
            songs = listOf(
                Song(
                    videoId = "abcdefghijk",
                    title = "A, \"quoted\" track",
                    artist = "Artist\nName",
                    albumName = "Album, one",
                    durationText = "3:42",
                ),
            ),
        )

        val csv = exportScrobblePlaylist(playlist, ScrobblePlaylistExportFormat.CSV)

        assertTrue(csv.startsWith("Track,Artist,Album,Duration,Video ID\r\n"))
        assertTrue(csv.contains("\"A, \"\"quoted\"\" track\""))
        assertTrue(csv.contains("\"Artist\nName\""))
        assertTrue(csv.contains("\"Album, one\",3:42,abcdefghijk"))
    }

    @Test
    fun m3uUsesPlayableYoutubeAndLocalLocationsAndSkipsUnresolvableRows() {
        val playlist = SavedScrobblePlaylist(
            title = "Mix\nOne",
            reason = "Test",
            songs = listOf(
                Song("abcdefghijk", "Video track", "Artist", null, durationText = "3:42"),
                Song("", "Local track", "Local artist", null, localPath = "/storage/music/local.mp3"),
                Song("not-a-video-id", "Unresolvable", "Artist", null),
                Song("", "Content URI only", "Local artist", null, localUri = "content://media/external/audio/42"),
            ),
        )

        val m3u = exportScrobblePlaylist(playlist, ScrobblePlaylistExportFormat.M3U)

        assertTrue(m3u.startsWith("#EXTM3U\n#PLAYLIST:Mix One\n"))
        assertTrue(m3u.contains("#EXTINF:222,Artist - Video track\nhttps://music.youtube.com/watch?v=abcdefghijk"))
        assertTrue(m3u.contains("#EXTINF:-1,Local artist - Local track\n/storage/music/local.mp3"))
        assertFalse(m3u.contains("Unresolvable"))
        assertFalse(m3u.contains("Content URI only"))
    }

    @Test
    fun filenamesAreSanitizedAndUseTheRequestedExtension() {
        assertEquals(
            "Mix _ one.csv",
            scrobblePlaylistExportFilename("Mix / one", ScrobblePlaylistExportFormat.CSV),
        )
        assertEquals(
            "playlist.m3u",
            scrobblePlaylistExportFilename("\n", ScrobblePlaylistExportFormat.M3U),
        )
    }
}
