package com.example.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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

// Facial feature & accent palette matching R.drawable.hydrate_buddy_character
private val EyeInkColor = Color(0xFF0A1D33)
private val EyeIrisTint = Color(0xFF103052)
private val EyeTearPoolColor = Color(0xFF7DD3FC)
private val MouthInteriorColor = Color(0xFF7F1D3A)
private val ThirstyMouthInteriorColor = Color(0xFF9F2A4D)
private val TongueColor = Color(0xFFFB7185)
private val BrowInkColor = Color(0xFF0C3559)
private val CheekGlowColor = Color(0xFFFF6B9A)
private val CrownGoldTop = Color(0xFFFDE047)
private val CrownGoldBottom = Color(0xFFF59E0B)
private val CrownBorderColor = Color(0xFFB45309)
private val CrownGemColor = Color(0xFF38BDF8)
private val SweatOuterColor = Color(0xFFE0F2FE)
private val SweatBorderColor = Color(0xFF38BDF8)
private val SweatHighlightColor = Color.White.copy(alpha = 0.9f)
private val SadTearColor = Color(0xFFBAE6FD)
private val SadTearStrokeColor = Color(0xFF0284C7)
private val TapHeartColor = Color(0xFFFF5D8F)

private val CelebratingSparkleColors = listOf(
    Color(0xFFFBBF24).copy(alpha = 0.90f),
    Color(0xFF34D399).copy(alpha = 0.90f),
    Color(0xFFA78BFA).copy(alpha = 0.90f),
    Color(0xFF38BDF8).copy(alpha = 0.90f)
)

private val ExcitedSparkleColors = listOf(
    Color(0xFF38BDF8).copy(alpha = 0.90f),
    Color(0xFF2DD4BF).copy(alpha = 0.90f),
    Color(0xFFA78BFA).copy(alpha = 0.90f)
)

// Exact normalized coordinates matching faceless R.drawable.hydrate_buddy_character
private const val LEFT_EYE_X = 0.3737f
private const val RIGHT_EYE_X = 0.6250f
private const val EYE_CENTER_Y = 0.5301f
private const val EYE_RADIUS_RATIO = 0.0505f

private const val MOUTH_CENTER_X = 0.5000f
private const val MOUTH_CENTER_Y = 0.6635f

private const val LEFT_CHEEK_X = 0.318f
private const val RIGHT_CHEEK_X = 0.682f
private const val CHEEK_Y = 0.605f

private const val DROPLET_TIP_Y = 0.0705f

private class CharacterOverlayCache {
    var cachedW = -1f
    var cachedH = -1f

    val path = Path()
    val clipMouthPath = Path()
    val crownPath = Path()
    val sweatPath = Path()
    val tearDropPath = Path()
    val burstHeartPath = Path()

    var featureStroke = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    var browStroke = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    var crownStroke = Stroke(width = 2.5f, join = StrokeJoin.Round)
    var sweatStroke = Stroke(width = 2f)

