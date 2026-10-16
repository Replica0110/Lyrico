@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.base

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.ControlSize
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 歌词偏移单次调整步长（毫秒）。 */
const val LYRICS_OFFSET_STEP_MS: Long = 100L

/** 歌词偏移允许的下限（毫秒）。 */
const val LYRICS_OFFSET_MIN_MS: Long = -10_000L

/** 歌词偏移允许的上限（毫秒）。 */
const val LYRICS_OFFSET_MAX_MS: Long = 10_000L

/** 步进器高度。 */
private val STEPPER_HEIGHT = 40.dp

/** 步进器左右按钮宽度。 */
private val STEPPER_BUTTON_WIDTH = 74.dp

/** 中间数值胶囊宽度：固定宽度，避免位数变化导致整个控件抖动。 */
private val STEPPER_VALUE_WIDTH = 86.dp

/**
 * 禁用态底色的不透明度（相对主题主色）。
 *
 * Miuix 自带的禁用色不可用：浅色主题 `OnSecondaryContainer`(#A9A9A9) 配 `SecondaryContainer`(#F0F0F0)
 * 只有 2.06:1，启用态就已经发灰；主题的 `DisabledPrimary`(#C2D9FF) 与 `DisabledOnPrimary`(#F3F8FF)
 * 更是只有 1.34:1。因此禁用态只把主色压到 [DISABLED_FILL_ALPHA] 不透明度，文字保持实心 `onPrimary`。
 */
private const val DISABLED_FILL_ALPHA = 0.75f

/**
 * 歌词偏移调整控件。
 *
 * 采用「步进器 + 数值锚点」布局，而不是多个等重按钮平铺：
 * - 第一行是一个整体分段步进器：`−100ms` | 大号偏移值 | `+100ms`，两侧按钮触碰区域独立、视觉同属一个控件；
 * - 第二行是两个弱化的文字按钮：重置、手动输入，不参与主视觉竞争。
 *
 * 偏移值本身也可点击，直接打开手动输入弹窗。
 *
 * @param offset 当前偏移值（毫秒），正数表示歌词整体延后。
 * @param onOffsetChange 偏移值变化回调，传入的是调整后的绝对值。
 * @param modifier 外层修饰符。
 * @param minOffset 允许的最小偏移值。
 * @param maxOffset 允许的最大偏移值。
 * @param step 单次增减步长（毫秒）。
 * @param enabled 是否允许交互。
 * @param animated 数值变化时是否播放滚动动画。
 */
