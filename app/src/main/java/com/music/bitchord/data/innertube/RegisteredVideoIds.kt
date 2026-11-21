package com.music.bitchord.data.innertube

/**
 * Video ids whose play YouTube accepted (the start-of-play ping returned 2xx).
 *
 * Bounded so a long session cannot grow it without limit. Only the most recent
 * [capacity] ids are kept, which is enough: a play is judged while it is still
 * the current track, moments after its ping.
 */
class RegisteredVideoIds(private val capacity: Int = 64) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val ids = LinkedHashSet<String>()

    @Synchronized
    fun add(videoId: String) {
        ids.remove(videoId)
        ids.add(videoId)
        while (ids.size > capacity) {
            ids.remove(ids.first())
        }
    }

    @Synchronized
    fun contains(videoId: String): Boolean = videoId in ids
}
