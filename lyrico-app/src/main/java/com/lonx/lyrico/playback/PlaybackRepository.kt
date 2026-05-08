package com.lonx.lyrico.playback

import android.net.Uri
import com.lonx.lyrico.data.model.entity.SongEntity
import kotlinx.coroutines.flow.StateFlow

interface PlaybackRepository {

    val state: StateFlow<PlaybackState>

    suspend fun play(song: SongEntity)

    suspend fun playQueue(
        songs: List<SongEntity>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L
    )

    suspend fun playUri(uri: Uri)

    fun togglePlayPause()

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun skipToNext()

    fun skipToPrevious()

    fun playQueueItem(index: Int)

    fun stop()

    fun setRepeatMode(mode: PlaybackRepeatMode)

    fun setShuffleModeEnabled(enabled: Boolean)

    fun clearError()

    fun release()
}