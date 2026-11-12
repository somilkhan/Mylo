package com.music.bitchord.data.sync

/**
 * Tracks continuation tokens for one history traversal.
 *
 * YouTube Music occasionally returns a token already seen earlier in the
 * traversal. Comparing only with the immediately previous token misses
 * multi-page cycles (A → B → A), while accepting blank tokens can issue
 * malformed requests.
 */
internal class HistoryContinuationTracker {
    private val seen = HashSet<String>()

    /** Starts a fresh traversal and registers its first continuation, if any. */
    fun reset(token: String?): String? {
        seen.clear()
        return remember(token)
    }

    /**
     * Returns [next] only when it is a new, non-blank token distinct from
     * [current]. A null result means the chain is exhausted or has looped.
     */
    fun advance(current: String, next: String?): String? {
        if (current.isBlank()) return null
        return next?.takeIf { it.isNotBlank() && it != current && seen.add(it) }
    }

    private fun remember(token: String?): String? =
        token?.takeIf { it.isNotBlank() && seen.add(it) }
}
