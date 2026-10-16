package com.lonx.lyrico.data.model

enum class UiEngine(val value: String) {
    MIUIX("miuix"),
    SALT("salt");

    companion object {
        fun fromValue(value: String?): UiEngine = entries.firstOrNull { it.value == value } ?: MIUIX
    }
}
