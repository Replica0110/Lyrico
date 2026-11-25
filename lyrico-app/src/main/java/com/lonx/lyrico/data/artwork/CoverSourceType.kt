package com.lonx.lyrico.data.artwork

import android.graphics.Bitmap
import android.net.Uri

enum class CoverSourceType {
    BYTE_ARRAY,
    BITMAP,
    NETWORK_URL,
    CONTENT_OR_FILE_URI,
    FILE_PATH,
    URI,
    UNSUPPORTED
}

fun getCoverSourceType(source: Any?): CoverSourceType =
    when (source) {
        is ByteArray -> CoverSourceType.BYTE_ARRAY
        is Bitmap -> CoverSourceType.BITMAP
        is Uri -> source.toString().toCoverSourceType(uriFallback = CoverSourceType.URI)
        is String -> source.trim().toCoverSourceType(uriFallback = CoverSourceType.FILE_PATH)
        else -> CoverSourceType.UNSUPPORTED
    }

private fun String.toCoverSourceType(uriFallback: CoverSourceType): CoverSourceType =
    when {
        startsWith("http://", ignoreCase = true) ||
            startsWith("https://", ignoreCase = true) -> CoverSourceType.NETWORK_URL

        startsWith("content://", ignoreCase = true) ||
            startsWith("file://", ignoreCase = true) -> CoverSourceType.CONTENT_OR_FILE_URI

        uriFallback == CoverSourceType.URI -> CoverSourceType.URI
        else -> CoverSourceType.FILE_PATH
    }
