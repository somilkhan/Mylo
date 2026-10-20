package com.music.bitchord

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * MYLO's logo-only startup reveal. The approved artwork carries its own thread,
 * crimson emblem, inverted character and three wave marks; animate the artwork
 * as one composition so its original geometry and character details stay intact.
 */
@Composable
internal fun MyloSplash(onFinished: () -> Unit) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }
    var progress by remember { mutableFloatStateOf(if (animationsEnabled) 0f else 1f) }
    val eased by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
        label = "mylo-splash-progress",
    )

    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) {
            onFinished()
        } else {
            delay(80)
            progress = 1f
            delay(2450)
            onFinished()
        }
    }

    // Keep the startup surface modal: taps must never reach the app underneath.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        val logoSize = minOf(maxWidth, maxHeight) * 0.88f
        val entranceDistancePx = with(LocalDensity.current) { ((maxHeight + logoSize) / 2f).toPx() }
        Image(
            painter = painterResource(id = R.drawable.mylo_logo),
            contentDescription = "Mylo logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(logoSize)
                .graphicsLayer {
                    val phase = eased.coerceIn(0f, 1f)
                    // The hanging thread leads the descent; a restrained swing
                    // decays to zero so the artwork settles without a hard stop.
                    translationY = (1f - phase) * -entranceDistancePx
                    rotationZ = -sin(phase * Math.PI).toFloat() * (1f - phase) * 9f
                    alpha = phase
                },
        )
    }
}
