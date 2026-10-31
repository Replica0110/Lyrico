package com.lonx.lyrico.platform.player

import android.content.Intent
import android.net.Uri

object PlayerIntentFactory {
    fun buildPlayIntent(
        uri: Uri,
        mimeType: String = "audio/*"
    ): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildChooserIntent(
        uri: Uri,
        title: String,
        mimeType: String = "audio/*"
    ): Intent {
        return Intent.createChooser(buildPlayIntent(uri, mimeType), title)
    }
}