@Composable
fun LyricsOffsetField(
    offset: Long,
    onOffsetChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    minOffset: Long = LYRICS_OFFSET_MIN_MS,
    maxOffset: Long = LYRICS_OFFSET_MAX_MS,
    step: Long = LYRICS_OFFSET_STEP_MS,
    enabled: Boolean = true,
    animated: Boolean = true,
) {
    var showInputDialog by remember { mutableStateOf(false) }
    val safeOffset = offset.coerceIn(minOffset, maxOffset)
    val haptics = LocalHapticFeedback.current
    val canDecrease = enabled && safeOffset > minOffset
    val canIncrease = enabled && safeOffset < maxOffset

    fun stepBy(delta: Long) {
        val next = (safeOffset + delta).coerceIn(minOffset, maxOffset)
        if (next == safeOffset) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onOffsetChange(next)
    }

    if (LocalUiEngine.current == UiEngine.SALT) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.moriafly.salt.ui.Button(
                    text = "−${step}ms", onClick = { stepBy(-step) },
                    enabled = canDecrease, modifier = Modifier.weight(1f),
                    size = ControlSize.Small, appearance = ButtonAppearance.Subtle,
                )
                com.moriafly.salt.ui.Button(
                    onClick = { showInputDialog = true }, enabled = enabled,
                    modifier = Modifier.weight(1.2f), size = ControlSize.Small,
                    appearance = ButtonAppearance.Plain,
                ) {
                    AnimatedContent(
                        targetState = safeOffset,
                        contentAlignment = Alignment.Center,
                        transitionSpec = {
                            val forward = targetState > initialState
                            if (animated) ((slideInVertically { if (forward) it else -it } + fadeIn()) togetherWith
                                (slideOutVertically { if (forward) -it else it } + fadeOut())).using(null)
                            else (fadeIn() togetherWith fadeOut()).using(null)
                        }, label = "saltLyricsOffsetValue",
                    ) { value ->
                        com.moriafly.salt.ui.Text(formatLyricsOffset(value),
                            style = SaltTheme.textStyles.main, maxLines = 1)
                    }
                }
                com.moriafly.salt.ui.Button(
                    text = "+${step}ms", onClick = { stepBy(step) },
                    enabled = canIncrease, modifier = Modifier.weight(1f),
                    size = ControlSize.Small, appearance = ButtonAppearance.Subtle,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.moriafly.salt.ui.Button(
                    text = stringResource(R.string.action_reset), onClick = { onOffsetChange(0L) },
                    enabled = enabled && safeOffset != 0L, modifier = Modifier.weight(1f),
                    size = ControlSize.Small, appearance = ButtonAppearance.Plain,
                )
                com.moriafly.salt.ui.Button(
                    text = stringResource(R.string.action_manual_input), onClick = { showInputDialog = true },
                    enabled = enabled, modifier = Modifier.weight(1f),
                    size = ControlSize.Small, appearance = ButtonAppearance.Plain,
                )
            }
        }
    } else Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 分段步进器：一个容器里放两侧按钮与中间数值，避免三个控件各自为政。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(STEPPER_HEIGHT)
                .clip(RoundedCornerShape(STEPPER_HEIGHT / 2))
                .background(MiuixTheme.colorScheme.secondaryContainer)
                .semantics { contentDescription = formatLyricsOffset(safeOffset) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepperSideButton(
                label = "-${step}ms",
                enabled = canDecrease,
                onClick = { stepBy(-step) }
            )

            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(enabled = enabled) { showInputDialog = true },
                contentAlignment = Alignment.Center
            ) {
                // 数值是控件的信息锚点：比正文重一档、但不压过页面标题，胶囊底暗示可点击手动输入。
                // clip 必须打开：数值位数变化时靠裁剪保证只在胶囊内左右滚动，不会溢出。
                Box(
                    modifier = Modifier
                        .width(STEPPER_VALUE_WIDTH)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MiuixTheme.colorScheme.surface)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = safeOffset,
                        transitionSpec = {
                            if (!animated) {
                                fadeIn() togetherWith fadeOut()
                            } else {
                                // 上下滚动：增大时向上滚出、新值从下进；减小时反向。
                                // 胶囊是固定宽度，纯垂直位移不会再出现横向漂移；
                                // 再叠一个很短的 fade 收掉连点时的重影。
                                val forward = targetState > initialState
                                val slide = spring<IntOffset>(dampingRatio = 0.85f, stiffness = 900f)
                                val enter = slideInVertically(animationSpec = slide) { height ->
                                    if (forward) height else -height
                                } + fadeIn(animationSpec = tween(durationMillis = 90))
                                val exit = slideOutVertically(animationSpec = slide) { height ->
                                    if (forward) -height else height
                                } + fadeOut(animationSpec = tween(durationMillis = 90))
                                enter togetherWith exit
                            }
                        },
                        label = "lyricsOffsetValue"
                    ) { value ->
                        Text(
                            text = formatLyricsOffset(value),
                            style = MiuixTheme.textStyles.body1.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }

            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )

            StepperSideButton(
                label = "+${step}ms",
                enabled = canIncrease,
                onClick = { stepBy(step) }
            )
        }

        // 次要动作弱化为文字按钮，不再和步进器抢视觉。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                text = stringResource(R.string.action_reset),
                onClick = { onOffsetChange(0L) },
                modifier = Modifier.weight(1f),
                enabled = enabled && safeOffset != 0L
            )
            TextButton(
                text = stringResource(R.string.action_manual_input),
                onClick = { showInputDialog = true },
                modifier = Modifier.weight(1f),
                enabled = enabled
            )
        }
    }

    LyricsOffsetInputDialog(
        show = showInputDialog,
        initialValue = safeOffset,
        minOffset = minOffset,
        maxOffset = maxOffset,
        onDismiss = { showInputDialog = false },
        onConfirm = { value ->
            showInputDialog = false
            onOffsetChange(value)
        }
    )
}

