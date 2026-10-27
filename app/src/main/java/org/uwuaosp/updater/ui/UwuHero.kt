/*
 * SPDX-FileCopyrightText: 2026 UwUniverse
 * SPDX-License-Identifier: Apache-2.0
 */
package org.uwuaosp.updater.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.uwuaosp.updater.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun UwuHero(showUpdateAnimation: Boolean) {
    var running by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        running = ValueAnimator.areAnimatorsEnabled()
        onPauseOrDispose { running = false }
    }
    Box(Modifier.fillMaxWidth().height(164.dp), contentAlignment = Alignment.Center) {
        if (showUpdateAnimation) {
            // Keep the cookie behind the other shapes, matching the final demo.
            HeroShape(R.drawable.uwu_cookie, Color(0xFF8F8EE8), 2, 30f, 20f, .34f, -7f, running)
            HeroShape(R.drawable.uwu_star, Color(0xFF78DCFD), 0, 0f, -25f, .38f, -12f, running)
            HeroShape(R.drawable.uwu_cross, Color(0xFFD0D0FD), 1, -30f, 20f, .29f, 9f, running)
        } else {
            Image(painterResource(R.drawable.uwu_branding), null, Modifier.size(328.dp, 164.dp))
        }
    }
}

@Composable
private fun HeroShape(
    drawable: Int, color: Color, index: Int, x: Float, y: Float,
    scale: Float, tilt: Float, running: Boolean,
) {
    if (running) {
        val transition = rememberInfiniteTransition(label = "uwu hero")
        val rotation = transition.animateFloat(0f, 360f,
            infiniteRepeatable(tween(10_000 + index * 1_000, easing = LinearEasing), RepeatMode.Restart),
            label = "rotation")
        val drift = transition.animateFloat(0f, (Math.PI * 2).toFloat(),
            infiniteRepeatable(tween(6_200, easing = LinearEasing), RepeatMode.Restart), label = "drift")
        Icon(painterResource(drawable), null,
            Modifier.size((200 * scale).dp).graphicsLayer {
                val phase = drift.value + index * (Math.PI * 2 / 3).toFloat()
                translationX = (x + 3 * sin(phase)).dp.toPx()
                translationY = (y + 2.2f * cos(phase)).dp.toPx()
                rotationZ = tilt + rotation.value
            }, tint = color)
    } else {
        Icon(painterResource(drawable), null,
            Modifier.size((200 * scale).dp).graphicsLayer {
                translationX = x.dp.toPx(); translationY = y.dp.toPx(); rotationZ = tilt
            }, tint = color)
    }
}
