package com.music.bitchord.ui.scrobble.stats

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** One pulse shared by every placeholder on the tab. Under reduce motion it holds still. */
@Composable
internal fun rememberSkeletonAlpha(reduceMotion: Boolean): Float {
    val transition = rememberInfiniteTransition(label = "stats-skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stats-skeleton-alpha",
    )
    return if (reduceMotion) 0.6f else pulse
}

@Composable
internal fun SkeletonBlock(
    alpha: Float,
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(50),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)),
    )
}

/** Placeholder shaped like the featured artist card and the rows under it. */
@Composable
internal fun StatsRankingsSkeleton(reduceMotion: Boolean) {
    val alpha = rememberSkeletonAlpha(reduceMotion)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SkeletonBlock(alpha, Modifier.size(96.dp), CircleShape)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SkeletonBlock(alpha, Modifier.fillMaxWidth(0.4f).height(12.dp))
                    SkeletonBlock(alpha, Modifier.fillMaxWidth(0.8f).height(20.dp))
                    SkeletonBlock(alpha, Modifier.fillMaxWidth(0.5f).height(12.dp))
                }
            }
        }
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBlock(alpha, Modifier.width(24.dp).height(10.dp))
                SkeletonBlock(alpha, Modifier.size(44.dp), CircleShape)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SkeletonBlock(alpha, Modifier.fillMaxWidth(0.55f).height(12.dp))
                    SkeletonBlock(alpha, Modifier.fillMaxWidth(0.9f).height(4.dp))
                }
            }
        }
    }
}
