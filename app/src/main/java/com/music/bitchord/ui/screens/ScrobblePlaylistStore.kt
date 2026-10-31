package com.music.bitchord.ui.screens

import androidx.compose.runtime.mutableStateListOf
import com.music.bitchord.data.model.Song

/** A generated mix saved for the lifetime of the current app process. */
internal data class SavedScrobblePlaylist(
    val title: String,
    val reason: String,
    val songs: List<Song>,
)

/**
 * Session-only saved mixes. Persistence is deliberately deferred until the
 * product chooses a durable playlist schema; closing/reopening Scrobble does
 * not discard these while the process remains alive.
 */
internal object ScrobblePlaylistStore {
    val playlists = mutableStateListOf<SavedScrobblePlaylist>()

    fun save(title: String, reason: String, songs: List<Song>) {
        if (title.isBlank() || songs.isEmpty()) return
        val saved = SavedScrobblePlaylist(title, reason, songs.toList())
        val existingIndex = playlists.indexOfFirst { it.title == title }
        if (existingIndex >= 0) {
            playlists[existingIndex] = saved
        } else {
            playlists.add(0, saved)
        }
    }

    fun remove(title: String) {
        playlists.removeAll { it.title == title }
    }
}
