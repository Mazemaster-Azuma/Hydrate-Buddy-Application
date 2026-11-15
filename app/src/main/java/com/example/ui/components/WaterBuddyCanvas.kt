package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.BuddyMood
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ShadowColor = Color(0xFF0284C7).copy(alpha = 0.14f)
private val RimColor = Color(0xFF0369A1).copy(alpha = 0.28f)
private val InkColor = Color(0xFF0F2537)
private val CheekColorBase = Color(0xFFFB7185)
private val CrownGold = Color(0xFFFBBF24)
private val CrownBorder = Color(0xFFD97706)
private val SweatColor = Color(0xFFE0F2FE)
private val CelebratingSparkleColors = listOf(
    Color(0xFFFBBF24),
    Color(0xFF34D399),
    Color(0xFFA78BFA),
    Color(0xFF38BDF8)
)
private val ExcitedSparkleColors = listOf(
    Color(0xFF38BDF8),
    Color(0xFF2DD4BF),
    Color(0xFFA78BFA)
)

@Composable
fun WaterBuddyCanvas(
    mood: BuddyMood,
    modifier: Modifier = Modifier,
    size: Dp = 176.dp,
    onBuddyTap: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "buddy_idle")

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mood) {
                    BuddyMood.CELEBRATING, BuddyMood.EXCITED -> 900
                    BuddyMood.THIRSTY -> 2400
                    else -> 1650
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )

    val sparklePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkle_phase"
    )

    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(mood) {
        while (true) {
            delay(3400L)
            isBlinking = true
            delay(130L)
            isBlinking = false
        }
    }

    var tapPopped by remember { mutableStateOf(false) }
    val tapScale by animateFloatAsState(
        targetValue = if (tapPopped) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buddy_tap_scale"
    )

    LaunchedEffect(tapPopped) {
        if (tapPopped) {
            delay(220L)
            tapPopped = false
        }
    }

    // Pre-allocate paths and gradient brushes outside Canvas onDraw for zero per-frame GC churn
    val dropPath = remember { Path() }
    val eyeArcPath = remember { Path() }
    val mouthPath = remember { Path() }
    val crownPath = remember { Path() }

    val bodyBrush = remember(mood) {
        when (mood) {
            BuddyMood.THIRSTY -> Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFBAE6FD),
                    Color(0xFF7DD3FC),
                    Color(0xFF60A5FA)
                )
            )
            BuddyMood.CELEBRATING -> Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF67E8F9),
                    Color(0xFF22D3EE),
                    Color(0xFF0284C7)
                )
            )
            BuddyMood.EXCITED -> Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFA5F3FC),
                    Color(0xFF38BDF8),
                    Color(0xFF0284C7)
                )
            )
            else -> Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF99F6E4),
                    Color(0xFF38BDF8),
                    Color(0xFF0284C7)
                )
            )
        }
    }

    val clickableModifier = if (onBuddyTap != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button
        ) {
            tapPopped = true
            onBuddyTap()
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .testTag("water_buddy_character")
            .semantics {
                contentDescription = "Droplet virtual water buddy, currently feeling ${mood.label}"
            }
            .then(clickableModifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = floatOffset
                    scaleX = tapScale
                    scaleY = tapScale
                }
        ) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h * 0.56f
            val dropRadius = w * 0.33f

            // Soft ground shadow below Droplet
            drawOval(
                color = ShadowColor,
                topLeft = Offset(cx - dropRadius * 0.72f, h * 0.88f),
                size = Size(dropRadius * 1.44f, h * 0.07f)
            )

            // Celebration / Excited orbiting aura sparkles
            if (mood == BuddyMood.CELEBRATING || mood == BuddyMood.EXCITED) {
                drawCelebrationSparkles(
                    cx = cx,
                    cy = cy,
                    radius = dropRadius * 1.32f,
                    phase = sparklePhase,
                    isCelebrating = mood == BuddyMood.CELEBRATING
                )
            }

            // Teardrop body path (reused via rewind)
            dropPath.rewind()
            val topY = h * 0.12f
            val bottomY = h * 0.86f
            dropPath.moveTo(cx, topY)
            dropPath.cubicTo(
                cx + dropRadius * 0.35f, h * 0.30f,
                cx + dropRadius * 1.16f, h * 0.52f,
                cx + dropRadius * 1.02f, cy + dropRadius * 0.25f
            )
            dropPath.cubicTo(
                cx + dropRadius * 0.92f, bottomY,
                cx + dropRadius * 0.42f, bottomY,
                cx, bottomY
            )
            dropPath.cubicTo(
                cx - dropRadius * 0.42f, bottomY,
                cx - dropRadius * 0.92f, bottomY,
                cx - dropRadius * 1.02f, cy + dropRadius * 0.25f
            )
            dropPath.cubicTo(
                cx - dropRadius * 1.16f, h * 0.52f,
                cx - dropRadius * 0.35f, h * 0.30f,
                cx, topY
            )
            dropPath.close()

            drawPath(path = dropPath, brush = bodyBrush)

            // Crisp soft outer rim
            drawPath(
                path = dropPath,
                color = RimColor,
                style = Stroke(width = w * 0.018f)
            )

            // Glossy upper-left specular highlight
            rotate(degrees = -26f, pivot = Offset(cx - dropRadius * 0.44f, cy - dropRadius * 0.16f)) {
                drawOval(
                    color = Color.White.copy(alpha = 0.58f),
                    topLeft = Offset(cx - dropRadius * 0.54f, cy - dropRadius * 0.42f),
                    size = Size(dropRadius * 0.22f, dropRadius * 0.52f)
                )
            }
            drawCircle(
                color = Color.White.copy(alpha = 0.65f),
                radius = dropRadius * 0.075f,
                center = Offset(cx - dropRadius * 0.26f, cy - dropRadius * 0.52f)
            )

            // Rosy cheeks (softer when thirsty)
            val cheekAlpha = if (mood == BuddyMood.THIRSTY) 0.22f else 0.62f
            val cheekColor = CheekColorBase.copy(alpha = cheekAlpha)
            drawOval(
                color = cheekColor,
                topLeft = Offset(cx - dropRadius * 0.68f, cy + dropRadius * 0.16f),
                size = Size(dropRadius * 0.30f, dropRadius * 0.16f)
            )
            drawOval(
                color = cheekColor,
                topLeft = Offset(cx + dropRadius * 0.38f, cy + dropRadius * 0.16f),
                size = Size(dropRadius * 0.30f, dropRadius * 0.16f)
            )

            // Eyes & facial expression
            drawBuddyFace(
                cx = cx,
                cy = cy + dropRadius * 0.04f,
                dropRadius = dropRadius,
                mood = mood,
                isBlinking = isBlinking,
                eyeArcPath = eyeArcPath,
                mouthPath = mouthPath
            )

            // Cute tiny celebration crown when goal reached!
            if (mood == BuddyMood.CELEBRATING) {
                drawTinyCrown(
                    cx = cx,
                    topY = h * 0.06f,
                    width = dropRadius * 0.52f,
                    crownPath = crownPath
                )
            }
        }
    }
}

