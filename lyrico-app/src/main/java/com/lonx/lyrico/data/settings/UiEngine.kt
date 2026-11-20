package com.lonx.lyrico.data.settings

enum class UiEngine(val value: String) {
    MIUIX("miuix"),
    SALT("salt");

    companion object {
        fun fromValue(value: String?): UiEngine = entries.firstOrNull { it.value == value } ?: MIUIX
    }
}
