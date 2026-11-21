package com.lonx.lyrico.ui.components.library

internal data class LibraryMenuAction(
    val title: String,
    val selected: Boolean = false,
    /** 次要说明（如排序的升序/降序），由菜单以原生副文本显示，不拼进标题。 */
    val summary: String? = null,
    val onClick: () -> Unit,
)
