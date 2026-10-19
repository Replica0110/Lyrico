package com.lonx.lyrico.ui.components.base

import androidx.compose.runtime.Composable
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.salt.SaltDropdownRow
import com.lonx.lyrico.ui.theme.LocalUiEngine
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

/** Concurrency has the same five choices; each engine supplies its native dropdown. */
@Composable
internal fun ConcurrencyDropdown(
    title: String,
    value: Int,
    onChange: (Int) -> Unit,
    summary: String? = null,
) {
    val labels = (1..5).map { it.toString() }
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltDropdownRow(
            title = title,
            sub = summary,
            labels = labels,
            selectedIndex = value - 1,
            icon = LucideIcons.Parallel,
            onSelectedIndexChange = { onChange(it + 1) },
        )
    } else {
        WindowDropdownPreference(
            title = title,
            summary = summary,
            items = labels,
            selectedIndex = value - 1,
            startAction = { PreferenceIcon(LucideIcons.Parallel, PreferenceIconTone.Tools) },
            onSelectedIndexChange = { onChange(it + 1) },
        )
    }
}
