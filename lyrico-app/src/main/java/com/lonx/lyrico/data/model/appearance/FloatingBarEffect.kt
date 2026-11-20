package com.lonx.lyrico.data.model.appearance

import androidx.annotation.StringRes
import com.lonx.lyrico.R

/** 悬浮导航栏的视觉效果。毛玻璃与液态玻璃互斥，因此用三选一设置而不是两个开关。 */
enum class FloatingBarEffect(
    @field:StringRes val labelRes: Int
) {
    NONE(R.string.floating_bar_effect_none),
    FROSTED_GLASS(R.string.floating_bar_effect_frosted_glass),
    LIQUID_GLASS(R.string.floating_bar_effect_liquid_glass);

    companion object {
        /** 宽松解析持久化的枚举名，遇到未知值回退到 [NONE]。 */
        fun fromName(name: String?): FloatingBarEffect =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}
