package com.lonx.lyrico.ui.components.fab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FabMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        // 文字标签带阴影背景，避免与列表内容混淆
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MiuixTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.main,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 小尺寸的 FAB
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MiuixTheme.colorScheme.surface,
            contentColor = MiuixTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}