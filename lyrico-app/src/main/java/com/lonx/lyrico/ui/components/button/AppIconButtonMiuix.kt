package com.lonx.lyrico.ui.components.button

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
internal fun AppIconButtonMiuix(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
) {
    top.yukonga.miuix.kmp.basic.IconButton(onClick = onClick) {
        top.yukonga.miuix.kmp.basic.Icon(icon, description)
    }
}
