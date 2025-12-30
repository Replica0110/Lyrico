package com.lonx.lyrico.utils

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MusicContentObserver(
    private val scope: CoroutineScope,
    handler: Handler,
    private val onChange: () -> Unit
) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        // We launch a coroutine to avoid blocking the UI thread
        // and to easily call suspend functions if needed.
        scope.launch {
            onChange()
        }
    }
}
