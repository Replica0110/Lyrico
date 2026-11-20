@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.button

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton

@Composable
internal fun AppIconButtonSalt(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
) {
    SaltIconButton(icon, description.orEmpty(), onClick)
}
