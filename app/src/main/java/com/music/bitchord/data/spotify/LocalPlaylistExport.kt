package com.music.bitchord.data.spotify

import com.music.bitchord.data.model.Song

/**
 * Stable, dependency-light playlist export formats for Mylo's local playlists.
 *
 * M3U entries use canonical YouTube Music watch URLs rather than transient
 * stream URLs, so an exported playlist remains useful after a stream expires.
 */
internal object LocalPlaylistExport {

    enum class Format(val extension: String, val mimeType: String) {
        CSV("csv", "text/csv"),
        M3U("m3u", "audio/x-mpegurl"),
    }

    fun filename(title: String, format: Format): String {
        val safeStem = title.map { character ->
            if (character.code < 32 || character.code == 127 || character in "/\\:*?\"<>|") '_' else character
        }.joinToString("").trim().trim('.').take(80).ifBlank { "Playlist" }
        return safeStem + "." + format.extension
    }

    fun content(title: String, songs: List<Song>, format: Format): String = when (format) {
        Format.CSV -> toCsv(songs)
        Format.M3U -> toM3u(title, songs)
    }

    fun toCsv(songs: List<Song>): String {
        val rows = songs.joinToString("\r\n") { song ->
            csvEscape(song.title) + "," + csvEscape(song.artist)
        }
        return if (rows.isEmpty()) "Track,Artist\r\n" else "Track,Artist\r\n" + rows
    }

    fun toM3u(title: String, songs: List<Song>): String {
        val playable = songs.filter { song ->
            song.videoId.isNotBlank() && song.videoId.none(Char::isWhitespace)
        }
        val skipped = songs.size - playable.size
        return buildString {
            append("#EXTM3U\n")
            append("#PLAYLIST:" + singleLine(title) + "\n")
            playable.forEach { song ->
                append("#EXTINF:-1," + singleLine(song.artist) + " - " + singleLine(song.title) + "\n")
                append("https://music.youtube.com/watch?v=" + song.videoId + "\n")
            }
            if (skipped > 0) {
                append("#Skipped " + skipped + " track" + (if (skipped == 1) "" else "s") + " without a playable video ID\n")
            }
        }
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (value.any { it == ',' || it == '\"' || it == '\r' || it == '\n' }) {
            "\"" + escaped + "\""
        } else {
            escaped
        }
    }

    private fun singleLine(value: String): String =
        value.map { character ->
            if (character == '\r' || character == '\n' || character.code < 32) ' ' else character
        }.joinToString("").trim()
}
