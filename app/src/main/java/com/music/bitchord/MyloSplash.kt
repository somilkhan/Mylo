package com.music.bitchord

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay

/** Native crimson MYLO mark reveal; canonical character art can replace its layer. */
@Composable
internal fun MyloSplash(onFinished: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val eased by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(2200, easing = FastOutSlowInEasing),
        label = "mylo-splash-progress",
    )
    LaunchedEffect(Unit) {
        delay(80)
        progress = 1f
        delay(2450)
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            val s = minOf(size.width, size.height)
            val cx = size.width * .5f
            val cy = size.height * .5f
            val radius = s * .355f
            val red = Color(0xFFB50918)
            val hotRed = Color(0xFFFF2338)
            drawCircle(red.copy(alpha = .16f + eased * .18f), radius,
                androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(red.copy(alpha = .55f * eased), radius,
                androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(s * .0018f))
            val anchorX = cx + s * .006f
            drawLine(Color(0xFFFFD8D8).copy(alpha = .9f * eased),
                androidx.compose.ui.geometry.Offset(anchorX, cy - radius),
                androidx.compose.ui.geometry.Offset(anchorX, cy - radius * .28f), s * .0028f)
            drawLine(hotRed.copy(alpha = .9f * eased),
                androidx.compose.ui.geometry.Offset(anchorX + s * .002f, cy - radius * .96f),
                androidx.compose.ui.geometry.Offset(anchorX + s * .002f, cy - radius * .29f), s * .001f)
            for (i in 0..2) {
                val y = cy + radius * (.42f + i * .105f)
                val x = cx + radius * (.12f + i * .075f)
                val wave = Path().apply {
                    moveTo(x, y + radius * .12f)
                    cubicTo(x + radius * .15f, y - radius * .08f,
                        x + radius * .46f, y - radius * .29f,
                        x + radius * .78f, y - radius * .28f + i * radius * .018f)
                }
                drawPath(wave, hotRed.copy(alpha = eased),
                    style = Stroke(width = s * .018f, cap = StrokeCap.Round))
            }
        }
    }
}
