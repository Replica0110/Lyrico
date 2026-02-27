package com.lonx.lyrico.data.repository

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

class PlaybackRepositoryImpl : PlaybackRepository {
    override fun play(context: Context, uri: Uri) {

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "选择播放器")
        try {
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "未找到可用播放器", Toast.LENGTH_SHORT).show()
        }
    }
}
