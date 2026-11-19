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

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.UnplacedAwareModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import com.moriafly.salt.core.os.OS
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.thenIf
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import kotlin.jvm.JvmSynthetic

/**
 * Appearance used by [glass].
 */
@UnstableSaltUiApi
@Immutable
sealed interface GlassEffect {
    /**
     * Draws the color for the current theme, including its alpha, without processing the source.
     */
    data class Solid(
        val lightColor: Color = Color(0x99FFFFFF),
        val darkColor: Color = Color(0x99181818)
    ) : GlassEffect

    /**
     * Blurs the source with theme-aware premium material tinting, without glass optics.
     */
    data object Blur : GlassEffect

    /**
     * Draws glass with a background color selected for the current theme.
     */
    data class Regular(
        val lightColor: Color = Color(0x99FFFFFF),
        val darkColor: Color = Color(0x99181818)
    ) : GlassEffect

    /**
     * Draws glass without an added background color, tint, or color grading. Refraction, blur, and
     * highlights can still change the apparent color of the content.
     */
    data object Transparent : GlassEffect
}

/**
 * The default glass appearance for the current composition.
 */
@UnstableSaltUiApi
val LocalGlassEffect = staticCompositionLocalOf<GlassEffect> { GlassEffect.Regular() }

/**
 * Connects explicitly marked source content to glass surfaces.
 * Multiple composables can share the same source.
 */
@UnstableSaltUiApi
@Stable
class GlassSource internal constructor() {
    @get:JvmSynthetic
    internal val hazeState = HazeState()

    @get:JvmSynthetic
    internal val hazeInput = HazeInput.Sources(hazeState)
    private val surfaces = mutableStateListOf<GlassSurface>()

    private class GlassSurface(
        shape: RoundedCornerShape,
        density: Density,
        layoutDirection: LayoutDirection
    ) {
        var shape by mutableStateOf(shape)
        var density by mutableStateOf(density)
        var layoutDirection by mutableStateOf(layoutDirection)
        var bounds by mutableStateOf<SurfaceBounds?>(null)
    }

