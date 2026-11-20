package com.lonx.lyrico.data.model.appearance

import com.lonx.lyrico.data.repository.SettingsDefaults
import com.lonx.lyrico.ui.theme.KeyColor
import com.lonx.lyrico.ui.theme.KeyColors

/**
 * 主题相关配置，用于主题切换和外观设置的消费者
 */
data class ThemeConfig(
    val themeMode: ThemeMode = SettingsDefaults.THEME_MODE,
    val monetEnable: Boolean = SettingsDefaults.MONET_ENABLE,
    val keyColor: KeyColor = KeyColors.first()
)
