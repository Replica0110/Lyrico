package com.lonx.lyrico.ui.components.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Menu semantics are shared; each engine renders its own action row. */
@Composable
internal fun ItemMenuAction(
    title: String,
    icon: ImageVector,
    tone: PreferenceIconTone = PreferenceIconTone.Tools,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        val color = if (tone == PreferenceIconTone.Danger) SaltTheme.colors.error else SaltTheme.colors.text
        Item(text = title, sub = summary, iconPainter = rememberVectorPainter(icon),
            textColor = color, iconColor = color, enabled = enabled, onClick = onClick)
    } else {
        ArrowPreference(title = title, summary = summary, enabled = enabled,
            startAction = { PreferenceIcon(icon, tone, enabled = enabled) },
            titleColor = BasicComponentColors(
                if (tone == PreferenceIconTone.Danger) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface,
                MiuixTheme.colorScheme.disabledOnSecondaryVariant),
            onClick = onClick)
    }
}