    private data class SurfaceBounds(
        val coordinates: LayoutCoordinates,
        val root: LayoutCoordinates,
        val size: IntSize,
        val origin: Offset,
        val topRight: Offset,
        val bottomLeft: Offset,
        val bottomRight: Offset
    ) {
        val widthInRoot = (topRight - origin).getDistance()
        val heightInRoot = (bottomLeft - origin).getDistance()

        // LayoutCoordinates can mutate without changing identity, so retain transformed corners
        // to detect position and transform changes.
        constructor(coordinates: LayoutCoordinates) : this(
            coordinates = coordinates,
            root = coordinates.findRootCoordinates(),
            size = coordinates.size,
            origin = coordinates.localToRoot(Offset.Zero),
            topRight = coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), 0f)),
            bottomLeft = coordinates.localToRoot(Offset(0f, coordinates.size.height.toFloat())),
            bottomRight = coordinates.localToRoot(
                Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
            )
        )
    }

    internal data class SourceElement(
        val source: GlassSource
    ) : ModifierNodeElement<SourceNode>() {
        override fun create() = SourceNode(source)

        override fun update(node: SourceNode) {
            node.source = source
        }

        override fun InspectorInfo.inspectableProperties() {
            name = "glassSource"
        }
    }

    internal class SourceNode(
        var source: GlassSource
    ) : Modifier.Node(),
        DrawModifierNode,
        LayoutAwareModifierNode,
        GlobalPositionAwareModifierNode,
        UnplacedAwareModifierNode {
        private var coordinates: LayoutCoordinates? = null
        private val transform = Matrix()
        private val shapePath = Path()
        private val visiblePath = Path()
        private val cutoutPath = Path()

        override fun onPlaced(coordinates: LayoutCoordinates) {
            this.coordinates = coordinates
            invalidateDraw()
        }

        override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
            this.coordinates = coordinates
            invalidateDraw()
        }

        override fun onUnplaced() {
            coordinates = null
        }

        override fun onDetach() {
            coordinates = null
        }

        override fun ContentDrawScope.draw() {
            val sourceCoordinates = coordinates
            if (sourceCoordinates == null || !sourceCoordinates.isAttached ||
                source.surfaces.isEmpty()
            ) {
                drawContent()
                return
            }

            val root = sourceCoordinates.findRootCoordinates()
            val canvas = drawContext.canvas
            canvas.save()
            try {
                source.surfaces.forEach { surface ->
                    val bounds = surface.bounds ?: return@forEach
                    if (bounds.root !== root || !bounds.coordinates.isAttached ||
                        bounds.size.width <= 0 || bounds.size.height <= 0
                    ) {
                        return@forEach
                    }

                    val visibleBounds = sourceCoordinates.localBoundingBoxOf(
                        sourceCoordinates = bounds.coordinates,
                        clipBounds = true
                    )
                    if (visibleBounds.isEmpty) {
                        return@forEach
                    }

                    val width = bounds.size.width.toFloat()
                    val height = bounds.size.height.toFloat()
                    val rootWidth = bounds.widthInRoot
                    val rootHeight = bounds.heightInRoot
                    if (!rootWidth.isFinite() || !rootHeight.isFinite() ||
                        rootWidth <= CutoutEdgeOverlapPx * 2f ||
                        rootHeight <= CutoutEdgeOverlapPx * 2f
                    ) {
                        return@forEach
                    }
                    val insetX = CutoutEdgeOverlapPx * width / rootWidth
                    val insetY = CutoutEdgeOverlapPx * height / rootHeight

                    transform.reset()
                    sourceCoordinates.transformFrom(bounds.coordinates, transform)
                    transform.translate(insetX, insetY)
                    transform.scale(
                        x = (width - insetX * 2f) / width,
                        y = (height - insetY * 2f) / height
                    )
                    shapePath.reset()
                    shapePath.addOutline(
                        surface.shape.createOutline(
                            size = bounds.size.toSize(),
                            layoutDirection = surface.layoutDirection,
                            density = surface.density
                        )
                    )
                    shapePath.transform(transform)
                    val shapeBounds = shapePath.getBounds()
                    if (shapeBounds.left >= visibleBounds.left &&
                        shapeBounds.top >= visibleBounds.top &&
                        shapeBounds.right <= visibleBounds.right &&
                        shapeBounds.bottom <= visibleBounds.bottom
                    ) {
                        canvas.clipPath(shapePath, ClipOp.Difference)
                    } else {
                        visiblePath.reset()
                        visiblePath.addRect(visibleBounds)
                        cutoutPath.reset()
                        cutoutPath.op(shapePath, visiblePath, PathOperation.Intersect)
                        canvas.clipPath(cutoutPath, ClipOp.Difference)
                    }
                }
                drawContent()
            } finally {
                canvas.restore()
            }
        }

        private companion object {
            // Keep source pixels under the antialiased edge, measured in root pixels.
            @Suppress("ConstPropertyName")
            private const val CutoutEdgeOverlapPx = 2.5f
        }
    }

    internal data class SurfaceElement(
        val source: GlassSource,
        val shape: RoundedCornerShape,
        val density: Density,
        val layoutDirection: LayoutDirection
    ) : ModifierNodeElement<SurfaceNode>() {
        override fun create() = SurfaceNode(source, shape, density, layoutDirection)

        override fun update(node: SurfaceNode) {
            node.update(source, shape, density, layoutDirection)
        }

        override fun InspectorInfo.inspectableProperties() {
            name = "glass"
        }
    }

    internal class SurfaceNode(
        private var source: GlassSource,
        shape: RoundedCornerShape,
        density: Density,
        layoutDirection: LayoutDirection
    ) : Modifier.Node(),
        LayoutAwareModifierNode,
        GlobalPositionAwareModifierNode,
        UnplacedAwareModifierNode {
        private val surface = GlassSurface(shape, density, layoutDirection)

        override fun onAttach() {
            source.surfaces.add(surface)
        }

        override fun onPlaced(coordinates: LayoutCoordinates) {
            surface.bounds = SurfaceBounds(coordinates)
        }

        override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
            surface.bounds = SurfaceBounds(coordinates)
        }

        override fun onUnplaced() {
            surface.bounds = null
        }

        override fun onDetach() {
            surface.bounds = null
            source.surfaces.remove(surface)
        }

        fun update(
            source: GlassSource,
            shape: RoundedCornerShape,
            density: Density,
            layoutDirection: LayoutDirection
        ) {
            if (this.source !== source) {
                if (isAttached) {
                    this.source.surfaces.remove(surface)
                    source.surfaces.add(surface)
                }
                this.source = source
            }
            surface.shape = shape
            surface.density = density
            surface.layoutDirection = layoutDirection
        }
    }
}

/**
 * Remembers a source that can be shared between source content and glass surfaces.
 */
@UnstableSaltUiApi
@Composable
fun rememberGlassSource(): GlassSource = remember { GlassSource() }

/**
 * Marks this content as input for glass surfaces using the same [source].
 *
 * One [source] can mark multiple composables, each captured separately. A [glass] surface outside
 * those source nodes selects all of them. When nested within a source, it selects only sources
 * with a lower source z-index; this API currently uses the same default z-index for every source.
 * Place the source content behind its glass surfaces so automatic clipping matches the intended
 * stacking order.
 */
@UnstableSaltUiApi
fun Modifier.glassSource(source: GlassSource): Modifier = this
    // Keep the clip outside hazeSource so Haze records the complete content.
    .then(GlassSource.SourceElement(source))
    // Isolate clip-only redraws from unchanged source content.
    .graphicsLayer()
    .hazeSource(source.hazeState)

