package com.lonx.lyrico.ui.components.blur

import android.os.Build
import androidx.compose.animation.core.EaseInOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.data.settings.FloatingBarEffect
import com.lonx.lyrico.data.settings.SettingsRepository
import com.lonx.lyrico.ui.components.scaffold.scaffoldTopAppBarInsetsPadding
import com.moriafly.salt.ui.verticalEdge
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.theme.MiuixTheme

// A host can share its preference subscription with all child pages.
internal val LocalBarBlurEnabled = staticCompositionLocalOf<Boolean?> { null }

internal class BarBlurBackdrop {
    val hazeState = HazeState()
    var bounds by mutableStateOf<Rect?>(null)
    var atTop by mutableStateOf(true)
}

internal val isBarBlurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

internal val isLiquidGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

internal fun resolveFloatingBarEffect(
    enabled: Boolean,
    preference: FloatingBarEffect,
): FloatingBarEffect = when {
    !enabled || !isBarBlurSupported -> FloatingBarEffect.NONE
    preference == FloatingBarEffect.LIQUID_GLASS && isLiquidGlassSupported -> FloatingBarEffect.LIQUID_GLASS
    else -> FloatingBarEffect.FROSTED_GLASS
}

@Composable
internal fun rememberBarBlurBackdrop(
    enabled: Boolean = rememberBarBlurEnabled(),
): BarBlurBackdrop? {
    if (!enabled || !isBarBlurSupported) return null
    return remember { BarBlurBackdrop() }
}

/** Apply to the scrolling content, before content insets, never to the bar itself. */
internal fun Modifier.blurSource(backdrop: BarBlurBackdrop?): Modifier =
    if (backdrop == null) this else blurSourceClip(backdrop).hazeSource(backdrop.hazeState)

private fun Modifier.blurSourceClip(backdrop: BarBlurBackdrop): Modifier =
    composed {
        var sourceTop by remember { mutableStateOf(0f) }
        onGloballyPositioned { sourceTop = it.positionInRoot().y }
            .drawWithContent {
                val bar = backdrop.bounds
                if (bar == null) {
                    drawContent()
                } else {
                    val top = if (backdrop.atTop) (bar.bottom - sourceTop).coerceIn(0f, size.height) else 0f
                    val bottom = if (backdrop.atTop) size.height else (bar.top - sourceTop).coerceIn(0f, size.height)
                    // Clip the visible list while Haze still records the full source.
                    clipRect(top = top, bottom = bottom) { this@drawWithContent.drawContent() }
                }
            }
    }

/** Owns the surface and safe-area padding. Child app bars must be transparent and omit insets. */
@Composable
internal fun BlurredTopBar(
    backdrop: BarBlurBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BlurredBar(
        backdrop = backdrop,
        modifier = modifier,
        atTop = true,
        surfaceColor = MiuixTheme.colorScheme.surface,
        content = { Box(Modifier.scaffoldTopAppBarInsetsPadding()) { content() } },
    )
}

/** 顶栏/底栏模糊开关，各页面统一从这里读。 */
@Composable
internal fun rememberBarBlurEnabled(): Boolean {
    LocalBarBlurEnabled.current?.let { return it }
    val settings: SettingsRepository = koinInject()
    val enabled by settings.barBlurEnabled.collectAsStateWithLifecycle(initialValue = false)
    return enabled
}

@Composable
internal fun BlurredBar(
    backdrop: BarBlurBackdrop?,
    modifier: Modifier,
    atTop: Boolean,
    surfaceColor: Color = MiuixTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    if (backdrop == null) {
        Box(Modifier.background(surfaceColor).then(modifier)) { content() }
        return
    }

    val density = LocalDensity.current
    var heightPx by remember { mutableStateOf(0) }
    val height = with(density) { heightPx.toDp() }
    val progressiveSupported = isLiquidGlassSupported
    val style = remember(atTop) {
        HazeBlurStyle {
            noiseFactor(0f)
            if (progressiveSupported) {
                progressive(
                    HazeProgressive.verticalGradient(
                        easing = EaseInOut,
                        startIntensity = if (atTop) 1f else 0f,
                        endIntensity = if (atTop) 0f else 1f,
                    )
                )
            } else {
                blurRadius(8.dp)
                val colors = listOf(Color.Transparent, Color.Black)
                mask(Brush.verticalGradient(if (atTop) colors else colors.reversed()))
            }
        }
    }
    val inset = if (progressiveSupported) (height - 80.dp).coerceAtLeast(0.dp) else 0.dp
    val fadeHeight = height - inset
    Box(
        modifier
            .background(surfaceColor)
            .onSizeChanged { heightPx = it.height }
            .onGloballyPositioned {
                backdrop.atTop = atTop
                backdrop.bounds = it.boundsInRoot()
            }
    ) {
        if (fadeHeight > 0.dp) {
            Box(
                Modifier.matchParentSize()
                    .padding(top = if (atTop) inset else 0.dp, bottom = if (atTop) 0.dp else inset)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .verticalEdge(
                        top = if (atTop) fadeHeight else 0.dp,
                        bottom = if (atTop) 0.dp else fadeHeight,
                    )
                    .hazeBlur(input = HazeInput.Sources(backdrop.hazeState), style = style)
            )
        }
        Box { content() }
    }
}
