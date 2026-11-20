package com.lonx.lyrico.ui.components.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppTextMiuix(
    text: String,
    modifier: Modifier = Modifier,
    tone: AppTextTone = AppTextTone.Body,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    top.yukonga.miuix.kmp.basic.Text(
        text,
        modifier = modifier,
        style =
            when (tone) {
                AppTextTone.Title -> MiuixTheme.textStyles.title3
                AppTextTone.Body -> MiuixTheme.textStyles.body1
                AppTextTone.Secondary -> MiuixTheme.textStyles.body2
            },
        color =
            if (tone == AppTextTone.Secondary)
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            else MiuixTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = overflow,
    )
}
