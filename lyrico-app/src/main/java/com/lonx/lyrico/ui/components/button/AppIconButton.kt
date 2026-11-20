package com.lonx.lyrico.ui.components.button

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine

@Composable
internal fun AppIconButton(icon: ImageVector, description: String?, onClick: () -> Unit) {
    when (LocalUiEngine.current) {
        UiEngine.SALT -> AppIconButtonSalt(icon, description, onClick)
        UiEngine.MIUIX -> AppIconButtonMiuix(icon, description, onClick)
    }
}
