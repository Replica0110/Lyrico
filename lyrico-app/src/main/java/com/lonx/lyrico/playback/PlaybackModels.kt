package com.lonx.lyrico.playback

import android.net.Uri
import com.lonx.lyrico.data.model.entity.SongEntity

enum class PlaybackRepeatMode {
    OFF,
    ONE,
    ALL
}

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    READY,
    ENDED
}

data class PlaybackError(
    val message: String,
    val cause: Throwable? = null
)

data class PlaybackState(
    val currentSong: SongEntity? = null,
    val fallbackUri: Uri? = null,

    val queue: List<SongEntity> = emptyList(),
    val currentIndex: Int = -1,

    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,

    val status: PlaybackStatus = PlaybackStatus.IDLE,

    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,

    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.OFF,
    val shuffleModeEnabled: Boolean = false,

    val error: PlaybackError? = null
) {
    val hasMedia: Boolean
        get() = currentSong != null || fallbackUri != null || queue.isNotEmpty()

    val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}