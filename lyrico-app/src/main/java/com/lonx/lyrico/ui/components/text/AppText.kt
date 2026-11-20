package com.lonx.lyrico.ui.components.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine

internal enum class AppTextTone {
    Title,
    Body,
    Secondary,
}

@Composable
internal fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    tone: AppTextTone = AppTextTone.Body,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    when (LocalUiEngine.current) {
        UiEngine.SALT -> AppTextSalt(text, modifier, tone, maxLines, overflow)
        UiEngine.MIUIX -> AppTextMiuix(text, modifier, tone, maxLines, overflow)
    }
}
