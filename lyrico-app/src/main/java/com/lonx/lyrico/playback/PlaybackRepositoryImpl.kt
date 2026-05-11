package com.lonx.lyrico.playback

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.service.PlaybackService
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class PlaybackRepositoryImpl(
    context: Context,
    private val appScope: CoroutineScope
) : PlaybackRepository {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private var positionTickerJob: Job? = null
    private var artworkUpdateJob: Job? = null
    private val lyriconSyncManager = LyriconSyncManager(appContext)

    private val playerListener = object : Player.Listener {

        override fun onEvents(player: Player, events: Player.Events) {
            publishPlayerSnapshot(player)
            updatePositionTicker(player.isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val c = ensureControllerOrNull() ?: return
            publishPlayerSnapshot(c)
            ensureCurrentItemArtwork(c)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publishPlayerSnapshot(ensureControllerOrNull() ?: return)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishPlayerSnapshot(ensureControllerOrNull() ?: return)
            updatePositionTicker(isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(
                    isPlaying = false,
                    error = PlaybackError(
                        message = error.localizedMessage ?: error.errorCodeName,
                        cause = error
                    )
                )
            }
            updatePositionTicker(false)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.update {
                it.copy(repeatMode = repeatMode.toPlaybackRepeatMode())
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.update {
                it.copy(shuffleModeEnabled = shuffleModeEnabled)
            }
        }
    }

    init {
        appScope.launch(Dispatchers.Main.immediate) {
            connectController()
        }
    }

    override suspend fun play(song: SongEntity) {
        playQueue(listOf(song), startIndex = 0, startPositionMs = 0L)
    }

    override suspend fun playQueue(
        songs: List<SongEntity>,
        startIndex: Int,
        startPositionMs: Long
    ) {
        if (songs.isEmpty()) return

        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        val mediaItems = songs.mapIndexed { index, song ->
            if (index == safeIndex) {
                song.toPlaybackMediaItemWithArtwork()
            } else {
                song.toPlaybackMediaItem()
            }
        }

        withController { c ->
            c.setMediaItems(mediaItems, safeIndex, startPositionMs.coerceAtLeast(0L))
            c.prepare()
            c.play()

            publishPlayerSnapshot(c)
            updatePositionTicker(true)
        }
    }

    override suspend fun playUri(uri: Uri) {
        withController { c ->
            c.setMediaItem(uri.toFallbackMediaItem())
            c.prepare()
            c.play()

            publishPlayerSnapshot(c)
            updatePositionTicker(true)
        }
    }

    override fun togglePlayPause() {
        val c = ensureControllerOrNull() ?: return

        if (c.isPlaying) {
            c.pause()
        } else {
            c.play()
        }

        publishPlayerSnapshot(c)
        updatePositionTicker(c.isPlaying)
    }

    override fun play() {
        val c = ensureControllerOrNull() ?: return
        c.play()
        publishPlayerSnapshot(c)
        updatePositionTicker(c.isPlaying)
    }

    override fun pause() {
        val c = ensureControllerOrNull() ?: return
        c.pause()
        publishPlayerSnapshot(c)
        updatePositionTicker(false)
    }

    override fun seekTo(positionMs: Long) {
        val c = ensureControllerOrNull() ?: return
        c.seekTo(positionMs.coerceAtLeast(0L))
        lyriconSyncManager.seekTo(positionMs)
        publishPlayerSnapshot(c)
    }

    override fun skipToNext() {
        val c = ensureControllerOrNull() ?: return
        if (c.hasNextMediaItem()) {
            c.seekToNextMediaItem()
        } else {
            c.seekToNext()
        }
        publishPlayerSnapshot(c)
    }

    override fun skipToPrevious() {
        val c = ensureControllerOrNull() ?: return

        if (c.currentPosition > RESTART_PREVIOUS_THRESHOLD_MS) {
            c.seekTo(0L)
        } else if (c.hasPreviousMediaItem()) {
            c.seekToPreviousMediaItem()
        } else {
            c.seekToPrevious()
        }

        publishPlayerSnapshot(c)
    }

    override fun playQueueItem(index: Int) {
        val c = ensureControllerOrNull() ?: return
        if (index !in 0 until c.mediaItemCount) return

        c.seekToDefaultPosition(index)
        c.play()

        publishPlayerSnapshot(c)
        updatePositionTicker(c.isPlaying)
    }

    override fun stop() {
        val c = ensureControllerOrNull() ?: return

        c.stop()
        c.clearMediaItems()

        positionTickerJob?.cancel()
        positionTickerJob = null

        _state.value = PlaybackState()
        lyriconSyncManager.clear()
    }

    override fun setRepeatMode(mode: PlaybackRepeatMode) {
        val c = ensureControllerOrNull() ?: return
        c.repeatMode = mode.toPlayerRepeatMode()
        _state.update { it.copy(repeatMode = mode) }
    }

    override fun setShuffleModeEnabled(enabled: Boolean) {
        val c = ensureControllerOrNull() ?: return
        c.shuffleModeEnabled = enabled
        _state.update { it.copy(shuffleModeEnabled = enabled) }
    }

    override fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun release() {
        positionTickerJob?.cancel()
        positionTickerJob = null
        artworkUpdateJob?.cancel()
        artworkUpdateJob = null

        controller?.removeListener(playerListener)

        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }

        controller = null
        controllerFuture = null
        lyriconSyncManager.release()
    }

    private suspend fun connectController() {
        if (controller != null) return

        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java)
        )

        val future = MediaController.Builder(appContext, sessionToken)
            .buildAsync()

        controllerFuture = future

        try {
            val c = withContext(Dispatchers.Main.immediate) {
                future.await()
            }

            controller = c
            c.addListener(playerListener)

            publishPlayerSnapshot(c)
            updatePositionTicker(c.isPlaying)
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    error = PlaybackError(
                        message = e.localizedMessage ?: "无法连接播放服务",
                        cause = e
                    )
                )
            }
        }
    }

    private suspend fun awaitController(): MediaController {
        controller?.let { return it }

        connectController()

        controller?.let { return it }

        val future = controllerFuture ?: error("MediaController future is null")
        return withContext(Dispatchers.Main.immediate) {
            future.await().also { c ->
                controller = c
                c.addListener(playerListener)
                publishPlayerSnapshot(c)
                updatePositionTicker(c.isPlaying)
            }
        }
    }

    private suspend fun withController(block: (MediaController) -> Unit) {
        withContext(Dispatchers.Main.immediate) {
            val c = awaitController()
            block(c)
        }
    }

    private fun ensureControllerOrNull(): MediaController? {
        return controller
    }

    private fun publishPlayerSnapshot(player: Player) {
        val mediaItems = player.allMediaItems()
        val queue = mediaItems.mapNotNull { it.toSongEntityOrNull() }

        val currentIndex = player.currentMediaItemIndex
            .takeIf { it >= 0 }
            ?: -1

        val currentMediaItem = player.currentMediaItem
        val currentSong = queue.getOrNull(currentIndex)
            ?: currentMediaItem?.toSongEntityOrNull()

        val fallbackUri = currentSong?.getUri
            ?: currentMediaItem?.localConfiguration?.uri

        _state.update {
            it.copy(
                currentSong = currentSong,
                fallbackUri = fallbackUri,
                queue = queue,
                currentIndex = currentIndex,
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                status = player.playbackState.toPlaybackStatus(),
                durationMs = player.duration.normalizedDuration(previous = it.durationMs),
                positionMs = player.currentPosition.coerceAtLeast(0L),
                bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                repeatMode = player.repeatMode.toPlaybackRepeatMode(),
                shuffleModeEnabled = player.shuffleModeEnabled,
                error = null
            )
        }
        lyriconSyncManager.syncState(_state.value)
    }

    private fun updatePositionTicker(isPlaying: Boolean) {
        if (isPlaying) {
            if (positionTickerJob?.isActive == true) return

            positionTickerJob = appScope.launch(Dispatchers.Main.immediate) {
                while (true) {
                    val c = controller
                    if (c == null || !c.isPlaying) {
                        break
                    }

                    _state.update {
                        it.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0L),
                            bufferedPositionMs = c.bufferedPosition.coerceAtLeast(0L),
                            durationMs = c.duration.normalizedDuration(previous = it.durationMs)
                        )
                    }
                    lyriconSyncManager.syncPosition(_state.value.positionMs)

                    delay(POSITION_UPDATE_INTERVAL_MS)
                }
            }
        } else {
            positionTickerJob?.cancel()
            positionTickerJob = null

            controller?.let { c ->
                _state.update {
                    it.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0L),
                        bufferedPositionMs = c.bufferedPosition.coerceAtLeast(0L),
                        durationMs = c.duration.normalizedDuration(previous = it.durationMs)
                    )
                }
                lyriconSyncManager.syncPosition(_state.value.positionMs)
            }
        }
    }

    private fun Player.allMediaItems(): List<MediaItem> {
        if (mediaItemCount <= 0) return emptyList()
        return buildList {
            for (i in 0 until mediaItemCount) {
                add(getMediaItemAt(i))
            }
        }
    }

    private fun Int.toPlaybackStatus(): PlaybackStatus {
        return when (this) {
            Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            Player.STATE_READY -> PlaybackStatus.READY
            Player.STATE_ENDED -> PlaybackStatus.ENDED
            else -> PlaybackStatus.IDLE
        }
    }

    private fun Int.toPlaybackRepeatMode(): PlaybackRepeatMode {
        return when (this) {
            Player.REPEAT_MODE_ONE -> PlaybackRepeatMode.ONE
            Player.REPEAT_MODE_ALL -> PlaybackRepeatMode.ALL
            else -> PlaybackRepeatMode.OFF
        }
    }

    private fun PlaybackRepeatMode.toPlayerRepeatMode(): Int {
        return when (this) {
            PlaybackRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            PlaybackRepeatMode.ONE -> Player.REPEAT_MODE_ONE
            PlaybackRepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    private fun Long.normalizedDuration(previous: Long): Long {
        return if (this > 0L) this else max(previous, 0L)
    }

    private suspend fun SongEntity.toPlaybackMediaItemWithArtwork(): MediaItem {
        val item = toPlaybackMediaItem()
        val artworkBytes = readNotificationArtworkBytes(this) ?: return item

        return item.buildUpon()
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .build()
            )
            .build()
    }

    private suspend fun readNotificationArtworkBytes(song: SongEntity): ByteArray? {
        return withContext(Dispatchers.IO) {
            val original = appContext.contentResolver.openFileDescriptor(song.getUri, "r")?.use { pfd ->
                AudioTagReader.readPicture(pfd)
            }?.takeIf { it.isNotEmpty() } ?: return@withContext null

            original.downsampleArtwork(maxSize = NOTIFICATION_ARTWORK_MAX_SIZE)
        }
    }

    private fun ensureCurrentItemArtwork(player: Player) {
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex < 0) return

        val currentItem = player.currentMediaItem ?: return
        if (currentItem.mediaMetadata.artworkData?.isNotEmpty() == true) return

        val currentSong = currentItem.toSongEntityOrNull() ?: return
        artworkUpdateJob?.cancel()
        artworkUpdateJob = appScope.launch {
            val itemWithArtwork = currentSong.toPlaybackMediaItemWithArtwork()
            if (itemWithArtwork.mediaMetadata.artworkData?.isNotEmpty() != true) return@launch

            withContext(Dispatchers.Main.immediate) {
                val c = controller ?: return@withContext
                if (
                    c.currentMediaItemIndex == currentIndex &&
                    currentIndex in 0 until c.mediaItemCount &&
                    c.getMediaItemAt(currentIndex).mediaId == currentItem.mediaId &&
                    c.getMediaItemAt(currentIndex).mediaMetadata.artworkData?.isNotEmpty() != true
                ) {
                    val currentPosition = c.currentPosition.coerceAtLeast(0L)
                    c.replaceMediaItem(currentIndex, itemWithArtwork)
                    if (c.currentMediaItemIndex == currentIndex && currentPosition > 0L) {
                        c.seekTo(currentPosition)
                    }
                }
            }
        }
    }

    private fun ByteArray.downsampleArtwork(maxSize: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(this, 0, size, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return takeIf { it.size <= NOTIFICATION_ARTWORK_MAX_BYTES }
        }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxSize || bounds.outHeight / sampleSize > maxSize) {
            sampleSize *= 2
        }

        val bitmap = BitmapFactory.decodeByteArray(
            this,
            0,
            size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return takeIf { it.size <= NOTIFICATION_ARTWORK_MAX_BYTES }

        return try {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
                output.toByteArray().takeIf { it.size <= NOTIFICATION_ARTWORK_MAX_BYTES }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val POSITION_UPDATE_INTERVAL_MS = 250L
        const val RESTART_PREVIOUS_THRESHOLD_MS = 3_000L
        const val NOTIFICATION_ARTWORK_MAX_SIZE = 512
        const val NOTIFICATION_ARTWORK_MAX_BYTES = 512 * 1024
    }
}
