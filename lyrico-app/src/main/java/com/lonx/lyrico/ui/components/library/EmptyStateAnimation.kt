package com.lonx.lyrico.ui.components.library

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.theme.LyricoColors
import com.moriafly.salt.ui.SaltTheme

enum class EmptyStateKind(internal val asset: String) {
    List("list"), Songs("songs"), Albums("albums"), Artists("artists"),
    Folders("folders"), Search("search"), Cover("cover"), Lyrics("lyrics"),
    Plugins("plugins"), Tasks("tasks"), Logs("logs"),
}

/** Local vector animations share named color slots with the app theme. */
@Composable
internal fun EmptyStateAnimation(kind: EmptyStateKind, compact: Boolean) = key(kind) {
    val compositionResult = rememberLottieComposition(
        LottieCompositionSpec.Asset("empty_states/${kind.asset}.json"),
    )
    val composition by compositionResult
    // 动画色槽随引擎取色，保持与静态插画一致的语义；暖色/冷色沿用应用约定色。
    val salt = LocalUiEngine.current == UiEngine.SALT
    val accent = if (salt) SaltTheme.colors.highlight else MiuixTheme.colorScheme.primary
    val surface = if (salt) SaltTheme.colors.subBackground else MiuixTheme.colorScheme.surfaceContainer
    val warm = LyricoColors.emptyIllustrationWarm
    val fresh = LyricoColors.emptyIllustrationFresh
    val properties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.COLOR, accent.toArgb(), "**", "Accent"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, accent.toArgb(), "**", "Soft"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, surface.toArgb(), "**", "Surface"),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, accent.toArgb(), "**", "Outline"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, warm.toArgb(), "**", "Warm"),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, warm.toArgb(), "**", "WarmOutline"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, fresh.toArgb(), "**", "Fresh"),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, fresh.toArgb(), "**", "FreshOutline"),
    )
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val inspection = LocalInspectionMode.current
    var entered by remember { mutableStateOf(false) }
    val idleFrame = composition?.getMarker("idle")?.startFrame?.toInt() ?: 45
    val animation = animateLottieCompositionAsState(
        composition,
        isPlaying = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && !inspection,
        restartOnPlay = false,
        clipSpec = if (entered) LottieClipSpec.Frame(min = idleFrame) else LottieClipSpec.Frame(max = idleFrame),
        iterations = if (entered) LottieConstants.IterateForever else 1,
    )
    LaunchedEffect(composition, animation.isAtEnd) {
        if (composition != null && animation.composition === composition && animation.isAtEnd) entered = true
    }
    val scale = if (compact) 0.45f else 1f
    val illustrationModifier = Modifier.size((188 * scale).dp, (152 * scale).dp)
    if (composition == null) {
        if (compositionResult.isFailure) EmptyStateIllustration(compact) else Spacer(illustrationModifier)
    } else {
        LottieAnimation(
            composition = composition,
            modifier = illustrationModifier,
            progress = { if (inspection) 1f else animation.progress },
            dynamicProperties = properties,
        )
    }
}