/**
 * Draws glass using content explicitly marked with [glassSource] and the same [source].
 * Unmarked content behind translucent source pixels can remain visible, but is not blurred.
 * [effect] selects a solid color, blur, regular glass or glass without an added color. It defaults
 * to [GlassEffect.Regular]. Its colors and glass appearance follow the light or dark
 * [SaltTheme.configs]. [interactionState] can be shared with other content to follow the surface.
 * Add click handling separately.
 *
 * Source content in the same Compose hierarchy is clipped beneath glass surfaces while remaining
 * available as input to the effect. Solid surfaces do not capture or clip source content.
 * Sources should be drawn before their glass surfaces.
 * On Android below 13, transparent glass leaves the original source visible during fallback.
 * The cutout does not follow transparency or non-rectangular clipping applied by an ancestor.
 * The content and source cutout follow the pressed material as it scales and moves, while the
 * content's hit region remains at its original layout position. The press ends when another
 * gesture, such as scrolling, claims the pointer movement.
 */
@UnstableSaltUiApi
@Composable
fun Modifier.glass(
    source: GlassSource,
    shape: RoundedCornerShape = CircleShape,
    effect: GlassEffect = GlassEffect.Regular(),
    interactionState: GlassInteractionState = rememberGlassInteractionState()
): Modifier {
    val isDarkTheme = SaltTheme.configs.isDarkTheme
    val background = when (effect) {
        is GlassEffect.Solid -> if (isDarkTheme) effect.darkColor else effect.lightColor
        GlassEffect.Blur -> SaltTheme.colors.subBackground
        is GlassEffect.Regular -> if (isDarkTheme) effect.darkColor else effect.lightColor
        GlassEffect.Transparent -> Color.Transparent
    }
    return this
        .glassPointerInput(interactionState)
        // Move the material and its automatically tracked source cutout together.
        .graphicsLayer {
            scaleX = interactionState.scale
            scaleY = interactionState.scale
            transformOrigin = interactionState.transformOrigin(size)
            translationX = interactionState.translation.x
            translationY = interactionState.translation.y
        }
        // The glass cutout, when needed, observes the material's transform here.
        .then(
            when (effect) {
                is GlassEffect.Solid -> Modifier.background(background, shape)
                GlassEffect.Blur -> {
                    val android = OS.current as? OS.Android
                    if (android != null && android.versionSdk < OS.Android.ANDROID_12) {
                        Modifier.background(background, shape)
                    } else {
                        val style = remember(isDarkTheme) { GlassStyles.blur(isDarkTheme) }
                        Modifier
                            .then(
                                GlassSource.SurfaceElement(
                                    source = source,
                                    shape = shape,
                                    density = LocalDensity.current,
                                    layoutDirection = LocalLayoutDirection.current
                                )
                            )
                            .clip(shape)
                            .hazeBlur(input = source.hazeInput, style = style)
                    }
                }
                is GlassEffect.Regular, GlassEffect.Transparent -> {
                    val isTransparent = effect == GlassEffect.Transparent
                    val style = remember(shape, background, isDarkTheme, isTransparent) {
                        val style = if (isTransparent) {
                            GlassStyles.transparent(isDarkTheme)
                        } else {
                            GlassStyles.regular(isDarkTheme)
                        }
                        style.then {
                            shape(shape)
                            backgroundColor(background)
                        }
                    }
                    val android = OS.current as? OS.Android
                    // Haze 2.1's transparent fallback does not replay its source below Android 13.
                    val keepSourceVisible = background.alpha == 0f && android != null &&
                        android.versionSdk < OS.Android.ANDROID_13
                    Modifier
                        .thenIf(!keepSourceVisible) {
                            then(
                                GlassSource.SurfaceElement(
                                    source = source,
                                    shape = shape,
                                    density = LocalDensity.current,
                                    layoutDirection = LocalLayoutDirection.current
                                )
                            )
                        }
                        // Inherit Haze's performance mode; full resolution can cost more to render.
                        .hazeGlass(input = source.hazeInput, style = style)
                        .glassInteractionLight(
                            interaction = interactionState,
                            shape = shape,
                            isDarkTheme = isDarkTheme
                        )
                }
            }
        )
        // Undo the material transform for the content's hit testing.
        .graphicsLayer {
            val inverseScale = 1f / interactionState.scale
            scaleX = inverseScale
            scaleY = inverseScale
            transformOrigin = interactionState.transformOrigin(size)
            translationX = -interactionState.translation.x * inverseScale
            translationY = -interactionState.translation.y * inverseScale
        }
        // Reapply the transform to content drawing without changing its hit region.
        .drawWithContent {
            val glassScale = interactionState.scale
            val translation = interactionState.translation
            if (glassScale == 1f && translation == Offset.Zero) {
                drawContent()
            } else {
                val origin = interactionState.transformOrigin(size)
                val pivot = Offset(
                    x = size.width * origin.pivotFractionX,
                    y = size.height * origin.pivotFractionY
                )
                withTransform({
                    translate(translation.x, translation.y)
                    scale(scaleX = glassScale, scaleY = glassScale, pivot = pivot)
                }) {
                    this@drawWithContent.drawContent()
                }
            }
        }
}
