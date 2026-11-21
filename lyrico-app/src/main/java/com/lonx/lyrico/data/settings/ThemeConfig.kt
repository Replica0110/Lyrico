package com.lonx.lyrico.data.settings

/**
 * 主题相关配置，用于主题切换和外观设置的消费者
 */
data class ThemeConfig(
    val themeMode: ThemeMode = SettingsDefaults.THEME_MODE,
    val monetEnable: Boolean = SettingsDefaults.MONET_ENABLE,
    val keyColor: ThemeColor = ThemeColor.DEFAULT
)
