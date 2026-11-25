package com.lonx.lyrico.domain.rename

data class RenamePreview(
    val originalPath: String,
    val newPath: String,
    val conflict: Boolean = false
)
