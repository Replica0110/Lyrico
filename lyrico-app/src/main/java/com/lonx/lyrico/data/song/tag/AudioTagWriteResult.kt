package com.lonx.lyrico.data.song.tag

import android.content.IntentSender
import com.lonx.audiotag.model.AudioTagData

sealed interface AudioTagWriteResult {
    data class Success(
        val savedData: AudioTagData
    ) : AudioTagWriteResult

    data class PermissionRequired(
        val intentSender: IntentSender
    ) : AudioTagWriteResult

    data class Failed(
        val error: Throwable
    ) : AudioTagWriteResult
}
