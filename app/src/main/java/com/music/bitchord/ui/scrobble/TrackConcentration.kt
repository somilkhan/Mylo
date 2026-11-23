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
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
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

@Composable
internal fun TrackConcentrationCard(
    trackPlays: List<Long>,
    localListeningMs: Long,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val data = remember(trackPlays) { calculateTrackConcentration(trackPlays) } ?: return
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
        contentColor = colors.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.scrobble_track_concentration), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "How your plays are distributed across your tracks.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (data.topTrackPercent > 0) {
                    Box(Modifier.weight(top.coerceAtLeast(0.001f)).height(10.dp).background(colors.tertiary))
                }
                if (data.nextTwoPercent > 0) {
                    Box(Modifier.weight(next.coerceAtLeast(0.001f)).height(10.dp).background(colors.secondary))
                }
                if (data.otherTracksPercent > 0) {
                    Box(Modifier.weight(others.coerceAtLeast(0.001f)).height(10.dp).background(colors.onSurfaceVariant))
                }
            }
            ConcentrationBreakdownRow("Top track", data.topTrackPercent, data.topTrackPlays, colors.tertiary)
            ConcentrationBreakdownRow("Next 2 tracks", data.nextTwoPercent, data.nextTwoPlays, colors.secondary)
            ConcentrationBreakdownRow("Other tracks", data.otherTracksPercent, data.otherTracksPlays, colors.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(data.averagePlaysPerTrack, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.scrobble_avg_plays_per_track), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(data.uniqueTracks.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.scrobble_unique_tracks), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            Text(
                stringResource(R.string.scrobble_mylo_listening_time, if (localListeningMs > 0L) formatConcentrationDuration(localListeningMs) else stringResource(R.string.scrobble_not_recorded_yet)),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConcentrationBreakdownRow(
    label: String,
    percent: Int,
    plays: Long,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(R.string.scrobble_percent_summary, "$percent%", stringResource(R.string.scrobble_plays_count, NumberFormat.getIntegerInstance().format(plays))),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatConcentrationDuration(ms: Long): String {
    val minutes = ms.coerceAtLeast(0L) / 60_000L
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours > 0) "${hours}h ${remainder}m" else "${minutes}m"
}
