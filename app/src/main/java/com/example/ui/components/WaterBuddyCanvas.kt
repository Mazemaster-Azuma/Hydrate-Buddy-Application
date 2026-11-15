package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BuddyMood
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val EyelidTopColor = Color(0xFF55D1FE)
private val EyelidBottomColor = Color(0xFF42C1FC)
private val EyelidInkColor = Color(0xFF0E2439)
private val CrownGoldTop = Color(0xFFFDE047)
private val CrownGoldBottom = Color(0xFFF59E0B)
private val CrownBorderColor = Color(0xFFB45309)
private val CrownGemColor = Color(0xFF38BDF8)
private val SweatOuterColor = Color(0xFFE0F2FE)
private val SweatHighlightColor = Color.White.copy(alpha = 0.85f)

private val CelebratingSparkleColors = listOf(
    Color(0xFFFBBF24).copy(alpha = 0.88f),
    Color(0xFF34D399).copy(alpha = 0.88f),
    Color(0xFFA78BFA).copy(alpha = 0.88f),
    Color(0xFF38BDF8).copy(alpha = 0.88f)
)

private val ExcitedSparkleColors = listOf(
    Color(0xFF38BDF8).copy(alpha = 0.88f),
    Color(0xFF2DD4BF).copy(alpha = 0.88f),
    Color(0xFFA78BFA).copy(alpha = 0.88f)
)

// Exact normalized positions matching R.drawable.hydrate_buddy_character
private const val LEFT_EYE_X = 0.3737f
private const val RIGHT_EYE_X = 0.6250f
private const val EYE_CENTER_Y = 0.5301f
private const val EYE_RADIUS_RATIO = 0.049f
private const val DROPLET_TIP_Y = 0.0705f

private class CharacterOverlayCache {
    var cachedW = -1f
    var cachedH = -1f
    val blinkLeftPath = Path()
    val blinkRightPath = Path()
    val crownPath = Path()
    val sweatPath = Path()

    var blinkStroke = Stroke(width = 4f, cap = StrokeCap.Round)
    var crownStroke = Stroke(width = 3f, join = StrokeJoin.Round)
    var sweatStroke = Stroke(width = 2f)

    fun ensureSize(w: Float, h: Float) {
        if (w == cachedW && h == cachedH) return
        cachedW = w
        cachedH = h

        val eyeRadius = w * EYE_RADIUS_RATIO
        val eyeY = h * EYE_CENTER_Y
        val leftCx = w * LEFT_EYE_X
        val rightCx = w * RIGHT_EYE_X

        blinkLeftPath.rewind()
        blinkLeftPath.moveTo(leftCx - eyeRadius * 0.88f, eyeY - eyeRadius * 0.05f)
        blinkLeftPath.quadraticTo(
            leftCx,
            eyeY + eyeRadius * 0.55f,
            leftCx + eyeRadius * 0.88f,
            eyeY - eyeRadius * 0.05f
        )

        blinkRightPath.rewind()
        blinkRightPath.moveTo(rightCx - eyeRadius * 0.88f, eyeY - eyeRadius * 0.05f)
        blinkRightPath.quadraticTo(
            rightCx,
            eyeY + eyeRadius * 0.55f,
            rightCx + eyeRadius * 0.88f,
            eyeY - eyeRadius * 0.05f
        )

        blinkStroke = Stroke(width = (w * 0.017f).coerceAtLeast(2.5f), cap = StrokeCap.Round)

        // Crown positioned just above the droplet tip
        val cx = w * 0.5f
        val crownWidth = w * 0.19f
        val half = crownWidth / 2f
        val crownHeight = crownWidth * 0.58f
        val crownBottomY = h * (DROPLET_TIP_Y + 0.008f)
        val crownTopY = crownBottomY - crownHeight

        crownPath.rewind()
        crownPath.moveTo(cx - half, crownBottomY)
        crownPath.lineTo(cx - half * 1.10f, crownTopY + crownHeight * 0.24f)
        crownPath.lineTo(cx - half * 0.42f, crownTopY + crownHeight * 0.62f)
        crownPath.lineTo(cx, crownTopY)
        crownPath.lineTo(cx + half * 0.42f, crownTopY + crownHeight * 0.62f)
        crownPath.lineTo(cx + half * 1.10f, crownTopY + crownHeight * 0.24f)
        crownPath.lineTo(cx + half, crownBottomY)
        crownPath.close()

        crownStroke = Stroke(
            width = (w * 0.011f).coerceAtLeast(1.8f),
            join = StrokeJoin.Round
        )

        // Sweat drop near upper-right temple for thirsty mood
        val sx = w * 0.72f
        val sy = h * 0.38f
        val sr = w * 0.032f
        sweatPath.rewind()
        sweatPath.moveTo(sx, sy - sr * 1.45f)
        sweatPath.cubicTo(
            sx + sr * 0.8f, sy - sr * 0.4f,
            sx + sr * 1.05f, sy + sr * 0.5f,
            sx, sy + sr * 0.9f
        )
        sweatPath.cubicTo(
            sx - sr * 1.05f, sy + sr * 0.5f,
            sx - sr * 0.8f, sy - sr * 0.4f,
            sx, sy - sr * 1.45f
        )
        sweatPath.close()
        sweatStroke = Stroke(width = (w * 0.008f).coerceAtLeast(1.2f))
    }
}

