package com.music.bitchord.ui.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A truthful 24-hour listening timeline backed by the recorder's actual audio
 * samples. It deliberately does not infer play counts or listening sessions.
 */
internal data class ListeningRhythmData(
    val hours: List<Long>,
    val totalMs: Long,
    val peakHour: Int?,
    val peakDurationMs: Long,
)

internal fun calculateListeningRhythm(hourOfDay: List<Long>): ListeningRhythmData {
    val normalized = List(24) { index -> hourOfDay.getOrNull(index)?.coerceAtLeast(0L) ?: 0L }
    val total = normalized.sum()
    val peak = normalized.indices
        .filter { normalized[it] > 0L }
        .maxByOrNull { normalized[it] }
    return ListeningRhythmData(
        hours = normalized,
        totalMs = total,
        peakHour = peak,
        peakDurationMs = peak?.let(normalized::get) ?: 0L,
    )
}

@Composable
internal fun ListeningRhythmCard(
    hourOfDay: List<Long>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val data = remember(hourOfDay) { calculateListeningRhythm(hourOfDay) }
    val colors = MaterialTheme.colorScheme
    val chartDescription = data.hours.mapIndexedNotNull { hour, ms ->
        if (ms > 0L) "${formatHour(context, hour)}: ${formatListening(context, ms)}" else null
    }.joinToString(", ").ifBlank { "No listening time recorded" }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Listening rhythm",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Text(
                text = "When your music played across the day.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            if (data.peakHour == null) {
                Text(
                    text = "No listening time recorded for this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Peak hour · ${formatHour(context, data.peakHour)} · ${formatListening(context, data.peakDurationMs)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .semantics { contentDescription = chartDescription },
                ) {
                    drawListeningBars(
                        values = data.hours,
                        peakHour = data.peakHour,
                        normalColor = colors.primary.copy(alpha = 0.48f),
                        peakColor = colors.tertiary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf("12a", "6a", "12p", "6p", "12a").forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawListeningBars(
    values: List<Long>,
    peakHour: Int,
    normalColor: Color,
    peakColor: Color,
) {
    val maxValue = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val slotWidth = size.width / 24f
    val barWidth = slotWidth * 0.56f
    val minimumHeight = 2.dp.toPx()
    val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

    values.forEachIndexed { hour, value ->
        if (value <= 0L) return@forEachIndexed
        val fraction = value.toDouble() / maxValue.toDouble()
        val barHeight = (size.height * fraction).toFloat().coerceAtLeast(minimumHeight)
        drawRoundRect(
            color = if (hour == peakHour) peakColor else normalColor,
            topLeft = Offset(
                x = hour * slotWidth + (slotWidth - barWidth) / 2f,
                y = size.height - barHeight,
            ),
            size = Size(barWidth, barHeight),
            cornerRadius = radius,
        )
    }
}
