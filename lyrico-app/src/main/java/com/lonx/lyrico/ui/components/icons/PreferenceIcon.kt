package com.lonx.lyrico.ui.components.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class PreferenceIconTone {
    Appearance, Library, Lyrics, Tools, Teal, Orange, Pink, Danger;

    fun forItem(index: Int): PreferenceIconTone = if (this == Danger) Danger else at(index)

    companion object {
        private val itemTones = listOf(Appearance, Tools, Library, Lyrics, Pink, Teal, Orange)

        fun at(index: Int): PreferenceIconTone = itemTones[index.mod(itemTones.size)]
    }

    @Composable
    fun color(): Color {
        // 暗色判断与语义色都跟随当前引擎，避免 Salt 下取到 Miuix 色板。
        val salt = LocalUiEngine.current == UiEngine.SALT
        val dark = if (salt) SaltTheme.colors.background.luminance() < 0.5f
        else MiuixTheme.colorScheme.surface.luminance() < 0.5f
        return when (this) {
            // 固定色板在两种引擎中保持一致，相邻条目按各自指定的色调显示。
            Appearance -> if (dark) Color(0xFFC4A7FF) else Color(0xFF7952B3)
            Library -> if (dark) Color(0xFFF3BF75) else Color(0xFF99600C)
            Lyrics -> if (dark) Color(0xFF7DD6AB) else Color(0xFF23784F)
            Tools -> if (dark) Color(0xFF91BEFF) else Color(0xFF3264B4)
            Teal -> if (dark) Color(0xFF75D5CE) else Color(0xFF16776F)
            Orange -> if (dark) Color(0xFFFFB78A) else Color(0xFFAC571E)
            Pink -> if (dark) Color(0xFFF3A6CF) else Color(0xFFA64278)
            Danger -> if (salt) SaltTheme.colors.error else MiuixTheme.colorScheme.error
        }
    }
}

/** Decorative leading icon; the preference title supplies its accessible name. */
@Composable
internal fun PreferenceIcon(
    imageVector: ImageVector,
    tone: PreferenceIconTone = PreferenceIconTone.Tools,
    modifier: Modifier = Modifier.padding(end = 6.dp),
    enabled: Boolean = true,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = modifier.size(24.dp),
        tint = tone.color().let { if (enabled) it else it.copy(alpha = 0.38f) },
    )
}