/** 步进器两侧的增减按钮。 */
@Composable
private fun StepperSideButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(STEPPER_BUTTON_WIDTH)
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = if (enabled) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            },
            maxLines = 1,
        )
    }
}

/**
 * 手动输入歌词偏移值的对话框。
 *
 * 数字键盘多数不提供负号，因此输入框只接受绝对值，正负号由左侧的符号按钮切换。
 */
@Composable
fun LyricsOffsetInputDialog(
    show: Boolean,
    initialValue: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    modifier: Modifier = Modifier,
    minOffset: Long = LYRICS_OFFSET_MIN_MS,
    maxOffset: Long = LYRICS_OFFSET_MAX_MS,
) {
    // 每次打开都使用当前偏移值作为初始输入，关闭后输入态不残留。
    var negative by remember(show, initialValue) { mutableStateOf(initialValue < 0L) }
    var magnitudeInput by remember(show, initialValue) {
        mutableStateOf(kotlin.math.abs(initialValue).toString())
    }
    val magnitude = magnitudeInput.trim().toLongOrNull()
    val value = magnitude?.let { if (negative) -it else it }
    val isValid = value != null && value in minOffset..maxOffset
    val rangeHint = stringResource(R.string.lyrics_offset_input_hint, minOffset, maxOffset)

    if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == com.lonx.lyrico.data.model.UiEngine.SALT) {
        SaltConfirmDialog(
            show = show, title = stringResource(R.string.label_lyrics_offset),
            onDismissRequest = onDismiss, onConfirm = { value?.let(onConfirm) },
            cancelText = stringResource(R.string.cancel), confirmText = stringResource(R.string.confirm),
            confirmEnabled = isValid, dismissOnConfirm = false,
        ) {
            com.moriafly.salt.ui.RoundedColumn(paddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    com.moriafly.salt.ui.Button(
                        text = if (negative) "−" else "+", onClick = { negative = !negative },
                        appearance = com.moriafly.salt.ui.ButtonAppearance.Plain,
                    )
                    com.moriafly.salt.ui.ItemEdit(
                        text = magnitudeInput, onChange = { magnitudeInput = it },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            com.moriafly.salt.ui.Text(
                text = when {
                    magnitude == null -> stringResource(R.string.lyrics_offset_input_invalid)
                    !isValid -> stringResource(R.string.lyrics_offset_input_out_of_range, rangeHint)
                    else -> rangeHint
                },
                style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                color = if (isValid) com.moriafly.salt.ui.SaltTheme.colors.subText
                    else com.moriafly.salt.ui.SaltTheme.colors.error,
            )
        }
        return
    }

    WindowDialog(
        show = show,
        title = stringResource(R.string.label_lyrics_offset),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.secondaryContainer)
                        .clickable { negative = !negative },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (negative) "−" else "+",
                        style = MiuixTheme.textStyles.body1.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MiuixTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                TextField(
                    value = magnitudeInput,
                    onValueChange = { magnitudeInput = it },
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.label_lyrics_offset),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    magnitude == null -> stringResource(R.string.lyrics_offset_input_invalid)
                    !isValid -> stringResource(R.string.lyrics_offset_input_out_of_range, rangeHint)
                    else -> rangeHint
                },
                style = MiuixTheme.textStyles.footnote1,
                color = if (isValid) {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                } else {
                    MiuixTheme.colorScheme.error
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = { value?.let(onConfirm) },
                    modifier = Modifier.weight(1f),
                    enabled = isValid,
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

/** 把偏移值格式化为带符号的展示文本，例如 `+100ms`、`-100ms`、`0ms`。 */
fun formatLyricsOffset(offset: Long): String {
    return when {
        offset > 0L -> "+${offset}ms"
        else -> "${offset}ms"
    }
}
