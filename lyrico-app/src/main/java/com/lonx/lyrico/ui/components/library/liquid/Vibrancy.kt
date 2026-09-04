// Adapted from SukiSU Ultra and Kyant0/AndroidLiquidGlass (Apache 2.0).
package com.lonx.lyrico.ui.components.library.liquid

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

internal fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}
