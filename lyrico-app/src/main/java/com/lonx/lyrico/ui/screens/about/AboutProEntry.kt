package com.lonx.lyrico.ui.screens.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.Item as SaltItem
import top.yukonga.miuix.kmp.preference.ArrowPreference

/** 「关于」中的 Pro 入口，两种主题各自的偏好行样式。 */
@Composable
internal fun AboutProEntry(onClick: () -> Unit) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltItem(
            text = "Lyrico Pro",
            sub = stringResource(R.string.pro_about_summary),
            iconPainter = rememberVectorPainter(LucideIcons.Crown),
            iconColor = PreferenceIconTone.Gold.color(),
            onClick = onClick,
        )
    } else {
        ArrowPreference(
            title = "Lyrico Pro",
            summary = stringResource(R.string.pro_about_summary),
            startAction = { PreferenceIcon(LucideIcons.Crown, PreferenceIconTone.Gold) },
            onClick = onClick,
        )
    }
}
