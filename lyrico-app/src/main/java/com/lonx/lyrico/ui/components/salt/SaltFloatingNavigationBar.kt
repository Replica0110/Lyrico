@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.salt

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.lonx.lyrico.ui.components.salt.glass.GlassEffect
import com.lonx.lyrico.ui.components.salt.glass.GlassSource
import com.lonx.lyrico.ui.components.salt.glass.glass

/**
 * Geometry and glass treatment follow SaltAudioAnalyzer's bottom bar.
 * [source] must sample sibling content behind the bar, never the bar itself.
 * Insets and outside padding are included in this component's measured height.
 */
@Composable
internal fun SaltFloatingNavigationBar(
    source: GlassSource,
    effect: GlassEffect,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .glass(source = source, shape = shape, effect = effect)
                .then(
                    if (effect is GlassEffect.Solid) {
                        Modifier.border(1.dp, SaltTheme.colors.subText.copy(alpha = 0.2f), shape)
                    } else Modifier
                )
                .heightIn(min = 56.dp)
                .selectableGroup()
                .pointerInput(Unit) {
                    detectTapGestures { /* Keep taps in the pill's gaps off the list below. */ }
                },
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
internal fun RowScope.SaltFloatingNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val tint = if (selected) SaltTheme.colors.highlight else SaltTheme.colors.text.copy(alpha = 0.5f)
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .graphicsLayer { alpha = if (pressed) 0.6f else 1f }
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(rememberVectorPainter(icon), null, modifier = Modifier.size(24.dp), tint = tint)
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            style = SaltTheme.textStyles.sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
