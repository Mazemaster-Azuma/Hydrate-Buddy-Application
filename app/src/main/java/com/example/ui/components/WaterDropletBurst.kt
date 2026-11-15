package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class DropletParticle(
    val angleRad: Float,
    val distanceFactor: Float,
    val radiusDp: Float,
    val color: Color
)

@Composable
fun WaterDropletBurst(
    triggerCount: Int,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(1f) }
    val particles = remember {
        val colors = listOf(
            Color(0xFF0EA5E9),
            Color(0xFF2DD4BF),
            Color(0xFF38BDF8),
            Color(0xFFA78BFA),
            Color(0xFF34D399)
        )
        List(12) { index ->
            val angle = ((index * 30) - 180) * (PI / 180.0)
            DropletParticle(
                angleRad = angle.toFloat(),
                distanceFactor = 0.65f + (index % 3) * 0.16f,
                radiusDp = 4f + (index % 3) * 2f,
                color = colors[index % colors.size]
            )
        }
    }

    LaunchedEffect(triggerCount) {
        if (triggerCount > 0) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
            )
        }
    }

    if (progress.value < 0.99f && triggerCount > 0) {
        Canvas(modifier = modifier) {
            val t = progress.value
            val alpha = (1f - t).coerceIn(0f, 1f)
            val maxSpread = size.minDimension * 0.48f

            particles.forEach { p ->
                val dist = maxSpread * p.distanceFactor * t
                val x = center.x + cos(p.angleRad) * dist
                val y = center.y + sin(p.angleRad) * dist - (t * 24f)
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = p.radiusDp * density * (1f - t * 0.35f),
                    center = Offset(x, y)
                )
            }
        }
    }
}
