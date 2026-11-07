package com.music.bitchord.data.generator

/**
 * Returns raw history rows not represented by the dated parser, preserving duplicate listens.
 * Match by video ID as Innertube history rows do not expose a stable per-play ID.
 */
internal fun unmatchedHistoryVideoIds(datedVideoIds: List<String>, rawVideoIds: List<String>): List<String> {
    val represented = datedVideoIds.filter(String::isNotBlank).groupingBy { it }.eachCount().toMutableMap()
    return buildList {
        rawVideoIds.filter(String::isNotBlank).forEach { id ->
            val count = represented[id] ?: 0
            if (count > 0) represented[id] = count - 1 else add(id)
        }
    }
}
