package com.lonx.lyrico.data.model.appearance

enum class ThemeColor(val argb: Int?) {
    DEFAULT(null),
    BLUE(0xFF3482FF.toInt()),
    GREEN(0xFF36D167.toInt()),
    YELLOW(0xFFFFB21D.toInt()),
    ORANGE(0xFFFF5722.toInt()),
    PURPLE(0xFF7C4DFF.toInt()),
    PINK(0xFFE91E63.toInt()),
    TEAL(0xFF00BCD4.toInt());

    companion object {
        fun fromArgb(argb: Int?): ThemeColor = entries.find { it.argb == argb } ?: DEFAULT
    }
}