private fun DrawScope.drawBuddyEye(
    idx: Int,
    ex: Float,
    eyeY: Float,
    eyeRadius: Float,
    dropRadius: Float,
    mood: BuddyMood,
    isBlinking: Boolean,
    eyeArcPath: Path
) {
    when {
        mood == BuddyMood.CELEBRATING -> {
            eyeArcPath.rewind()
            eyeArcPath.moveTo(ex - eyeRadius, eyeY + eyeRadius * 0.25f)
            eyeArcPath.quadraticTo(ex, eyeY - eyeRadius * 0.95f, ex + eyeRadius, eyeY + eyeRadius * 0.25f)
            drawPath(
                path = eyeArcPath,
                color = InkColor,
                style = Stroke(width = dropRadius * 0.07f, cap = StrokeCap.Round)
            )
        }
        isBlinking -> {
            drawLine(
                color = InkColor,
                start = Offset(ex - eyeRadius * 0.9f, eyeY),
                end = Offset(ex + eyeRadius * 0.9f, eyeY),
                strokeWidth = dropRadius * 0.065f,
                cap = StrokeCap.Round
            )
        }
        mood == BuddyMood.THIRSTY -> {
            drawCircle(
                color = InkColor,
                radius = eyeRadius * 0.85f,
                center = Offset(ex, eyeY + eyeRadius * 0.15f)
            )
            drawCircle(
                color = Color.White,
                radius = eyeRadius * 0.28f,
                center = Offset(ex - eyeRadius * 0.25f, eyeY - eyeRadius * 0.05f)
            )
            val browStart = if (idx == 0) {
                Offset(ex - eyeRadius, eyeY - eyeRadius * 0.6f)
            } else {
                Offset(ex + eyeRadius, eyeY - eyeRadius * 0.6f)
            }
            val browEnd = if (idx == 0) {
                Offset(ex + eyeRadius * 0.8f, eyeY - eyeRadius * 0.95f)
            } else {
                Offset(ex - eyeRadius * 0.8f, eyeY - eyeRadius * 0.95f)
            }
            drawLine(
                color = InkColor,
                start = browStart,
                end = browEnd,
                strokeWidth = dropRadius * 0.055f,
                cap = StrokeCap.Round
            )
        }
        else -> {
            drawCircle(
                color = InkColor,
                radius = eyeRadius,
                center = Offset(ex, eyeY)
            )
            drawCircle(
                color = Color.White,
                radius = eyeRadius * 0.38f,
                center = Offset(ex - eyeRadius * 0.28f, eyeY - eyeRadius * 0.30f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = eyeRadius * 0.16f,
                center = Offset(ex + eyeRadius * 0.28f, eyeY + eyeRadius * 0.28f)
            )
        }
    }
}

private fun DrawScope.drawBuddyFace(
    cx: Float,
    cy: Float,
    dropRadius: Float,
    mood: BuddyMood,
    isBlinking: Boolean,
    eyeArcPath: Path,
    mouthPath: Path
) {
    val eyeOffsetX = dropRadius * 0.36f
    val eyeY = cy - dropRadius * 0.08f
    val eyeRadius = dropRadius * 0.145f

    drawBuddyEye(0, cx - eyeOffsetX, eyeY, eyeRadius, dropRadius, mood, isBlinking, eyeArcPath)
    drawBuddyEye(1, cx + eyeOffsetX, eyeY, eyeRadius, dropRadius, mood, isBlinking, eyeArcPath)

    // Mouth
    val mouthY = cy + dropRadius * 0.24f
    when (mood) {
        BuddyMood.CELEBRATING, BuddyMood.EXCITED -> {
            val smileWidth = dropRadius * 0.42f
            val smileHeight = dropRadius * 0.28f
            mouthPath.rewind()
            mouthPath.moveTo(cx - smileWidth / 2f, mouthY - smileHeight * 0.15f)
            mouthPath.quadraticTo(cx, mouthY + smileHeight * 1.15f, cx + smileWidth / 2f, mouthY - smileHeight * 0.15f)
            mouthPath.close()
            drawPath(path = mouthPath, color = InkColor)
            drawOval(
                color = CheekColorBase,
                topLeft = Offset(cx - smileWidth * 0.24f, mouthY + smileHeight * 0.18f),
                size = Size(smileWidth * 0.48f, smileHeight * 0.38f)
            )
        }
        BuddyMood.HAPPY -> {
            mouthPath.rewind()
            mouthPath.moveTo(cx - dropRadius * 0.20f, mouthY)
            mouthPath.quadraticTo(cx, mouthY + dropRadius * 0.22f, cx + dropRadius * 0.20f, mouthY)
            drawPath(
                path = mouthPath,
                color = InkColor,
                style = Stroke(width = dropRadius * 0.068f, cap = StrokeCap.Round)
            )
        }
        BuddyMood.NEUTRAL -> {
            drawRoundRect(
                color = InkColor,
                topLeft = Offset(cx - dropRadius * 0.13f, mouthY + dropRadius * 0.02f),
                size = Size(dropRadius * 0.26f, dropRadius * 0.065f),
                cornerRadius = CornerRadius(12f, 12f)
            )
        }
        BuddyMood.THIRSTY -> {
            mouthPath.rewind()
            mouthPath.moveTo(cx - dropRadius * 0.18f, mouthY + dropRadius * 0.08f)
            mouthPath.quadraticTo(cx - dropRadius * 0.08f, mouthY - dropRadius * 0.02f, cx, mouthY + dropRadius * 0.06f)
            mouthPath.quadraticTo(cx + dropRadius * 0.08f, mouthY + dropRadius * 0.14f, cx + dropRadius * 0.18f, mouthY + dropRadius * 0.04f)
            drawPath(
                path = mouthPath,
                color = InkColor,
                style = Stroke(width = dropRadius * 0.06f, cap = StrokeCap.Round)
            )
            drawCircle(
                color = SweatColor,
                radius = dropRadius * 0.075f,
                center = Offset(cx + dropRadius * 0.62f, cy - dropRadius * 0.42f)
            )
        }
    }
}

private fun DrawScope.drawCelebrationSparkles(
    cx: Float,
    cy: Float,
    radius: Float,
    phase: Float,
    isCelebrating: Boolean
) {
    val colors = if (isCelebrating) CelebratingSparkleColors else ExcitedSparkleColors
    val count = if (isCelebrating) 8 else 5
    for (i in 0 until count) {
        val angle = phase + (i * (2 * PI / count)).toFloat()
        val dist = radius * (0.95f + 0.10f * sin(angle * 2f))
        val sx = cx + cos(angle) * dist
        val sy = cy + sin(angle) * dist * 0.85f
        val color = colors[i % colors.size]
        drawCircle(
            color = color.copy(alpha = 0.85f),
            radius = radius * 0.055f,
            center = Offset(sx, sy)
        )
    }
}

private fun DrawScope.drawTinyCrown(
    cx: Float,
    topY: Float,
    width: Float,
    crownPath: Path
) {
    crownPath.rewind()
    val half = width / 2f
    val height = width * 0.56f
    crownPath.moveTo(cx - half, topY + height)
    crownPath.lineTo(cx - half * 1.05f, topY + height * 0.25f)
    crownPath.lineTo(cx - half * 0.4f, topY + height * 0.62f)
    crownPath.lineTo(cx, topY)
    crownPath.lineTo(cx + half * 0.4f, topY + height * 0.62f)
    crownPath.lineTo(cx + half * 1.05f, topY + height * 0.25f)
    crownPath.lineTo(cx + half, topY + height)
    crownPath.close()

    drawPath(path = crownPath, color = CrownGold)
    drawPath(
        path = crownPath,
        color = CrownBorder,
        style = Stroke(width = width * 0.06f)
    )
}