    fun ensureSize(w: Float, h: Float) {
        if (w == cachedW && h == cachedH) return
        cachedW = w
        cachedH = h

        featureStroke = Stroke(
            width = (w * 0.0175f).coerceAtLeast(2.4f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        browStroke = Stroke(
            width = (w * 0.0135f).coerceAtLeast(2.0f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        // Crown positioned just above the droplet tip
        val cx = w * 0.5f
        val crownWidth = w * 0.195f
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

        // Sweat drop template centered around origin (0, 0)
        val sr = w * 0.028f
        sweatPath.rewind()
        sweatPath.moveTo(0f, -sr * 1.45f)
        sweatPath.cubicTo(
            sr * 0.8f, -sr * 0.4f,
            sr * 1.05f, sr * 0.5f,
            0f, sr * 0.9f
        )
        sweatPath.cubicTo(
            -sr * 1.05f, sr * 0.5f,
            -sr * 0.8f, -sr * 0.4f,
            0f, -sr * 1.45f
        )
        sweatPath.close()
        sweatStroke = Stroke(width = (w * 0.008f).coerceAtLeast(1.2f))

        // Teardrop template centered at origin for falling tear under sad eye
        val tr = w * 0.021f
        tearDropPath.rewind()
        tearDropPath.moveTo(0f, -tr * 1.55f)
        tearDropPath.cubicTo(
            tr * 0.85f, -tr * 0.35f,
            tr * 1.05f, tr * 0.55f,
            0f, tr * 0.95f
        )
        tearDropPath.cubicTo(
            -tr * 1.05f, tr * 0.55f,
            -tr * 0.85f, -tr * 0.35f,
            0f, -tr * 1.55f
        )
        tearDropPath.close()

        // Tiny heart template centered at origin for joyful tap reactions
        val hs = w * 0.030f
        burstHeartPath.rewind()
        burstHeartPath.moveTo(0f, hs * 0.85f)
        burstHeartPath.cubicTo(-hs * 1.35f, hs * 0.1f, -hs * 1.15f, -hs * 0.95f, 0f, -hs * 0.30f)
        burstHeartPath.cubicTo(hs * 1.15f, -hs * 0.95f, hs * 1.35f, hs * 0.1f, 0f, hs * 0.85f)
        burstHeartPath.close()
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
        BuddyMood.THIRSTY -> 3.2f
        BuddyMood.SAD -> 2.0f
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
                    BuddyMood.SAD -> 2900
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
                    BuddyMood.THIRSTY -> 2700
                    BuddyMood.SAD -> 3300
                    else -> 2100
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_phase"
    )

    // Subtle horizontal glance / idle liveliness
    val glancePhaseState = infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eye_glance"
    )

    // Shimmer, falling sweat & falling teardrop timing
    val secondaryPhaseState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondary_phase"
    )

    // Smooth eyelid blink progression (0f = open, 1f = closed)
    val blinkAnim = remember { Animatable(0f) }
    LaunchedEffect(mood) {
        blinkAnim.snapTo(0f)
        var cycle = 0
        while (true) {
            val waitMs = when (mood) {
                BuddyMood.EXCITED -> 2900L
                BuddyMood.THIRSTY -> 4000L
                BuddyMood.SAD -> 4600L
                else -> 3400L
            }
            delay(waitMs)
            blinkAnim.animateTo(1f, tween(durationMillis = 72, easing = FastOutSlowInEasing))
            blinkAnim.animateTo(0f, tween(durationMillis = 98, easing = FastOutSlowInEasing))
            cycle++
            if (cycle % 3 == 0 && mood != BuddyMood.THIRSTY && mood != BuddyMood.SAD) {
                delay(120L)
                blinkAnim.animateTo(1f, tween(durationMillis = 65, easing = FastOutSlowInEasing))
                blinkAnim.animateTo(0f, tween(durationMillis = 90, easing = FastOutSlowInEasing))
            }
        }
    }

    // Playful tap bounce, tilt wiggle, expression reaction & burst
    var tapPopped by remember { mutableStateOf(false) }
    var tapExpressing by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    val tapBurstAnim = remember { Animatable(0f) }

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
            if (tapCount % 2 == 0) 6f else -6f
        } else {
            0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "buddy_tap_tilt"
    )

    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            tapPopped = true
            tapExpressing = true
            tapBurstAnim.snapTo(0f)
            delay(190L)
            tapPopped = false
        }
    }

    LaunchedEffect(tapExpressing, tapCount) {
        if (tapExpressing) {
            tapBurstAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 780, easing = FastOutSlowInEasing)
            )
            tapExpressing = false
        }
    }

    val moodColorFilter = remember(mood) {
        when (mood) {
            BuddyMood.THIRSTY -> ColorFilter.colorMatrix(
                ColorMatrix().apply { setToSaturation(0.76f) }
            )
            BuddyMood.SAD -> ColorFilter.colorMatrix(
                ColorMatrix().apply { setToSaturation(0.56f) }
            )
            else -> null
        }
    }

    val overlayCache = remember { CharacterOverlayCache() }
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
        if (mood != BuddyMood.THIRSTY && mood != BuddyMood.SAD) {
            BuddyAuraBackdrop(
                mood = mood,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = floatOffsetState.value + if (mood == BuddyMood.SAD) 4f else 0f
                    val breathe = breathePhaseState.value
                    val tapScale = tapScaleState.value
                    scaleX = tapScale * (1f + 0.018f * (breathe - 0.5f))
                    scaleY = tapScale * (1f - 0.018f * (breathe - 0.5f))
                    rotationZ = tapTiltState.value + if (mood == BuddyMood.SAD && !tapPopped) -2.5f else 0f
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

            // Clean faceless droplet artwork provided by user
            Image(
                painter = painterResource(id = R.drawable.hydrate_buddy_character),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = moodColorFilter,
                modifier = Modifier.fillMaxSize()
            )

            // Expressive facial features, eyebrows, blush boost, blink, crown, tears & tap reactions
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height
                overlayCache.ensureSize(w, h)

                val blink = blinkAnim.value
                val glance = glancePhaseState.value
                val secPhase = secondaryPhaseState.value
                val isLowMood = mood == BuddyMood.THIRSTY || mood == BuddyMood.SAD
                val isJoyfulTap = tapExpressing && !isLowMood
                val isWinkTap = isJoyfulTap && (tapCount % 2 == 0)
                val isHappySquintTap = isJoyfulTap && (tapCount % 2 != 0)

                val eyeRadius = w * EYE_RADIUS_RATIO
                val eyeY = h * EYE_CENTER_Y + if (mood == BuddyMood.SAD) h * 0.010f else 0f
                val leftCx = w * LEFT_EYE_X
                val rightCx = w * RIGHT_EYE_X
                val mouthCx = w * MOUTH_CENTER_X
                val mouthCy = h * MOUTH_CENTER_Y + if (mood == BuddyMood.SAD) h * 0.012f else 0f

                // 1. Dynamic rosy cheek glow boost for joyful states (muted when sad/thirsty)
                val cheekBoostAlpha = when {
                    isJoyfulTap -> 0.44f
                    mood == BuddyMood.CELEBRATING -> 0.38f
                    mood == BuddyMood.EXCITED -> 0.28f
                    mood == BuddyMood.HAPPY -> 0.14f
                    else -> 0f
                }
                if (cheekBoostAlpha > 0f) {
                    val cheekR = w * 0.072f
                    drawOval(
                        color = CheekGlowColor.copy(alpha = cheekBoostAlpha),
                        topLeft = Offset(w * LEFT_CHEEK_X - cheekR, h * CHEEK_Y - cheekR * 0.56f),
                        size = Size(cheekR * 2f, cheekR * 1.12f)
                    )
                    drawOval(
                        color = CheekGlowColor.copy(alpha = cheekBoostAlpha),
                        topLeft = Offset(w * RIGHT_CHEEK_X - cheekR, h * CHEEK_Y - cheekR * 0.56f),
                        size = Size(cheekR * 2f, cheekR * 1.12f)
                    )
                }

                // 2. Eyebrows for expressive emotion
                drawEyebrows(
                    mood = mood,
                    isJoyfulTap = isJoyfulTap,
                    leftCx = leftCx,
                    rightCx = rightCx,
                    eyeY = eyeY,
                    eyeRadius = eyeRadius,
                    cache = overlayCache
                )

                // 3. Eyes (with smooth blink, glance drift, watery tear pool for SAD, squint, or wink)
                val glanceShiftX = if (mood == BuddyMood.NEUTRAL || mood == BuddyMood.HAPPY) {
                    glance * w * 0.007f
                } else {
                    0f
                }
                val eyeScale = when {
                    mood == BuddyMood.EXCITED -> 1.10f
                    mood == BuddyMood.THIRSTY -> 0.96f
                    mood == BuddyMood.SAD -> if (tapExpressing) 1.05f else 0.92f
                    else -> 1f
                }

                when {
                    isHappySquintTap || mood == BuddyMood.CELEBRATING -> {
                        drawHappyArchEye(leftCx, eyeY, eyeRadius, overlayCache)
                        drawHappyArchEye(rightCx, eyeY, eyeRadius, overlayCache)
                    }
                    isWinkTap -> {
                        drawWinkEye(leftCx, eyeY, eyeRadius, overlayCache)
                        drawOpenGlossyEye(
                            cx = rightCx,
                            cy = eyeY,
                            radius = eyeRadius * 1.08f,
                            glanceX = 0f,
                            blink = 0f,
                            sparkleBoost = true,
                            droopy = false,
                            waterySad = false,
                            shimmerPhase = secPhase
                        )
                    }
                    else -> {
                        val sparkle = mood == BuddyMood.EXCITED
                        val droopy = mood == BuddyMood.THIRSTY || mood == BuddyMood.SAD
                        val waterySad = mood == BuddyMood.SAD
                        drawOpenGlossyEye(
                            cx = leftCx,
                            cy = eyeY,
                            radius = eyeRadius * eyeScale,
                            glanceX = glanceShiftX,
                            blink = blink,
                            sparkleBoost = sparkle,
                            droopy = droopy,
                            waterySad = waterySad,
                            shimmerPhase = secPhase,
                            cache = overlayCache
                        )
                        drawOpenGlossyEye(
                            cx = rightCx,
                            cy = eyeY,
                            radius = eyeRadius * eyeScale,
                            glanceX = glanceShiftX,
                            blink = blink,
                            sparkleBoost = sparkle,
                            droopy = droopy,
                            waterySad = waterySad,
                            shimmerPhase = secPhase,
                            cache = overlayCache
                        )
                    }
                }

                // 4. Expressive mouths
                when {
                    isJoyfulTap || mood == BuddyMood.CELEBRATING -> {
                        drawOpenLaughMouth(
                            cx = mouthCx,
                            cy = mouthCy,
                            mouthWidth = w * (if (mood == BuddyMood.CELEBRATING) 0.145f else 0.125f),
                            mouthHeight = w * (if (mood == BuddyMood.CELEBRATING) 0.095f else 0.082f),
                            cache = overlayCache
                        )
                    }
                    mood == BuddyMood.EXCITED -> {
                        drawOpenLaughMouth(
                            cx = mouthCx,
                            cy = mouthCy,
                            mouthWidth = w * 0.115f,
                            mouthHeight = w * 0.072f,
                            cache = overlayCache
                        )
                    }
                    mood == BuddyMood.HAPPY -> {
                        drawHappySmileMouth(
                            cx = mouthCx,
                            cy = mouthCy,
                            halfWidth = w * 0.056f,
                            depth = w * 0.038f,
                            cache = overlayCache
                        )
                    }
                    mood == BuddyMood.NEUTRAL -> {
                        drawHappySmileMouth(
                            cx = mouthCx,
                            cy = mouthCy,
                            halfWidth = w * 0.044f,
                            depth = w * 0.017f,
                            cache = overlayCache
                        )
                    }
                    mood == BuddyMood.THIRSTY -> {
                        if (tapExpressing) {
                            // Pleading parched little open "o" mouth on tap
                            drawOval(
                                color = ThirstyMouthInteriorColor,
                                topLeft = Offset(mouthCx - w * 0.025f, mouthCy - w * 0.018f),
                                size = Size(w * 0.050f, w * 0.038f)
                            )
                            drawOval(
                                color = EyeInkColor,
                                topLeft = Offset(mouthCx - w * 0.025f, mouthCy - w * 0.018f),
                                size = Size(w * 0.050f, w * 0.038f),
                                style = Stroke(width = (w * 0.011f).coerceAtLeast(1.8f))
                            )
                        } else {
                            drawThirstyWavyMouth(
                                cx = mouthCx,
                                cy = mouthCy,
                                halfWidth = w * 0.052f,
                                amp = w * 0.014f,
                                cache = overlayCache
                            )
                        }
                    }
                    mood == BuddyMood.SAD -> {
                        drawSadFrownMouth(
                            cx = mouthCx,
                            cy = mouthCy,
                            halfWidth = w * 0.058f,
                            height = w * 0.036f,
                            cache = overlayCache
                        )
                    }
                }

                // 5. Celebrating golden crown
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

                // 6. Thirsty animated falling sweat drop near upper-right temple
                if (mood == BuddyMood.THIRSTY) {
                    val dropProgress = secPhase
                    val dropAlpha = when {
                        dropProgress < 0.18f -> dropProgress / 0.18f
                        dropProgress > 0.72f -> (1f - dropProgress) / 0.28f
                        else -> 1f
                    }.coerceIn(0f, 1f)

                    val sx = w * 0.725f
                    val sy = h * (0.355f + dropProgress * 0.055f)
                    drawContext.canvas.save()
                    drawContext.canvas.translate(sx, sy)
                    drawPath(path = overlayCache.sweatPath, color = SweatOuterColor.copy(alpha = dropAlpha))
                    drawPath(
                        path = overlayCache.sweatPath,
                        color = SweatBorderColor.copy(alpha = 0.78f * dropAlpha),
                        style = overlayCache.sweatStroke
                    )
                    drawCircle(
                        color = SweatHighlightColor.copy(alpha = dropAlpha),
                        radius = w * 0.007f,
                        center = Offset(-w * 0.007f, -w * 0.005f)
                    )
                    drawContext.canvas.restore()
                }

                // 7. Sad animated falling teardrop sliding beneath the left eye
                if (mood == BuddyMood.SAD) {
                    val tearProgress = secPhase
                    val tearAlpha = when {
                        tearProgress < 0.15f -> tearProgress / 0.15f
                        tearProgress > 0.70f -> (1f - tearProgress) / 0.30f
                        else -> 1f
                    }.coerceIn(0f, 1f)

                    val tx = leftCx - eyeRadius * 0.32f
                    val ty = eyeY + eyeRadius * 1.15f + tearProgress * h * 0.095f
                    drawContext.canvas.save()
                    drawContext.canvas.translate(tx, ty)
                    drawPath(
                        path = overlayCache.tearDropPath,
                        color = SadTearColor.copy(alpha = 0.92f * tearAlpha)
                    )
                    drawPath(
                        path = overlayCache.tearDropPath,
                        color = SadTearStrokeColor.copy(alpha = 0.65f * tearAlpha),
                        style = overlayCache.sweatStroke
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.90f * tearAlpha),
                        radius = w * 0.005f,
                        center = Offset(-w * 0.004f, -w * 0.004f)
                    )
                    drawContext.canvas.restore()
                }

                // 8. Mood-aware rising tap reaction burst
                if (tapExpressing) {
                    val t = tapBurstAnim.value
                    val burstAlpha = (1f - t).coerceIn(0f, 1f)
                    if (burstAlpha > 0.02f) {
                        val rise = h * 0.11f * t
                        if (isLowMood) {
                            // Tiny pleading water drop floating up when thirsty/sad
                            val px = w * 0.72f + sin(t * PI).toFloat() * w * 0.018f
                            val py = h * 0.25f - rise
                            drawContext.canvas.save()
                            drawContext.canvas.translate(px, py)
                            drawPath(
                                path = overlayCache.tearDropPath,
                                color = SweatOuterColor.copy(alpha = burstAlpha)
                            )
                            drawPath(
                                path = overlayCache.tearDropPath,
                                color = SweatBorderColor.copy(alpha = 0.85f * burstAlpha),
                                style = overlayCache.sweatStroke
                            )
                            drawContext.canvas.restore()
                        } else {
                            val hx = w * 0.72f + sin(t * PI).toFloat() * w * 0.025f
                            val hy = h * 0.24f - rise
                            drawContext.canvas.save()
                            drawContext.canvas.translate(hx, hy)
                            drawPath(
                                path = overlayCache.burstHeartPath,
                                color = TapHeartColor.copy(alpha = burstAlpha)
                            )
                            drawContext.canvas.restore()

                            val spX = w * 0.26f - t * w * 0.025f
                            val spY = h * 0.28f - rise * 0.85f
                            drawCircle(
                                color = Color(0xFFFBBF24).copy(alpha = burstAlpha),
                                radius = w * 0.016f * (1f - 0.3f * t),
                                center = Offset(spX, spY)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawEyebrows(
    mood: BuddyMood,
    isJoyfulTap: Boolean,
    leftCx: Float,
    rightCx: Float,
    eyeY: Float,
    eyeRadius: Float,
    cache: CharacterOverlayCache
) {
    val p = cache.path
    val bw = eyeRadius * 0.95f
    when {
        isJoyfulTap || mood == BuddyMood.EXCITED || mood == BuddyMood.CELEBRATING -> {
            val liftY = eyeY - eyeRadius * 1.55f
            p.rewind()
            p.moveTo(leftCx - bw, liftY + eyeRadius * 0.15f)
            p.quadraticTo(leftCx, liftY - eyeRadius * 0.35f, leftCx + bw, liftY + eyeRadius * 0.08f)
            drawPath(p, color = BrowInkColor.copy(alpha = 0.72f), style = cache.browStroke)

            p.rewind()
            p.moveTo(rightCx - bw, liftY + eyeRadius * 0.08f)
            p.quadraticTo(rightCx, liftY - eyeRadius * 0.35f, rightCx + bw, liftY + eyeRadius * 0.15f)
            drawPath(p, color = BrowInkColor.copy(alpha = 0.72f), style = cache.browStroke)
        }
        mood == BuddyMood.HAPPY -> {
            val liftY = eyeY - eyeRadius * 1.42f
            p.rewind()
            p.moveTo(leftCx - bw * 0.85f, liftY + eyeRadius * 0.1f)
            p.quadraticTo(leftCx, liftY - eyeRadius * 0.18f, leftCx + bw * 0.85f, liftY + eyeRadius * 0.08f)
            drawPath(p, color = BrowInkColor.copy(alpha = 0.52f), style = cache.browStroke)

            p.rewind()
            p.moveTo(rightCx - bw * 0.85f, liftY + eyeRadius * 0.08f)
            p.quadraticTo(rightCx, liftY - eyeRadius * 0.18f, rightCx + bw * 0.85f, liftY + eyeRadius * 0.1f)
            drawPath(p, color = BrowInkColor.copy(alpha = 0.52f), style = cache.browStroke)
        }
        mood == BuddyMood.THIRSTY -> {
            val by = eyeY - eyeRadius * 1.38f
            p.rewind()
            p.moveTo(leftCx - bw * 0.95f, by + eyeRadius * 0.25f)
            p.quadraticTo(leftCx, by - eyeRadius * 0.05f, leftCx + bw * 0.85f, by - eyeRadius * 0.28f)
            drawPath(p, color = BrowInkColor.copy(alpha = 0.78f), style = cache.browStroke)

            p.rewind()
            p.moveTo(rightCx - bw * 0.85f, by - eyeRadius * 0.28f)
            p.quadraticTo(rightCx, by - eyeRadius * 0.05f, rightCx + bw * 0.95f, by + eyeRadius * 0.25f)
            drawPath(p, color = BrowInkColor.copy(alpha = 0.78f), style = cache.browStroke)
        }
        mood == BuddyMood.SAD -> {
            // Steeply drooped puppy-sad eyebrows (/ \)
            val by = eyeY - eyeRadius * 1.32f
            p.rewind()
            p.moveTo(leftCx - bw * 1.0f, by + eyeRadius * 0.42f)
            p.quadraticTo(leftCx, by + eyeRadius * 0.05f, leftCx + bw * 0.88f, by - eyeRadius * 0.42f)
            drawPath(p, color = BrowInkColor.copy(alpha = 0.85f), style = cache.browStroke)

            p.rewind()
            p.moveTo(rightCx - bw * 0.88f, by - eyeRadius * 0.42f)
            p.quadraticTo(rightCx, by + eyeRadius * 0.05f, rightCx + bw * 1.0f, by + eyeRadius * 0.42f)
            drawPath(p, color = BrowInkColor.copy(alpha = 0.85f), style = cache.browStroke)
        }
        else -> Unit
    }
}

private fun DrawScope.drawOpenGlossyEye(
    cx: Float,
    cy: Float,
    radius: Float,
    glanceX: Float,
    blink: Float,
    sparkleBoost: Boolean,
    droopy: Boolean,
    waterySad: Boolean,
    shimmerPhase: Float,
    cache: CharacterOverlayCache? = null
) {
    val openness = (1f - blink).coerceIn(0f, 1f)
    if (openness < 0.22f && cache != null) {
        val p = cache.path
        p.rewind()
        p.moveTo(cx - radius * 0.90f, cy - radius * 0.05f)
        p.quadraticTo(cx, cy + radius * 0.52f, cx + radius * 0.90f, cy - radius * 0.05f)
        drawPath(path = p, color = EyeInkColor, style = cache.featureStroke)
        return
    }

    val verticalScale = when {
        waterySad -> openness * 0.82f
        droopy -> openness * 0.88f
        else -> openness
    }
    val hRadius = radius
    val vRadius = (radius * verticalScale).coerceAtLeast(2f)
    val eyeCy = if (droopy) cy + radius * 0.08f else cy

    // Dark glossy base
    drawOval(
        color = EyeInkColor,
        topLeft = Offset(cx - hRadius, eyeCy - vRadius),
        size = Size(hRadius * 2f, vRadius * 2f)
    )

    // Bottom iris sheen or shimmering tear pool when sad
    if (waterySad) {
        val tearPulse = 0.72f + 0.24f * sin(shimmerPhase * 2f * PI.toFloat())
        drawOval(
            color = EyeTearPoolColor.copy(alpha = tearPulse),
            topLeft = Offset(cx - hRadius * 0.78f, eyeCy + vRadius * 0.10f),
            size = Size(hRadius * 1.56f, vRadius * 0.82f)
        )
    } else {
        drawOval(
            color = EyeIrisTint.copy(alpha = 0.55f),
            topLeft = Offset(cx - hRadius * 0.65f, eyeCy + vRadius * 0.08f),
            size = Size(hRadius * 1.30f, vRadius * 0.78f)
        )
    }

    // Primary upper-left catchlight
    val shimmer = if (sparkleBoost) 1f + 0.16f * sin(shimmerPhase * 2f * PI.toFloat()) else 1f
    val mainHiR = radius * (if (sparkleBoost) 0.34f else if (waterySad) 0.30f else 0.28f) * shimmer
    val mainHiCenter = Offset(
        x = cx - radius * 0.30f + glanceX,
        y = eyeCy - vRadius * 0.28f
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.96f),
        radius = mainHiR.coerceAtMost(vRadius * 0.45f),
        center = mainHiCenter
    )

    // Secondary sparkle dot
    if (openness > 0.5f) {
        drawCircle(
            color = Color.White.copy(alpha = if (waterySad) 0.92f else 0.84f),
            radius = radius * (if (sparkleBoost || waterySad) 0.16f else 0.13f),
            center = Offset(
                x = cx + radius * 0.30f + glanceX * 0.6f,
                y = eyeCy + vRadius * 0.32f
            )
        )
    }
}

private fun DrawScope.drawHappyArchEye(
    cx: Float,
    cy: Float,
    radius: Float,
    cache: CharacterOverlayCache
) {
    val p = cache.path
    p.rewind()
    p.moveTo(cx - radius * 0.95f, cy + radius * 0.22f)
    p.quadraticTo(
        cx,
        cy - radius * 0.75f,
        cx + radius * 0.95f,
        cy + radius * 0.22f
    )
    drawPath(path = p, color = EyeInkColor, style = cache.featureStroke)
}

private fun DrawScope.drawWinkEye(
    cx: Float,
    cy: Float,
    radius: Float,
    cache: CharacterOverlayCache
) {
    val p = cache.path
    p.rewind()
    p.moveTo(cx - radius * 0.90f, cy - radius * 0.38f)
    p.lineTo(cx + radius * 0.75f, cy + radius * 0.02f)
    p.lineTo(cx - radius * 0.90f, cy + radius * 0.40f)
    drawPath(path = p, color = EyeInkColor, style = cache.featureStroke)
}

private fun DrawScope.drawHappySmileMouth(
    cx: Float,
    cy: Float,
    halfWidth: Float,
    depth: Float,
    cache: CharacterOverlayCache
) {
    val p = cache.path
    p.rewind()
    p.moveTo(cx - halfWidth, cy - depth * 0.15f)
    p.quadraticTo(
        cx,
        cy + depth,
        cx + halfWidth,
        cy - depth * 0.15f
    )
    drawPath(path = p, color = EyeInkColor, style = cache.featureStroke)
}

private fun DrawScope.drawOpenLaughMouth(
    cx: Float,
    cy: Float,
    mouthWidth: Float,
    mouthHeight: Float,
    cache: CharacterOverlayCache
) {
    val halfW = mouthWidth / 2f
    val topY = cy - mouthHeight * 0.24f
    val p = cache.clipMouthPath
    p.rewind()
    p.moveTo(cx - halfW, topY)
    p.quadraticTo(cx, topY + mouthHeight * 0.16f, cx + halfW, topY)
    p.cubicTo(
        cx + halfW,
        topY + mouthHeight * 1.15f,
        cx - halfW,
        topY + mouthHeight * 1.15f,
        cx - halfW,
        topY
    )
    p.close()

    drawPath(path = p, color = MouthInteriorColor)
    clipPath(p) {
        drawOval(
            color = TongueColor,
            topLeft = Offset(cx - halfW * 0.64f, topY + mouthHeight * 0.44f),
            size = Size(mouthWidth * 0.64f, mouthHeight * 0.68f)
        )
    }
    drawPath(
        path = p,
        color = EyeInkColor,
        style = Stroke(width = (mouthWidth * 0.095f).coerceAtLeast(2.0f), join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawThirstyWavyMouth(
    cx: Float,
    cy: Float,
    halfWidth: Float,
    amp: Float,
    cache: CharacterOverlayCache
) {
    val p = cache.path
    p.rewind()
    p.moveTo(cx - halfWidth, cy + amp * 0.35f)
    p.cubicTo(
        cx - halfWidth * 0.45f,
        cy - amp * 1.25f,
        cx - halfWidth * 0.1f,
        cy + amp * 1.15f,
        cx,
        cy + amp * 0.1f
    )
    p.cubicTo(
        cx + halfWidth * 0.1f,
        cy - amp * 0.95f,
        cx + halfWidth * 0.45f,
        cy + amp * 1.25f,
        cx + halfWidth,
        cy + amp * 0.35f
    )
    drawPath(path = p, color = EyeInkColor, style = cache.featureStroke)
}

private fun DrawScope.drawSadFrownMouth(
    cx: Float,
    cy: Float,
    halfWidth: Float,
    height: Float,
    cache: CharacterOverlayCache
) {
    val p = cache.path
    p.rewind()
    p.moveTo(cx - halfWidth, cy + height * 0.45f)
    p.quadraticTo(
        cx,
        cy - height * 0.95f,
        cx + halfWidth,
        cy + height * 0.45f
    )
    drawPath(path = p, color = EyeInkColor, style = cache.featureStroke)
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
