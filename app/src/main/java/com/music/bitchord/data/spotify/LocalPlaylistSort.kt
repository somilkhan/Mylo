package com.music.bitchord.data.spotify

import java.util.Locale

/** Playlist ordering choices used by the Scrobble playlist shelf. */
internal enum class LocalPlaylistSortMode(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    NAME("Name"),
    TRACK_COUNT("Track count"),
    CUSTOM("Custom order"),
}

/**
 * Sorts local playlists without mutating the store's saved order.
 *
 * Pinned playlists stay ahead of the rest for every automatic sort. Custom
 * order deliberately returns the persisted store order unchanged so the
 * playlist shelf can expose the existing movePlaylist operation.
 */
internal fun sortLocalPlaylists(
    playlists: List<LocalPlaylist>,
    mode: LocalPlaylistSortMode,
): List<LocalPlaylist> {
    if (mode == LocalPlaylistSortMode.CUSTOM) return playlists.toList()

    val comparator = when (mode) {
        LocalPlaylistSortMode.NEWEST ->
            compareByDescending<LocalPlaylist> { it.createdAtEpochMillis }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.id }

        LocalPlaylistSortMode.OLDEST ->
            compareBy<LocalPlaylist> { it.createdAtEpochMillis }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.id }

        LocalPlaylistSortMode.NAME ->
            compareBy<LocalPlaylist> { it.title.lowercase(Locale.ROOT) }
                .thenByDescending { it.createdAtEpochMillis }
                .thenBy { it.id }

        LocalPlaylistSortMode.TRACK_COUNT ->
            compareByDescending<LocalPlaylist> { it.songs.size }
                .thenByDescending { it.createdAtEpochMillis }
                .thenBy { it.id }

        LocalPlaylistSortMode.CUSTOM -> return playlists.toList()
    }

    val pinned = playlists.filter { it.isPinned }.sortedWith(comparator)
    val unpinned = playlists.filterNot { it.isPinned }.sortedWith(comparator)
    return pinned + unpinned
}
