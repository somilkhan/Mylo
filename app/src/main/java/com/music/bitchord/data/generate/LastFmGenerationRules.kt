package com.music.bitchord.data.generate

import java.util.Locale

/**
 * Pure rules for the Last.fm-backed generator modes (Forgotten Favorites,
 * Tag Top Tracks and Top Artists Mix). Kept free of network and Android code
 * so identity, ordering and filtering stay unit-testable.
 */
internal object LastFmGenerationRules {
    /** Last.fm's fixed "12month" window used by Forgotten Favorites. */
    const val FORGOTTEN_PERIOD = "12month"
    const val FORGOTTEN_RECENT_WINDOW_DAYS = 30L
    const val FORGOTTEN_TRACK_LIMIT = 100
    const val MAX_RECENT_PAGES = 10
    const val RECENT_PAGE_SIZE = 200

    const val TOP_ARTIST_COUNT = 5
    const val ARTIST_TRACKS_PER_ARTIST = 10
    const val ARTIST_REQUEST_CONCURRENCY = 3
    const val MIX_TRACK_CAP = 30

    const val TAG_TRACK_LIMIT = 50

    /** Upper bound on Last.fm rows resolved through YouTube Music search. */
    const val MAX_RESOLVE_CANDIDATES = 60

    /**
     * Maps Mylo's replay period onto Last.fm's user.getTop* period values.
     * Last.fm has no calendar-year window, so THIS_YEAR uses the closest
     * rolling window ("12month").
     */
    /** Unix-second [from, to] bounds for the recent-listening window ending at [nowMillis]. */
    fun recentWindowSeconds(nowMillis: Long): Pair<Long, Long> {
        val to = nowMillis / 1000L
        return (to - FORGOTTEN_RECENT_WINDOW_DAYS * SECONDS_PER_DAY) to to
    }

    /** Case- and whitespace-insensitive artist and title identity. */
    fun trackKey(artist: String, title: String): String =
        normalize(artist) + "\u0000" + normalize(title)

    /** Drops every item whose key appears in [recentKeys]; the remaining order is unchanged. */
    fun <T> excludeRecent(ranked: List<T>, recentKeys: Set<String>, key: (T) -> String): List<T> =
        ranked.filterNot { key(it) in recentKeys }

    /**
     * Round-robin across [pools], skipping any item whose key was already taken,
     * and stopping once [cap] items are collected. Within each pool the original
     * order is kept.
     */
    fun <T> interleaveUnique(pools: List<List<T>>, key: (T) -> String, cap: Int): List<T> {
        if (cap <= 0) return emptyList()
        val longest = pools.maxOfOrNull { it.size } ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<T>()
        for (index in 0 until longest) {
            for (pool in pools) {
                val item = pool.getOrNull(index) ?: continue
                if (seen.add(key(item))) {
                    out += item
                    if (out.size >= cap) return out
                }
            }
        }
        return out
    }

    private fun normalize(value: String): String =
        value.trim().replace(WHITESPACE, " ").lowercase(Locale.ROOT)

    private const val SECONDS_PER_DAY = 86_400L
    private val WHITESPACE = Regex("\\s+")
}
