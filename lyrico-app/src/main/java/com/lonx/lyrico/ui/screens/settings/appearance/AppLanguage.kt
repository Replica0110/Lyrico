package com.lonx.lyrico.ui.screens.settings.appearance

import androidx.annotation.StringRes
import com.lonx.lyrico.R

enum class AppLanguage(val languageTag: String, @param:StringRes val labelRes: Int) {
    SYSTEM("", R.string.language_system),
    ENGLISH("en", R.string.language_english),
    SIMPLIFIED_CHINESE("zh-CN", R.string.language_simplified_chinese),
    TRADITIONAL_CHINESE_TAIWAN("zh-TW", R.string.language_traditional_chinese_taiwan),
    TRADITIONAL_CHINESE_HONG_KONG("zh-HK", R.string.language_traditional_chinese_hong_kong),
}
