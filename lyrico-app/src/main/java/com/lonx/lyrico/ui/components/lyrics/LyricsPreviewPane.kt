package com.lonx.lyrico.ui.components.lyrics

import com.lonx.lyrico.ui.components.icons.LucideIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LyricsPreviewPane(
    lyricsText: String?,
    isLoading: Boolean,
    error: UiMessage?,
    loadingText: String,
    failedText: String,
    emptyText: String,
    onCopyLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    showCopyAction: Boolean = true,
    /**
     * 正文自带的内边距。放进 RoundedColumn / groupContent 卡片时由调用方指定与相邻 Item
     * 对齐的值，避免和外壳的内边距叠加。
     */
    contentPadding: PaddingValues = PaddingValues(12.dp),
) {
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
            com.lonx.lyrico.data.model.UiEngine.SALT
    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> item("loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // SaltUI 没有加载指示器，回退控件使用 Salt 颜色。
                            if (salt) androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = com.moriafly.salt.ui.SaltTheme.colors.highlight,
                                trackColor = com.moriafly.salt.ui.SaltTheme.colors.stroke,
                            ) else CircularProgressIndicator(size = 32.dp)
                            if (salt) com.moriafly.salt.ui.Text(
                                text = loadingText,
                                style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                                color = com.moriafly.salt.ui.SaltTheme.colors.subText
                            ) else Text(
                                text = loadingText,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                            )
                        }
                    }
                }

                error != null -> item("error") {
                    if (salt) com.moriafly.salt.ui.Text(
                        modifier = Modifier.padding(contentPadding),
                        text = error.asString() ?: failedText,
                        style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                        color = com.moriafly.salt.ui.SaltTheme.colors.text,
                    ) else Text(
                        modifier = Modifier.padding(contentPadding),
                        text = error.asString() ?: failedText,
                        style = MiuixTheme.textStyles.body2
                    )
                }

                lyricsText.isNullOrBlank() -> item("empty") {
                    IllustratedEmptyState(
                        kind = EmptyStateKind.Lyrics,
                        title = emptyText,
                        modifier = Modifier.fillParentMaxSize(),
                    )
                }

                else -> item("lyrics") {
                    if (salt) com.moriafly.salt.ui.Text(
                        modifier = Modifier.padding(contentPadding),
                        text = lyricsText,
                        style = saltLyricsReadingStyle(),
                        color = com.moriafly.salt.ui.SaltTheme.colors.text,
                        overflow = TextOverflow.Clip
                    ) else Text(
                        modifier = Modifier.padding(contentPadding),
                        text = lyricsText,
                        style = MiuixTheme.textStyles.body2,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }

        if (showCopyAction && !lyricsText.isNullOrBlank()) {
            if (salt) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            color = com.moriafly.salt.ui.SaltTheme.colors.subBackground.copy(alpha = 0.7f),
                            shape = CircleShape
                        )
                ) {
                    com.lonx.lyrico.ui.components.salt.SaltIconButton(
                        icon = LucideIcons.Copy,
                        label = "",
                        onClick = onCopyLyrics,
                        size = 36.dp,
                        iconSize = 20.dp
                    )
                }
            } else IconButton(
                onClick = onCopyLyrics,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(
                        color = MiuixTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = LucideIcons.Copy,
                    contentDescription = null
                )
            }
        }
    }
}
