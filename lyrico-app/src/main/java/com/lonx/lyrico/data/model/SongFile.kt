package com.lonx.lyrico.data.model

import android.net.Uri

data class SongFile(
    val mediaId: Long,
    val uri: Uri,
    val filePath: String,
    val fileName: String,
    val lastModified: Long,
    val dateAdded: Long
)
