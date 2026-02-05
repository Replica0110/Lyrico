package com.lonx.lyrico.data.model

enum class ThemeMode(val displayName: String) {
    AUTO("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式");

    companion object {
        fun fromDisplayName(name: String): ThemeMode {
            return entries.firstOrNull { it.displayName == name } ?: AUTO
        }
    }
}
