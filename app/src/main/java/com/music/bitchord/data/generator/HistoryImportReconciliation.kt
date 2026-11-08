package com.music.bitchord.data.generator

/**
 * Returns raw history rows not represented by the dated parser, preserving duplicate listens.
 * Match by video ID as Innertube history rows do not expose a stable per-play ID.
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
