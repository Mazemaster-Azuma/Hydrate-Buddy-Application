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
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalHydrateColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val RingTipColor = Color.White.copy(alpha = 0.94f)

@Composable
fun HydrationProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    ringSize: Dp = 248.dp,
    strokeWidth: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val animatedProgressState = animateFloatAsState(
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
    val sweepBrush = remember(isGoalMet, primary, secondary, success) {
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
        ProgressRingCanvas(
            animatedProgressState = animatedProgressState,
            strokeWidth = strokeWidth,
            trackColor = trackColor,
            primary = primary,
            sweepBrush = sweepBrush,
            modifier = Modifier.fillMaxSize()
        )

        content()
    }
}

@Composable
private fun ProgressRingCanvas(
    animatedProgressState: State<Float>,
    strokeWidth: Dp,
    trackColor: Color,
    primary: Color,
    sweepBrush: Brush,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val strokePx = with(density) { strokeWidth.toPx() }
    val insetPaddingPx = with(density) { 6.dp.toPx() }
    val innerRimStrokePx = with(density) { 1.2.dp.toPx() }
    val strokeStyle = remember(strokePx) {
        Stroke(width = strokePx, cap = StrokeCap.Round)
    }
    val innerRimStyle = remember(innerRimStrokePx) {
        Stroke(width = innerRimStrokePx)
    }
    val backdropColor = remember(primary) { primary.copy(alpha = 0.055f) }
    val innerRimColor = remember(primary) { primary.copy(alpha = 0.10f) }

    Canvas(modifier = modifier) {
        val inset = strokePx / 2f + insetPaddingPx
        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
        val topLeft = Offset(inset, inset)
        val currentProgress = animatedProgressState.value

        // Soft inner backdrop circle + subtle hairline rim
        val innerRadius = arcSize.width / 2f - strokePx * 0.45f
        drawCircle(
            color = backdropColor,
            radius = innerRadius,
            center = center
        )
        drawCircle(
            color = innerRimColor,
            radius = innerRadius,
            center = center,
            style = innerRimStyle
        )

        // Track ring
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = strokeStyle
        )

        if (currentProgress > 0.005f) {
            val sweep = 360f * currentProgress
            drawArc(
                brush = sweepBrush,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = strokeStyle
            )

            // Glowing tip highlight dot at end of arc
            val angleRad = ((-90f + sweep) * PI / 180.0).toFloat()
            val radius = arcSize.width / 2f
            val tipX = center.x + cos(angleRad) * radius
            val tipY = center.y + sin(angleRad) * radius
            drawCircle(
                color = RingTipColor,
                radius = strokePx * 0.25f,
                center = Offset(tipX, tipY)
            )
        }
    }
}
