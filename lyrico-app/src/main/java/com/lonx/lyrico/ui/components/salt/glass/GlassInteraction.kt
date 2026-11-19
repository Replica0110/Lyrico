/*
 * SaltUI
 * Copyright (C) 2026 Moriafly
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Adapted from SaltUI feature/haze-glass, commit da471d23d35b39a71d64006caa47a4c1b615834d.
// Keep the upstream implementation local until these APIs are released in SaltUI.
package com.lonx.lyrico.ui.components.salt.glass

import androidx.compose.animation.core.Spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.moriafly.salt.ui.UnstableSaltUiApi
import kotlinx.coroutines.flow.filter
import kotlin.jvm.JvmSynthetic
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * The visual transform of one glass surface, for content drawn outside that surface.
 * [translation] and [pivot] are in pixels; [pivot] is relative to the glass surface.
 * Use each state with only one glass surface.
 */
@UnstableSaltUiApi
@Stable
class GlassInteractionState internal constructor() {
    var translation by mutableStateOf(Offset.Zero)
        private set

    var scale by mutableFloatStateOf(1f)
        private set

    var pivot by mutableStateOf(Offset.Zero)
        private set

    internal var isAnimating by mutableStateOf(false)
        private set

    @get:JvmSynthetic
    internal var animationRequest by mutableLongStateOf(0L)
        private set

    // Pointer coordinates are local; displacement and target are normalized by the surface height.
    private var isPressed by mutableStateOf(false)
    private var origin = Offset.Zero
    private var size = IntSize.Zero

    // Pressure drives scale, while displacement drives translation. Both retain spring velocity.
    private var pressure by mutableFloatStateOf(0f)
    private var pressureVelocity = 0f
    private var displacement = Offset.Zero
    private var velocity = Offset.Zero
    private var target = Offset.Zero

    @get:JvmSynthetic
    internal var hoverPosition by mutableStateOf<Offset?>(null)
        private set

    @get:JvmSynthetic
    internal val lightStrength: Float
        // Reduced motion keeps the surface still while retaining pressed feedback.
        get() = if (isPressed && !isAnimating && scale == 1f) {
            1f
        } else {
            pressure.coerceIn(0f, 1f)
        }

    internal fun updateHover(position: Offset, size: IntSize) {
        hoverPosition = position.takeIf {
            it.isFinite && it.x in 0f..size.width.toFloat() && it.y in 0f..size.height.toFloat()
        }
    }

    internal fun clearHover() {
        hoverPosition = null
    }

    internal fun press(position: Offset, size: IntSize): Boolean {
        if (!position.isFinite || size.width <= 0 || size.height <= 0) {
            return false
        }
        isPressed = true
        origin = position
        this.size = size
        updatePivot(position)
        target = Offset.Zero
        requestAnimationIfNeeded()
        return true
    }

    internal fun move(position: Offset) {
        if (!isPressed || !position.isFinite) {
            return
        }
        updatePivot(position)
        // Bound the target so a pointer far outside the surface cannot drag it indefinitely.
        val delta = (position - origin) / size.height.toFloat()
        target = Offset(tanh(delta.x * 0.65f), tanh(delta.y * 0.65f))
        requestAnimationIfNeeded()
    }

    internal fun release(cancelled: Boolean = false) {
        if (!isPressed) {
            return
        }
        isPressed = false
        // A quick tap can release before the press spring moves; give it visible momentum.
        if (!cancelled && pressure < 0.15f) {
            pressureVelocity += 7f
        }
        target = Offset.Zero
        requestAnimationIfNeeded()
    }

    private fun requestAnimationIfNeeded() {
        val needsAnimation = !isSettled()
        if (needsAnimation && !isAnimating) {
            animationRequest++
        }
        isAnimating = needsAnimation
    }

