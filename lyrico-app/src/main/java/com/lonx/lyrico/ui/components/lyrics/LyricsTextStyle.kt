package com.lonx.lyrico.ui.components.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.SaltTheme

/**
 * 歌词正文的 Salt 阅读样式。
 *
 * Salt 主文是 16sp，长歌词下单屏行数偏少、不利于扫读；这里统一比主文小一档。
 * 字号集中在此处维护，各页歌词预览不再各写各的。
 */
@Composable
internal fun saltLyricsReadingStyle(): TextStyle =
    SaltTheme.textStyles.main.copy(fontSize = 14.sp)
