package com.lonx.lyrico.ui.components.base

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.enabledAlpha

/** Shared app button size and ripple; the surrounding top bar is owned by SaltUI. */
@Composable
internal fun SaltIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = SaltTheme.colors.text,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp,
) {
    Box(Modifier.size(size).enabledAlpha(enabled).clip(CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(rememberVectorPainter(icon), label, modifier = Modifier.size(iconSize), tint = tint)
    }
}