    internal fun advance(seconds: Float) {
        if (!seconds.isFinite() || seconds <= 0f) {
            return
        }
        advancePressure(seconds)
        advanceDisplacement(seconds)
        if (isSettled()) {
            pressure = if (isPressed) 1f else 0f
            pressureVelocity = 0f
            displacement = target
            velocity = Offset.Zero
            isAnimating = false
        }
        // Keep movement proportional to the control and restrict its scale to a positive value.
        val height = size.height.toFloat()
        translation = Offset(displacement.x * height * 0.13f, displacement.y * height * 0.09f)
        scale = (1f - pressure * 0.02f).coerceAtLeast(0.01f)
    }

    private fun advancePressure(seconds: Float) {
        // Integrate position and velocity together so retargeting preserves momentum.
        val pressureFrequency = sqrt(
            if (isPressed) Spring.StiffnessMedium else Spring.StiffnessMediumLow
        )
        val pressureDamping = if (isPressed) 0.82f else 0.72f
        val pressureTarget = if (isPressed) 1f else 0f
        val pressureDecayRate = pressureFrequency * pressureDamping
        val pressureAngularRate = pressureFrequency * sqrt(1f - pressureDamping * pressureDamping)
        val pressureDistance = pressure - pressureTarget
        val pressureSineCoefficient =
            (pressureVelocity + pressureDistance * pressureDecayRate) / pressureAngularRate
        val pressureAngle = pressureAngularRate * seconds
        val pressureDecay = exp(-pressureDecayRate * seconds)
        val pressureSine = sin(pressureAngle)
        val pressureCosine = cos(pressureAngle)
        val pressureWave =
            pressureDistance * pressureCosine + pressureSineCoefficient * pressureSine
        pressureVelocity = pressureDecay * (
            -pressureDecayRate * pressureWave -
                pressureDistance * pressureAngularRate * pressureSine +
                pressureSineCoefficient * pressureAngularRate * pressureCosine
        )
        pressure = pressureTarget + pressureDecay * pressureWave
    }

    private fun advanceDisplacement(seconds: Float) {
        // Translation follows the pointer more tightly while pressed and springs back on release.
        val frequency = if (isPressed) 38f else 26f
        val damping = if (isPressed) 0.72f else 0.55f
        val decayRate = frequency * damping
        val angularRate = frequency * sqrt(1f - damping * damping)
        val distance = displacement - target
        val sineCoefficient = (velocity + distance * decayRate) / angularRate
        val angle = angularRate * seconds
        val decay = exp(-decayRate * seconds)
        val sine = sin(angle)
        val cosine = cos(angle)
        val wave = distance * cosine + sineCoefficient * sine
        velocity = (
            wave * -decayRate - distance * (angularRate * sine) +
                sineCoefficient * (angularRate * cosine)
        ) * decay
        displacement = target + wave * decay
    }

    internal fun stopMotion() {
        pressure = 0f
        pressureVelocity = 0f
        displacement = Offset.Zero
        velocity = Offset.Zero
        target = Offset.Zero
        translation = Offset.Zero
        scale = 1f
        isAnimating = false
    }

    internal fun transformOrigin(size: Size): TransformOrigin {
        if (scale == 1f || size.width <= 0f || size.height <= 0f) {
            return TransformOrigin.Center
        }
        return TransformOrigin(
            (pivot.x / size.width).coerceIn(0f, 1f),
            (pivot.y / size.height).coerceIn(0f, 1f)
        )
    }

    private fun updatePivot(position: Offset) {
        val localPosition = position - translation
        pivot = Offset(
            x = localPosition.x.coerceIn(0f, size.width.toFloat()),
            y = localPosition.y.coerceIn(0f, size.height.toFloat())
        )
    }

    // Stop frame updates once both position and velocity are visually settled.
    private fun isSettled(): Boolean {
        val pressureTarget = if (isPressed) 1f else 0f
        return abs(pressure - pressureTarget) < 0.0001f && abs(pressureVelocity) < 0.003f &&
            abs(displacement.x - target.x) < 0.0001f && abs(displacement.y - target.y) < 0.0001f &&
            abs(velocity.x) < 0.003f && abs(velocity.y) < 0.003f
    }
}

/**
 * Remembers the interaction state for one glass surface.
 */
