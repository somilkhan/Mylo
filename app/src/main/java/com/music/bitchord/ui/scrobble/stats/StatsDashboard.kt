package com.music.bitchord.ui.scrobble.stats

import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.scrobbling.LastFmActivityBucket
import com.music.bitchord.data.scrobbling.LastFmStatsPeriod
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.ShimmerBox
import com.music.bitchord.ui.icons.SfIcons
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(24.dp)
private val ChartHeight = 116.dp

/**
 * The top of the Stats tab: the period's scrobbles as the one big figure,
 * how that compares with the window before it, an activity chart that can be
 * tapped or scrubbed bar by bar, and the period's per-day rate and distinct
 * artists, albums and tracks along the bottom.
 */
@Composable
internal fun StatsDashboard(
    period: LastFmStatsPeriod,
    scrobbles: Long,
    window: String,
    previousScrobbles: Long?,
    activity: List<LastFmActivityBucket>,
    perDay: Double?,
    artists: Long,
    albums: Long,
    tracks: Long,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val format = rememberCountFormat()
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .padding(horizontal = PAGE_GUTTER)
            .padding(top = 20.dp)
            .fillMaxWidth()
            .background(colors.surfaceVariant.copy(alpha = 0.55f), CardShape)
            .border(0.5.dp, colors.onSurface.copy(alpha = 0.07f), CardShape)
            .padding(top = 18.dp, bottom = 16.dp),
    ) {
        val label = stringResource(R.string.scrobble_stats_scrobbles)
        val number = format.format(scrobbles)
        Row(
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (previousScrobbles != null && previousScrobbles > 0L) {
                ChangeBadge(period, scrobbles, previousScrobbles)
            }
        }
        Text(
            text = number,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 52.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.2).sp,
                fontFeatureSettings = "tnum",
            ),
            color = colors.onSurface,
            maxLines = 1,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(top = 2.dp)
                .semantics { contentDescription = "$number $label, $window" },
        )
        Text(
            text = window,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        if (activity.size >= 2) {
            Spacer(Modifier.height(20.dp))
            ActivityChart(period, activity, reduceMotion)
        }

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.onSurface.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
            if (perDay != null) {
                Metric(rememberRateFormat().format(perDay), stringResource(R.string.scrobble_stats_per_day))
            }
            Metric(format.format(artists), stringResource(R.string.scrobble_stats_metric_artists))
            Metric(format.format(albums), stringResource(R.string.scrobble_stats_metric_albums))
            Metric(format.format(tracks), stringResource(R.string.scrobble_stats_metric_tracks))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Metric(value: String, label: String) {
    Column(
        Modifier
            .weight(1f)
            .semantics(mergeDescendants = true) { contentDescription = "$value $label" },
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** "↑ 12%" against the window before this one, quiet and monochrome. */
@Composable
private fun ChangeBadge(period: LastFmStatsPeriod, current: Long, previous: Long) {
    val locale = LocalConfiguration.current.locales[0]
    val percent = remember(locale) {
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 }
    }
    val change = (current - previous).toDouble() / previous
    val rounded = (change * 100).roundToInt()
    // The arrow carries the direction, so the figure goes without a sign.
    val text = percent.format(if (rounded == 0) 0.0 else abs(change))
    val description = when {
        rounded == 0 -> stringResource(R.string.scrobble_stats_change_flat)
        rounded > 0 -> stringResource(R.string.scrobble_stats_change_up, percent.format(abs(change)))
        else -> stringResource(R.string.scrobble_stats_change_down, percent.format(abs(change)))
    }
    val comparison = period.comparison?.let { stringResource(it) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        if (comparison != null) {
            Text(
                text = comparison,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
        }
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f), RoundedCornerShape(50))
                .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (rounded != 0) {
                Icon(
                    imageVector = SfIcons.ArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(12.dp)
                        .rotate(if (rounded > 0) 0f else 180f),
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Bars for each slice of the period, the busiest drawn solid. Touching the
 * chart picks the bar under the finger, and dragging scrubs across them; the
 * caption above names the picked slice, or the busiest when nothing is.
 */
@Composable
private fun ActivityChart(
    period: LastFmStatsPeriod,
    buckets: List<LastFmActivityBucket>,
    reduceMotion: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val format = rememberCountFormat()
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }

    var selected by rememberSaveable(period, buckets.size) { mutableStateOf(-1) }
    val peak = remember(buckets) { buckets.indices.maxByOrNull { buckets[it].scrobbles } ?: 0 }
    val highest = buckets[peak].scrobbles.coerceAtLeast(1L)
    val shown = if (selected in buckets.indices) selected else peak

    val grow = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(buckets) {
        if (reduceMotion) {
            grow.snapTo(1f)
        } else {
            grow.snapTo(0f)
            grow.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
        }
    }

    fun rangeLabel(bucket: LastFmActivityBucket): String {
        val start = bucket.startEpochSeconds * 1_000L
        val end = bucket.endEpochSeconds * 1_000L
        return when {
            period == LastFmStatsPeriod.OVERALL ->
                Instant.ofEpochSecond(bucket.startEpochSeconds).atZone(zone).year.toString()
            period == LastFmStatsPeriod.WEEK -> DateUtils.formatDateTime(
                context,
                end,
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_NO_YEAR,
            )
            else -> DateUtils.formatDateRange(
                context,
                start,
                end,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR,
            )
        }
    }

    val caption = rangeLabel(buckets[shown])
    val captionCount = pluralStringResource(
        R.plurals.scrobble_stats_scrobble_count,
        buckets[shown].scrobbles.toQuantity(),
        format.format(buckets[shown].scrobbles),
    )
    val busiest = stringResource(R.string.scrobble_stats_busiest)
    val chartDescription = stringResource(
        R.string.scrobble_stats_activity_description,
        rangeLabel(buckets[peak]),
        pluralStringResource(
            R.plurals.scrobble_stats_scrobble_count,
            buckets[peak].scrobbles.toQuantity(),
            format.format(buckets[peak].scrobbles),
        ),
    )

    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.scrobble_stats_activity),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = buildString {
                    if (selected !in buckets.indices) append(busiest).append("  ")
                    append(caption).append(" · ").append(captionCount)
                },
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))

        val solid = colors.onSurface
        val dim = colors.onSurface.copy(alpha = 0.16f)
        val stub = colors.onSurface.copy(alpha = 0.08f)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .semantics { contentDescription = chartDescription }
                .pointerInput(buckets) {
                    fun indexAt(x: Float): Int =
                        (x / size.width * buckets.size).toInt().coerceIn(0, buckets.lastIndex)
                    detectTapGestures { offset ->
                        val index = indexAt(offset.x)
                        selected = if (selected == index) -1 else index
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
                .pointerInput(buckets) {
                    fun indexAt(x: Float): Int =
                        (x / size.width * buckets.size).toInt().coerceIn(0, buckets.lastIndex)
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> selected = indexAt(offset.x) },
                    ) { change, _ ->
                        val index = indexAt(change.position.x)
                        if (index != selected) {
                            selected = index
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                },
        ) {
            val count = buckets.size
            val gap = if (count <= 7) 10.dp.toPx() else 6.dp.toPx()
            val barWidth = (size.width - gap * (count - 1)) / count
            val radius = CornerRadius(minOf(barWidth / 2f, 6.dp.toPx()))
            val minHeight = 3.dp.toPx()
            // Faint guide at half the busiest slice, so heights read as amounts.
            val guideY = size.height * 0.5f
            drawLine(
                color = stub,
                start = Offset(0f, guideY),
                end = Offset(size.width, guideY),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
            )
            buckets.forEachIndexed { index, bucket ->
                val fraction = bucket.scrobbles.toFloat() / highest
                val height = if (bucket.scrobbles == 0L) {
                    minHeight
                } else {
                    maxOf(minHeight, size.height * fraction * grow.value)
                }
                val x = index * (barWidth + gap)
                drawRoundRect(
                    color = when {
                        bucket.scrobbles == 0L -> stub
                        index == shown -> solid
                        else -> dim
                    },
                    topLeft = Offset(x, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = radius,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        AxisLabels(period, buckets, shown, zone, locale)
    }
}

/** Weekday initials under a week's bars; first and last dates otherwise. */
@Composable
private fun AxisLabels(
    period: LastFmStatsPeriod,
    buckets: List<LastFmActivityBucket>,
    shown: Int,
    zone: ZoneId,
    locale: java.util.Locale,
) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    if (period == LastFmStatsPeriod.WEEK) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            buckets.forEachIndexed { index, bucket ->
                val day = Instant.ofEpochSecond(bucket.endEpochSeconds).atZone(zone).dayOfWeek
                Text(
                    text = day.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                    style = style,
                    color = if (index == shown) colors.onSurface else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        return
    }
    val context = LocalContext.current
    val first = buckets.first()
    val start = if (period == LastFmStatsPeriod.OVERALL) {
        Instant.ofEpochSecond(first.startEpochSeconds).atZone(zone).year.toString()
    } else {
        DateUtils.formatDateTime(
            context,
            first.startEpochSeconds * 1_000L,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR,
        )
    }
    val end = if (period == LastFmStatsPeriod.OVERALL) {
        Instant.ofEpochSecond(buckets.last().startEpochSeconds).atZone(zone).year.toString()
    } else {
        stringResource(R.string.scrobble_stats_today)
    }
    Row(Modifier.fillMaxWidth()) {
        Text(start, style = style, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(end, style = style, color = colors.onSurfaceVariant)
    }
}

/** Scrobbles a day: whole numbers once they are big enough not to need a decimal. */
@Composable
private fun rememberRateFormat(): NumberFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        object : NumberFormat() {
            private val whole = getIntegerInstance(locale)
            private val tenths = getNumberInstance(locale).apply {
                minimumFractionDigits = 1
                maximumFractionDigits = 1
            }

            override fun format(number: Double, toAppendTo: StringBuffer, pos: java.text.FieldPosition) =
                (if (number >= 10.0 || number == 0.0) whole else tenths).format(number, toAppendTo, pos)

            override fun format(number: Long, toAppendTo: StringBuffer, pos: java.text.FieldPosition) =
                whole.format(number, toAppendTo, pos)

            override fun parse(source: String, parsePosition: java.text.ParsePosition) =
                whole.parse(source, parsePosition)
        }
    }
}

/** How the change badge names the window it compares against. */
private val LastFmStatsPeriod.comparison: Int?
    get() = when (this) {
        LastFmStatsPeriod.WEEK -> R.string.scrobble_stats_vs_previous_week
        LastFmStatsPeriod.MONTH -> R.string.scrobble_stats_vs_previous_month
        LastFmStatsPeriod.QUARTER -> R.string.scrobble_stats_vs_previous_quarter
        LastFmStatsPeriod.HALF_YEAR -> R.string.scrobble_stats_vs_previous_half_year
        LastFmStatsPeriod.YEAR -> R.string.scrobble_stats_vs_previous_year
        LastFmStatsPeriod.OVERALL -> null
    }

/** A shimmer in the dashboard's shape, so loading doesn't reflow the page. */
@Composable
internal fun StatsDashboardSkeleton(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .padding(horizontal = PAGE_GUTTER)
            .padding(top = 20.dp)
            .fillMaxWidth()
            .background(colors.surfaceVariant.copy(alpha = 0.55f), CardShape)
            .border(0.5.dp, colors.onSurface.copy(alpha = 0.07f), CardShape)
            .padding(20.dp),
    ) {
        ShimmerBox(Modifier.width(76.dp).height(12.dp), RoundedCornerShape(50))
        Spacer(Modifier.height(12.dp))
        ShimmerBox(Modifier.width(164.dp).height(44.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.height(10.dp))
        ShimmerBox(Modifier.width(112.dp).height(12.dp), RoundedCornerShape(50))
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth().height(ChartHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            SkeletonBars.forEach { fraction ->
                ShimmerBox(
                    Modifier.weight(1f).height(ChartHeight * fraction),
                    RoundedCornerShape(6.dp),
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth()) {
            repeat(4) {
                Column(Modifier.weight(1f)) {
                    ShimmerBox(Modifier.width(40.dp).height(14.dp), RoundedCornerShape(50))
                    Spacer(Modifier.height(8.dp))
                    ShimmerBox(Modifier.width(48.dp).height(10.dp), RoundedCornerShape(50))
                }
            }
        }
    }
}

private val SkeletonBars = listOf(0.35f, 0.55f, 0.42f, 0.8f, 0.6f, 0.3f, 0.7f, 0.5f, 0.9f, 0.45f)
