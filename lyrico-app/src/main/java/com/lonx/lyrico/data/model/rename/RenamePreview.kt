package com.lonx.lyrico.data.model.rename

data class RenamePreview(
    val originalPath: String,
    val newPath: String,
    val conflict: Boolean = false
)
