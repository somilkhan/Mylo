package com.music.bitchord.data.generator

/** Raw history is full-feed scoped; only compare it against a full dated import. */
internal fun shouldFetchUndatedHistoryFallback(lastSyncAtMillis: Long?): Boolean =
    lastSyncAtMillis == null

/**
 * Returns raw history rows not represented by the dated parser, preserving duplicate listens.
 * Match by video ID occurrence count because Innertube history rows lack stable per-play IDs.
 */
internal fun unmatchedHistoryVideoIds(datedVideoIds: List<String>, rawVideoIds: List<String>): List<String> {
    val represented = datedVideoIds.map(String::trim).filter(String::isNotEmpty)
        .groupingBy { it }.eachCount().toMutableMap()
    return buildList {
        rawVideoIds.map(String::trim).filter(String::isNotEmpty).forEach { id ->
            val count = represented[id] ?: 0
            if (count > 0) represented[id] = count - 1 else add(id)
        }
    }
}
