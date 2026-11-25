@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.moriafly.salt.ui.SaltTheme

@Composable
internal fun AppTextSalt(
    text: String,
    modifier: Modifier = Modifier,
    tone: AppTextTone = AppTextTone.Body,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {

    com.moriafly.salt.ui.Text(
        text,
        modifier = modifier,
        style =
            when (tone) {
                AppTextTone.Title -> SaltTheme.textStyles.largeTitle
                AppTextTone.Body -> SaltTheme.textStyles.main
                AppTextTone.Secondary -> SaltTheme.textStyles.sub
            },
        color =
            if (tone == AppTextTone.Secondary) SaltTheme.colors.subText
            else SaltTheme.colors.text,
        fontWeight = if (tone == AppTextTone.Title) FontWeight.Bold else null,
        maxLines = maxLines,
        overflow = overflow,
    )
}
