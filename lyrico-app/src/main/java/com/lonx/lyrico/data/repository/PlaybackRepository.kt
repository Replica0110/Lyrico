package com.lonx.lyrico.data.repository

import android.content.Context
import android.net.Uri

interface PlaybackRepository {

    fun play(context: Context, uri: Uri)
}
