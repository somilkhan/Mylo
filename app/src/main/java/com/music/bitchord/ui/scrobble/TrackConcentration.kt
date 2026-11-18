package com.music.bitchord.ui.scrobble

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.stats.ReplaySummary
import java.util.Locale
import kotlin.math.roundToInt

internal data class TrackConcentration(
    val topTrackPercent: Int,
    val nextTwoPercent: Int,
    val otherTracksPercent: Int,
    val topTrackMs: Long,
    val nextTwoMs: Long,
    val otherTracksMs: Long,
    val uniqueTracks: Int,
    val totalPlays: Int,
    val averagePlaysPerTrack: String,
)

internal fun calculateTrackConcentration(summary: ReplaySummary): TrackConcentration? {
    if (summary.isEmpty || summary.totalMs <= 0L) return null
    val tracks = summary.songs.sortedByDescending { it.ms.coerceAtLeast(0L) }
    if (tracks.isEmpty()) return null

    val topMs = tracks.first().ms.coerceAtLeast(0L)
    val nextMs = tracks.drop(1).take(2).sumOf { it.ms.coerceAtLeast(0L) }
    val totalMs = summary.totalMs
    val topPercent = ((topMs.toDouble() / totalMs) * 100.0).roundToInt().coerceIn(0, 100)
    val nextPercent = ((nextMs.toDouble() / totalMs) * 100.0).roundToInt().coerceIn(0, 100 - topPercent)
    val othersPercent = (100 - topPercent - nextPercent).coerceAtLeast(0)
    val otherMs = (totalMs - topMs - nextMs).coerceAtLeast(0L)
    val count = tracks.size
    val average = String.format(Locale.ROOT, "%.1f", summary.totalPlays.toFloat() / count)

    return TrackConcentration(
        topTrackPercent = topPercent,
        nextTwoPercent = nextPercent,
        otherTracksPercent = othersPercent,
        topTrackMs = topMs,
        nextTwoMs = nextMs,
        otherTracksMs = otherMs,
        uniqueTracks = count,
        totalPlays = summary.totalPlays,
        averagePlaysPerTrack = average,
    )
}

@Composable
internal fun TrackConcentrationCard(
    summary: ReplaySummary,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val data = calculateTrackConcentration(summary) ?: return
    val top by animateFloatAsState(
        targetValue = data.topTrackPercent / 100f,
        animationSpec = if (reduceMotion) snap() else tween(650),
        label = "top-track-share",
    )
    val next by animateFloatAsState(
        targetValue = data.nextTwoPercent / 100f,
        animationSpec = if (reduceMotion) snap() else tween(750),
        label = "next-two-track-share",
    )
    val others by animateFloatAsState(
        targetValue = data.otherTracksPercent / 100f,
        animationSpec = if (reduceMotion) snap() else tween(850),
        label = "other-track-share",
    )
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Track concentration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "How your listening time is distributed across your tracks.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (data.topTrackPercent > 0) Box(Modifier.weight(top.coerceAtLeast(0.001f)).height(10.dp).background(colors.primary))
                if (data.nextTwoPercent > 0) Box(Modifier.weight(next.coerceAtLeast(0.001f)).height(10.dp).background(colors.tertiary))
                if (data.otherTracksPercent > 0) Box(Modifier.weight(others.coerceAtLeast(0.001f)).height(10.dp).background(colors.outlineVariant))
            }
            ConcentrationBreakdownRow("Top track", data.topTrackPercent, data.topTrackMs, colors.primary)
            ConcentrationBreakdownRow("Next 2 tracks", data.nextTwoPercent, data.nextTwoMs, colors.tertiary)
            ConcentrationBreakdownRow("Other tracks", data.otherTracksPercent, data.otherTracksMs, colors.outlineVariant)
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column {
                    Text(data.averagePlaysPerTrack, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Avg plays / track", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                Column {
                    Text(data.uniqueTracks.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Unique tracks", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ConcentrationBreakdownRow(label: String, percent: Int, durationMs: Long, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.padding(top = 5.dp).width(8.dp).height(8.dp).clip(RoundedCornerShape(50)).background(color))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text("$percent% · ${formatConcentrationDuration(durationMs)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatConcentrationDuration(ms: Long): String {
    val minutes = ms.coerceAtLeast(0L) / 60_000L
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours > 0) "${hours}h ${remainder}m" else "${minutes}m"
}
