package com.lonx.lyrico.ui.screens.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.RoundedColumn
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun EditorMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        RoundedColumn(content = content)
    } else {
        Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
            Column(content = content)
        }
    }
}
