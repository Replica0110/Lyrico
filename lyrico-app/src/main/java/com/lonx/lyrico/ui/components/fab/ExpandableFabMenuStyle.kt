package com.lonx.lyrico.ui.components.fab

import com.lonx.lyrico.ui.components.icons.LucideIcons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.library.LocalLibraryUiEngine
import com.moriafly.salt.ui.SaltTheme

data class ExpandableFabMenuStyle(
    val mainIcon: ImageVector = LucideIcons.Plus,
    val mainFabSize: Dp = 56.dp,
    val mainIconSize: Dp = 24.dp,
    val mainContainerColor: Color,
    val mainContentColor: Color,

    val expandedWidth: Dp = 220.dp,
    val minExpandedHeight: Dp = 56.dp,
    val maxExpandedHeight: Dp = 320.dp,
    val expandedContainerColor: Color,
    val cornerRadius: Dp = 16.dp,
    val contentPadding: PaddingValues = PaddingValues(vertical = 6.dp),
    val scrimColor: Color = Color.Black.copy(alpha = 0.2f),
    val itemHeight: Dp = 48.dp,
    val itemIconSize: Dp = 20.dp,
    val itemContentColor: Color,
    val itemDisabledContentColor: Color
) {
    companion object {
        @Composable
        fun default(): ExpandableFabMenuStyle {
            if (LocalLibraryUiEngine.current == UiEngine.SALT) {
                return ExpandableFabMenuStyle(
                    mainContainerColor = SaltTheme.colors.highlight,
                    mainContentColor = SaltTheme.colors.onHighlight,
                    expandedContainerColor = SaltTheme.colors.popup,
                    itemContentColor = SaltTheme.colors.text,
                    itemDisabledContentColor = SaltTheme.colors.subText.copy(alpha = 0.5f)
                )
            }
            return ExpandableFabMenuStyle(
                mainContainerColor = MiuixTheme.colorScheme.primary,
                mainContentColor = MiuixTheme.colorScheme.onPrimary,
                expandedContainerColor = MiuixTheme.colorScheme.surfaceVariant,
                itemContentColor = MiuixTheme.colorScheme.onSurface,
                itemDisabledContentColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}