@UnstableSaltUiApi
@Composable
fun rememberGlassInteractionState(): GlassInteractionState {
    val interaction = remember { GlassInteractionState() }
    LaunchedEffect(interaction) {
        val durationScale = coroutineContext[MotionDurationScale]
        // A distinct request survives conflation when an animation settles and restarts quickly.
        snapshotFlow { interaction.animationRequest }
            .filter { it != 0L }
            .collect {
                if (!interaction.isAnimating) {
                    return@collect
                }
                var previousTime = withFrameNanos { time ->
                    val scale = durationScale?.scaleFactor ?: 1f
                    if (!scale.isFinite() || scale <= 0f) {
                        interaction.stopMotion()
                    } else {
                        interaction.advance(1f / 120f / scale)
                    }
                    time
                }
                while (interaction.isAnimating) {
                    withFrameNanos { time ->
                        val scale = durationScale?.scaleFactor ?: 1f
                        if (!scale.isFinite() || scale <= 0f) {
                            interaction.stopMotion()
                        } else {
                            interaction.advance((time - previousTime) / 1_000_000_000f / scale)
                        }
                        previousTime = time
                    }
                }
            }
    }
    return interaction
}

/**
 * Observe the original hit region without consuming the child's pointer events.
 * Cancel the interaction once another gesture, such as scrolling, claims the movement.
 * Keep the glass interaction active until the tracked pointer is released or cancelled.
 */
@OptIn(UnstableSaltUiApi::class)
internal fun Modifier.glassPointerInput(interaction: GlassInteractionState): Modifier =
    pointerInput(interaction) {
        var trackedPointer: PointerId? = null
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    when (event.type) {
                        PointerEventType.Enter, PointerEventType.Move -> {
                            event.changes
                                .lastOrNull {
                                    it.type == PointerType.Mouse || it.type == PointerType.Stylus
                                }
                                ?.let { interaction.updateHover(it.position, size) }
                        }
                        PointerEventType.Exit -> interaction.clearHover()
                    }
                    val tracked = trackedPointer
                    if (tracked == null) {
                        val down = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                        if (down != null && interaction.press(down.position, size)) {
                            trackedPointer = down.id
                        }
                        continue
                    }

                    val change = event.changes.firstOrNull { it.id == tracked }
                    if (change == null || !change.pressed) {
                        if (change != null) {
                            interaction.move(change.position)
                        }
                        interaction.release(cancelled = change == null)
                        trackedPointer = null
                        continue
                    }

                    // Another gesture can only claim this movement after the main pass, so the
                    // final pass decides whether the surface still follows the pointer.
                    val claimed = awaitPointerEvent(PointerEventPass.Final)
                        .changes
                        .firstOrNull { it.id == tracked }
                        ?.isConsumed == true
                    if (claimed) {
                        interaction.release(cancelled = true)
                        trackedPointer = null
                    } else {
                        interaction.move(change.position)
                    }
                }
            }
        } finally {
            interaction.release(cancelled = true)
            interaction.clearHover()
        }
    }

@OptIn(UnstableSaltUiApi::class)
internal fun Modifier.glassInteractionLight(
    interaction: GlassInteractionState,
    shape: RoundedCornerShape,
    isDarkTheme: Boolean
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    // Cover long controls without spreading a single light across an entire wide bar.
    val radius = maxOf(
        size.minDimension * 1.4f,
        minOf(size.maxDimension * 0.9f, size.minDimension * 5f)
    )
    val lightAlpha = if (isDarkTheme) 0.5f else 0.7f
    onDrawWithContent {
        val pressStrength = interaction.lightStrength
        val hoverPosition = interaction.hoverPosition
        val position = if (pressStrength > 0f) interaction.pivot else hoverPosition
        if (position != null && size.minDimension > 0f) {
            val strength = maxOf(pressStrength, if (hoverPosition != null) 0.55f else 0f)
            drawOutline(
                outline = outline,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = lightAlpha * strength),
                        Color.Transparent
                    ),
                    center = position,
                    radius = radius
                )
            )
        }
        drawContent()
    }
}

private val Offset.isFinite: Boolean
    get() = x.isFinite() && y.isFinite()
