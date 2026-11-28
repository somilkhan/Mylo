package com.music.bitchord.data.generate

import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.YtMusicRepository.SongPage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Unlike screen/library best-effort paging, exclusions must be complete or fail closed. */
internal suspend fun completeGenerationSongFeed(
    first: SongPage,
    maxPages: Int = 50,
    loadNext: suspend (String) -> SongPage,
): List<Song> {
    val songs = ArrayList<Song>()
    val seenTokens = HashSet<String>()
    var page = first
    var pages = 1
    while (true) {
        currentCoroutineContext().ensureActive()
        songs += page.songs
        val token = page.continuation ?: break
        requireGeneration(
            pages < maxPages && seenTokens.add(token),
            R.string.scrobble_gen_error_exclusions_unavailable,
        )
        page = loadNext(token)
        pages++
    }
    return songs.distinctBy { it.videoId }
}
