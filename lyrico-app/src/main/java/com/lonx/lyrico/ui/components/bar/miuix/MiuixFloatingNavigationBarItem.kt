package com.lonx.lyrico.ui.components.bar.miuix

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

/**
 * 悬浮栏子项。触控区与滑动指示块等宽，拖动经过时指示块才能落在对应项下。
 */
@Composable
internal fun RowScope.MiuixFloatingNavigationBarItem(
    index: Int,
    selected: Boolean,
    icon: @Composable () -> Unit,
    label: String,
) {
    val select = LocalMiuixBarSelection.current
    val lensPosition = LocalMiuixBarPosition.current
    val colors = MiuixTheme.colorScheme
    val base = if (selected) colors.onSurfaceContainer else colors.onSurfaceContainer.copy(alpha = 0.8f)
    // 指示块经过时向主题色过渡，对应液态玻璃放大下层文字的观感。
    val weight = (1f - abs(lensPosition() - index)).coerceIn(0f, 1f)
    val tint = lerp(base, colors.primary, weight)
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = TabMinWidth)
            .fillMaxHeight()
            .weight(1f)
            .padding(horizontal = 2.dp)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    select(index)
                },
            ),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint) {
            Box(modifier = Modifier.size(22.dp)) { icon() }
            Text(
                text = label,
                color = tint,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
