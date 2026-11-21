package com.lonx.lyrico.ui.screens.library

/** 歌曲、专辑、艺术家列表共用的字母分区顺序。 */
internal val SECTIONS_ASC = listOf("0") + ('A'..'Z').map { it.toString() } + listOf("#")

internal val SECTIONS_DESC = SECTIONS_ASC.asReversed()
