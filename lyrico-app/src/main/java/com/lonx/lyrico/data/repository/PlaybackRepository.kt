package com.lonx.lyrico.data.repository

import android.content.Context
import android.net.Uri

interface PlaybackRepository {

    fun play(context: Context, uri: Uri)

    fun openWithPackage(context: Context, uri: Uri, packageName: String): Boolean

    fun openSystemChooser(context: Context, uri: Uri): Boolean

    fun openDefaultApp(context: Context, uri: Uri): Boolean
}
