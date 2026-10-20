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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * Native animated MYLO splash. The figure is an interim vector layer; the
 * supplied canonical artwork should replace it when added as a transparent asset.
 */
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
            val r = s * .355f
            val crimson = Color(0xFF9C0712)
            val red = Color(0xFFFF2338)
            val ember = Color(0xFFFF6B32)
            val center = Offset(cx, cy)

            drawCircle(crimson.copy(alpha = .14f + eased * .19f), r, center)
            drawCircle(crimson.copy(alpha = .65f * eased), r, center, style = Stroke(s * .0018f))

            // The character descends from above, swings past center, and settles.
            val drop = (1f - eased) * -s * .47f
            val swing = sin(eased * Math.PI).toFloat() * (1f - eased) * 19f
            translate(top = drop) {
                rotate(swing, pivot = Offset(cx, cy - s * .08f)) {
                    val cloak = Path().apply {
                        moveTo(cx - s*.035f, cy-r*.50f)
                        cubicTo(cx-s*.12f, cy-r*.59f, cx-s*.25f, cy-r*.48f, cx-s*.29f, cy-r*.31f)
                        lineTo(cx-s*.18f, cy-r*.17f)
                        lineTo(cx-s*.10f, cy+r*.01f)
                        lineTo(cx-s*.09f, cy+r*.28f)
                        lineTo(cx-s*.03f, cy+r*.36f)
                        lineTo(cx+s*.015f, cy+r*.17f)
                        lineTo(cx+s*.12f, cy+r*.03f)
                        cubicTo(cx+s*.23f, cy-r*.02f, cx+s*.19f, cy-r*.20f, cx+s*.12f, cy-r*.30f)
                        lineTo(cx+s*.04f, cy-r*.49f)
                        close()
                    }
                    drawPath(cloak, Color(0xFF09090B).copy(alpha = eased))
                    drawPath(cloak, Color(0xFF6E1019).copy(alpha = eased*.9f),
                        style = Stroke(width = s*.0025f, cap = StrokeCap.Round))
                    // Coat folds and crimson rim lighting.
                    for (i in 0..3) {
                        val x = cx - s*.13f + i*s*.075f
                        val fold = Path().apply {
                            moveTo(x, cy-r*.27f)
                            cubicTo(x+s*.01f, cy-r*.08f, x-s*.012f, cy+r*.09f, x+s*.006f, cy+r*.25f)
                        }
                        drawPath(fold, if(i%2==0) Color(0xFF3A2528) else red.copy(alpha=.8f),
                            style=Stroke(s*.002f, cap=StrokeCap.Round))
                    }
                    // Inverted head and sharp, ember-colored hair echo the reference silhouette.
                    val head = Path().apply {
                        moveTo(cx-s*.055f, cy+r*.25f)
                        cubicTo(cx-s*.065f, cy+r*.32f, cx-s*.035f, cy+r*.39f, cx, cy+r*.40f)
                        cubicTo(cx+s*.045f, cy+r*.38f, cx+s*.06f, cy+r*.31f, cx+s*.045f, cy+r*.25f)
                        close()
                    }
                    drawPath(head, Color(0xFF171214).copy(alpha=eased))
                    val hair = Path().apply {
                        moveTo(cx-s*.055f, cy+r*.29f)
                        lineTo(cx-s*.10f, cy+r*.25f)
                        lineTo(cx-s*.075f, cy+r*.34f)
                        lineTo(cx-s*.12f, cy+r*.32f)
                        lineTo(cx-s*.065f, cy+r*.38f)
                        lineTo(cx-s*.03f, cy+r*.43f)
                        lineTo(cx+s*.01f, cy+r*.39f)
                        lineTo(cx+s*.045f, cy+r*.43f)
                        lineTo(cx+s*.06f, cy+r*.36f)
                        lineTo(cx+s*.10f, cy+r*.32f)
                        lineTo(cx+s*.055f, cy+r*.31f)
                        lineTo(cx+s*.07f, cy+r*.25f)
                        lineTo(cx+s*.015f, cy+r*.29f)
                        close()
                    }
                    drawPath(hair, ember.copy(alpha=eased))
                    drawPath(hair, red.copy(alpha=eased), style=Stroke(s*.002f))
                    drawLine(Color(0xFFFFD6C4).copy(alpha=eased),
                        Offset(cx-s*.025f, cy+r*.335f), Offset(cx-s*.005f, cy+r*.335f), s*.002f)
                    drawLine(Color(0xFFFFD6C4).copy(alpha=eased),
                        Offset(cx+s*.018f, cy+r*.335f), Offset(cx+s*.032f, cy+r*.335f), s*.002f)
                }
            }

            // Thread is drawn last so it visually connects to the descending figure.
            drawLine(Color(0xFFFFD8D8).copy(alpha = eased),
                Offset(cx+s*.006f, cy-r), Offset(cx+s*.006f, cy-r*.24f), s*.0024f)
            drawLine(red.copy(alpha = eased),
                Offset(cx+s*.009f, cy-r*.98f), Offset(cx+s*.009f, cy-r*.25f), s*.001f)

            // Three lower-right wave marks resolve into the final logo.
            for (i in 0..2) {
                val y = cy + r * (.42f + i * .105f)
                val x = cx + r * (.12f + i * .075f)
                val wave = Path().apply {
                    moveTo(x, y + r * .12f)
                    cubicTo(x + r * .15f, y - r * .08f,
                        x + r * .46f, y - r * .29f,
                        x + r * .78f, y - r * .28f + i * r * .018f)
                }
                drawPath(wave, red.copy(alpha = eased),
                    style = Stroke(width = s * .018f, cap = StrokeCap.Round))
            }
        }
    }
}