@Composable
fun WaterBuddyCanvas(
    mood: BuddyMood,
    modifier: Modifier = Modifier,
    size: Dp = 176.dp,
    onBuddyTap: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "buddy_idle")

    // Mood-synced floating vertical motion
    val floatAmp = when (mood) {
        BuddyMood.CELEBRATING -> 7f
        BuddyMood.EXCITED -> 6.5f
        BuddyMood.THIRSTY -> 3.5f
        else -> 5.5f
    }
    val floatOffsetState = infiniteTransition.animateFloat(
        initialValue = -floatAmp,
        targetValue = floatAmp,
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

    // Subtle organic liquid squash-and-stretch breathing
    val breathePhaseState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mood) {
                    BuddyMood.CELEBRATING, BuddyMood.EXCITED -> 1150
                    BuddyMood.THIRSTY -> 2800
                    else -> 2100
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_phase"
    )

    // Natural periodic blink
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(mood) {
        while (true) {
            delay(3400L)
            isBlinking = true
            delay(135L)
            isBlinking = false
        }
    }

    // Playful tap bounce + subtle tilt wiggle
    var tapPopped by remember { mutableStateOf(false) }
    var tapCount by remember { mutableStateOf(0) }
    val tapScaleState = animateFloatAsState(
        targetValue = if (tapPopped) 1.13f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buddy_tap_scale"
    )
    val tapTiltState = animateFloatAsState(
        targetValue = if (tapPopped) {
            if (tapCount % 2 == 0) 5.5f else -5.5f
        } else {
            0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "buddy_tap_tilt"
    )

    LaunchedEffect(tapPopped) {
        if (tapPopped) {
            delay(220L)
            tapPopped = false
        }
    }

    val thirstyColorFilter = remember(mood) {
        if (mood == BuddyMood.THIRSTY) {
            ColorFilter.colorMatrix(
                ColorMatrix().apply { setToSaturation(0.76f) }
            )
        } else {
            null
        }
    }

    val overlayCache = remember { CharacterOverlayCache() }

    val eyelidBrush = remember {
        Brush.verticalGradient(listOf(EyelidTopColor, EyelidBottomColor))
    }
    val crownBrush = remember {
        Brush.verticalGradient(listOf(CrownGoldTop, CrownGoldBottom))
    }

    val clickableModifier = if (onBuddyTap != null) {
        val interactionSource = remember { MutableInteractionSource() }
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button
        ) {
            tapCount++
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
        // Subtle radiant aura behind the character when hydrated / celebrating
        if (mood != BuddyMood.THIRSTY) {
            BuddyAuraBackdrop(
                mood = mood,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = floatOffsetState.value
                    val breathe = breathePhaseState.value
                    val tapScale = tapScaleState.value
                    scaleX = tapScale * (1f + 0.018f * (breathe - 0.5f))
                    scaleY = tapScale * (1f - 0.018f * (breathe - 0.5f))
                    rotationZ = tapTiltState.value
                },
            contentAlignment = Alignment.Center
        ) {
            // Orbiting celebration/excited sparkles
            if (mood == BuddyMood.CELEBRATING || mood == BuddyMood.EXCITED) {
                BuddySparkleOverlay(
                    isCelebrating = mood == BuddyMood.CELEBRATING,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Main character artwork provided by user
            Image(
                painter = painterResource(id = R.drawable.hydrate_buddy_character),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = thirstyColorFilter,
                modifier = Modifier.fillMaxSize()
            )

            // Lightweight expression overlay (blinking, celebrating crown, thirsty sweat drop)
            if (isBlinking || mood == BuddyMood.CELEBRATING || mood == BuddyMood.THIRSTY) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val h = this.size.height
                    overlayCache.ensureSize(w, h)

                    if (isBlinking && mood != BuddyMood.CELEBRATING) {
                        val eyeRadius = w * EYE_RADIUS_RATIO * 1.18f
                        val eyeY = h * EYE_CENTER_Y
                        val leftCx = w * LEFT_EYE_X
                        val rightCx = w * RIGHT_EYE_X

                        drawOval(
                            brush = eyelidBrush,
                            topLeft = Offset(leftCx - eyeRadius, eyeY - eyeRadius),
                            size = Size(eyeRadius * 2f, eyeRadius * 2f)
                        )
                        drawOval(
                            brush = eyelidBrush,
                            topLeft = Offset(rightCx - eyeRadius, eyeY - eyeRadius),
                            size = Size(eyeRadius * 2f, eyeRadius * 2f)
                        )

                        drawPath(
                            path = overlayCache.blinkLeftPath,
                            color = EyelidInkColor,
                            style = overlayCache.blinkStroke
                        )
                        drawPath(
                            path = overlayCache.blinkRightPath,
                            color = EyelidInkColor,
                            style = overlayCache.blinkStroke
                        )
                    }

                    if (mood == BuddyMood.CELEBRATING) {
                        rotate(degrees = -5f, pivot = Offset(w * 0.5f, h * DROPLET_TIP_Y)) {
                            drawPath(path = overlayCache.crownPath, brush = crownBrush)
                            drawPath(
                                path = overlayCache.crownPath,
                                color = CrownBorderColor,
                                style = overlayCache.crownStroke
                            )
                            drawCircle(
                                color = CrownGemColor,
                                radius = w * 0.012f,
                                center = Offset(w * 0.5f, h * (DROPLET_TIP_Y - 0.025f))
                            )
                        }
                    }

                    if (mood == BuddyMood.THIRSTY) {
                        drawPath(path = overlayCache.sweatPath, color = SweatOuterColor)
                        drawPath(
                            path = overlayCache.sweatPath,
                            color = Color(0xFF38BDF8).copy(alpha = 0.7f),
                            style = overlayCache.sweatStroke
                        )
                        drawCircle(
                            color = SweatHighlightColor,
                            radius = w * 0.008f,
                            center = Offset(w * 0.712f, h * 0.375f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BuddyAuraBackdrop(
    mood: BuddyMood,
    modifier: Modifier = Modifier
) {
    val auraTransition = rememberInfiniteTransition(label = "buddy_aura")
    val pulseState = auraTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )

    val auraBrush = remember(mood) {
        val coreColor = when (mood) {
            BuddyMood.CELEBRATING -> Color(0xFF34D399).copy(alpha = 0.24f)
            BuddyMood.EXCITED -> Color(0xFF38BDF8).copy(alpha = 0.22f)
            else -> Color(0xFF38BDF8).copy(alpha = 0.14f)
        }
        Brush.radialGradient(
            colors = listOf(coreColor, Color.Transparent)
        )
    }

    Canvas(
        modifier = modifier.graphicsLayer {
            alpha = pulseState.value
            val s = 0.96f + 0.06f * pulseState.value
            scaleX = s
            scaleY = s
        }
    ) {
        drawCircle(
            brush = auraBrush,
            radius = size.minDimension * 0.44f,
            center = Offset(size.width * 0.5f, size.height * 0.52f)
        )
    }
}

@Composable
private fun BuddySparkleOverlay(
    isCelebrating: Boolean,
    modifier: Modifier = Modifier
) {
    val sparkleTransition = rememberInfiniteTransition(label = "buddy_sparkles")
    val sparklePhaseState = sparkleTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkle_phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.50f
        val radius = w * 0.43f
        val phase = sparklePhaseState.value

        val colors = if (isCelebrating) CelebratingSparkleColors else ExcitedSparkleColors
        val count = if (isCelebrating) 8 else 5
        val baseRadius = w * 0.022f
        val step = (2 * PI / count).toFloat()

        for (i in 0 until count) {
            val angle = phase + i * step
            val dist = radius * (0.94f + 0.08f * sin(angle * 2f))
            val sx = cx + cos(angle) * dist
            val sy = cy + sin(angle) * dist * 0.90f
            val twinkle = 0.72f + 0.28f * cos(phase * 2f + i)
            drawCircle(
                color = colors[i % colors.size],
                radius = baseRadius * twinkle,
                center = Offset(sx, sy)
            )
        }
    }
}
