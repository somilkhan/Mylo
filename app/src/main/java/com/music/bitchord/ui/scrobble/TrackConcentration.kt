package com.music.bitchord.ui.scrobble

import java.util.Locale
import kotlin.math.roundToInt

internal data class TrackConcentration(
    val topTrackPercent: Int,
    val nextTwoPercent: Int,
    val otherTracksPercent: Int,
    val topTrackPlays: Long,
    val nextTwoPlays: Long,
    val otherTracksPlays: Long,
    val uniqueTracks: Int,
    val totalPlays: Long,
    val averagePlaysPerTrack: String,
)

internal fun calculateTrackConcentration(trackPlays: List<Long>): TrackConcentration? {
    val tracks = trackPlays.map { it.coerceAtLeast(0L) }.filter { it > 0L }.sortedDescending()
    if (tracks.isEmpty()) return null

    val total = tracks.sum()
    if (total <= 0L) return null

    val topPlays = tracks.first()
    val nextPlays = tracks.drop(1).take(2).sum()
    val otherPlays = (total - topPlays - nextPlays).coerceAtLeast(0L)
    val topPercent = ((topPlays.toDouble() / total) * 100.0).roundToInt().coerceIn(0, 100)
    val nextPercent = ((nextPlays.toDouble() / total) * 100.0).roundToInt().coerceIn(0, 100 - topPercent)
    val otherPercent = (100 - topPercent - nextPercent).coerceAtLeast(0)
    val count = tracks.size

    return TrackConcentration(
        topTrackPercent = topPercent,
        nextTwoPercent = nextPercent,
        otherTracksPercent = otherPercent,
        topTrackPlays = topPlays,
        nextTwoPlays = nextPlays,
        otherTracksPlays = otherPlays,
        uniqueTracks = count,
        totalPlays = total,
        averagePlaysPerTrack = String.format(Locale.ROOT, "%.1f", total.toDouble() / count),
    )
}
