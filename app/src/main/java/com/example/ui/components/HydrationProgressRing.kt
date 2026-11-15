package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalHydrateColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HydrationProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    ringSize: Dp = 248.dp,
    strokeWidth: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "hydration_ring_progress"
    )

    val trackColor = LocalHydrateColors.current.waterRingTrack
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val success = LocalHydrateColors.current.success

    val isGoalMet = progress >= 1f
    val sweepBrush = androidx.compose.runtime.remember(isGoalMet, primary, secondary, success) {
        val colors = if (isGoalMet) {
            listOf(secondary, success, Color(0xFF34D399), secondary)
        } else {
            listOf(primary, Color(0xFF38BDF8), secondary, primary)
        }
        Brush.sweepGradient(colors = colors)
    }

    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f + 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)

            // Soft inner backdrop circle
            drawCircle(
                color = primary.copy(alpha = 0.06f),
                radius = arcSize.width / 2f - strokePx * 0.4f,
                center = center
            )

            // Track ring
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            if (animatedProgress > 0.005f) {
                val sweep = 360f * animatedProgress
                drawArc(
                    brush = sweepBrush,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Glowing tip dot at end of arc
                val angleRad = ((-90f + sweep) * PI / 180.0).toFloat()
                val radius = arcSize.width / 2f
                val tipX = center.x + cos(angleRad) * radius
                val tipY = center.y + sin(angleRad) * radius
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = strokePx * 0.24f,
                    center = Offset(tipX, tipY)
                )
            }
        }

        content()
    }
}
