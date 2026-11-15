package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.Song

internal enum class ScrobblePlaylistExportFormat(
    val extension: String,
    val mimeType: String,
) {
    CSV("csv", "text/csv"),
    M3U("m3u", "audio/x-mpegurl"),
}

/** Pure export builders; never serialize transient queue/session state or credentials. */
internal fun exportScrobblePlaylist(
    playlist: SavedScrobblePlaylist,
    format: ScrobblePlaylistExportFormat,
): String = when (format) {
    ScrobblePlaylistExportFormat.CSV -> buildString {
        append("Track,Artist,Album,Duration,Video ID\r\n")
        playlist.songs.forEach { song ->
            append(
                listOf(
                    song.title,
                    song.artist,
                    song.albumName.orEmpty(),
                    song.durationText.orEmpty(),
                    song.videoId,
                ).joinToString(",") { csvEscape(it) },
            )
            append("\r\n")
        }
    }
    ScrobblePlaylistExportFormat.M3U -> buildString {
        append("#EXTM3U\n")
        append("#PLAYLIST:")
        append(m3uSafe(playlist.title))
        append('\n')
        playlist.songs.forEach { song ->
            val location = song.localPath?.takeIf(String::isNotBlank)
                ?: song.localUri?.takeIf(String::isNotBlank)
                ?: song.videoId.takeIf(::isYoutubeVideoId)
                    ?.let { "https://music.youtube.com/watch?v=$it" }
                ?: return@forEach
            val durationSeconds = parseDurationSeconds(song.durationText)
            append("#EXTINF:")
            append(durationSeconds ?: -1)
            append(',')
            append(m3uSafe(song.artist))
            append(" - ")
            append(m3uSafe(song.title))
            append('\n')
            append(location)
            append('\n')
        }
    }
}

internal fun scrobblePlaylistExportFilename(
    title: String,
    format: ScrobblePlaylistExportFormat,
): String {
    val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("[\\r\\n]+"), " ")
        .trim()
        .ifBlank { "playlist" }
    return "$safe.${format.extension}"
}

private fun csvEscape(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return if (value.any { it == ',' || it == '"' || it == '\\r' || it == '\\n' }) {
        "\"$escaped\""
    } else {
        escaped
    }
}

private fun m3uSafe(value: String): String =
    value.replace('\\r', ' ').replace('\\n', ' ').trim()

private fun parseDurationSeconds(value: String?): Int? {
    val parts = value?.trim()?.split(':') ?: return null
    if (parts.isEmpty() || parts.size > 3) return null
    val numbers = parts.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
    return numbers.fold(0L) { total, part -> total * 60L + part }
        .takeIf { it <= Int.MAX_VALUE }
        ?.toInt()
}

private val YOUTUBE_EXPORT_ID = Regex("^[A-Za-z0-9_-]{11}$")

private fun isYoutubeVideoId(value: String): Boolean = YOUTUBE_EXPORT_ID.matches(value)
