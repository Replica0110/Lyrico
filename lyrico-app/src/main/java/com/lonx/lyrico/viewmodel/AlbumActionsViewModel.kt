package com.lonx.lyrico.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.domain.song.usecase.DeleteSongsUseCase
import com.lonx.lyrico.domain.song.usecase.PatchSongTagsUseCase
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.utils.AlbumReplayGainCalculateState
import com.lonx.lyrico.utils.ReplayGainError
import com.lonx.lyrico.utils.ReplayGainScanner
import com.lonx.lyrico.utils.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumActionsUiState(
    val albumName: String? = null,
    val isCalculatingAlbumReplayGain: Boolean = false,
    val showAlbumReplayGainProgressDialog: Boolean = false,
    val albumReplayGainProgress: Float? = null,
    val albumReplayGainSongCount: Int = 0,
    val albumReplayGainWrittenCount: Int = 0,
    val albumReplayGainTotalTimeMillis: Long = 0
)

class AlbumActionsViewModel(
    private val libraryIndexRepository: LibraryIndexRepository,
    private val deleteSongsUseCase: DeleteSongsUseCase,
    private val patchSongTagsUseCase: PatchSongTagsUseCase,
    private val replayGainScanner: ReplayGainScanner,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumActionsUiState())
    val uiState: StateFlow<AlbumActionsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiMessage>()
    val events: SharedFlow<UiMessage> = _events.asSharedFlow()

    private var replayGainJob: Job? = null
    private var replayGainStartedAt: Long = 0L

    fun shareAlbum(context: Context, albumId: Long) {
        viewModelScope.launch {
            shareAlbum(context, libraryIndexRepository.getSongsByAlbumId(albumId))
        }
    }

    fun shareAlbum(context: Context, songs: List<SongEntity>) {
        if (songs.isEmpty()) return

        val uris = songs.map { it.uri.toUri() }.toCollection(ArrayList())
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(
                intent,
                context.getString(R.string.share_chooser_title)
            )
        )
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch {
            deleteAlbum(libraryIndexRepository.getSongsByAlbumId(albumId))
        }
    }

    fun deleteAlbum(songs: List<SongEntity>) {
        viewModelScope.launch {
            val result = deleteSongsUseCase(songs)
            _events.emit(
                UiMessage.StringResource(
                    R.string.album_delete_success,
                    result.deleted,
                    result.total
                )
            )
        }
    }

    fun calculateAlbumReplayGain(albumId: Long) {
        viewModelScope.launch {
            calculateAlbumReplayGain(libraryIndexRepository.getSongsByAlbumId(albumId))
        }
    }

    fun calculateAlbumReplayGain(songs: List<SongEntity>) {
        if (_uiState.value.isCalculatingAlbumReplayGain) return
        if (songs.isEmpty()) {
            viewModelScope.launch {
                _events.emit(UiMessage.StringResource(R.string.replay_gain_no_songs))
            }
            return
        }

        replayGainJob?.cancel()
        replayGainJob = viewModelScope.launch(Dispatchers.IO) {
            replayGainStartedAt = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    isCalculatingAlbumReplayGain = true,
                    showAlbumReplayGainProgressDialog = true,
                    albumReplayGainProgress = 0f,
                    albumReplayGainSongCount = songs.size,
                    albumReplayGainWrittenCount = 0,
                    albumReplayGainTotalTimeMillis = 0L,
                    albumName = songs.firstOrNull()?.album
                )
            }

            try {
                replayGainScanner.analyzeAlbum(songs.map { it.uri }).collect { state ->
                    when (state) {
                        is AlbumReplayGainCalculateState.Progress -> {
                            _uiState.update {
                                it.copy(albumReplayGainProgress = (state.percent * ANALYZE_PROGRESS_WEIGHT).coerceIn(0f, ANALYZE_PROGRESS_WEIGHT))
                            }
                        }
                        AlbumReplayGainCalculateState.Cancelled -> {
                            _events.emit(UiMessage.StringResource(R.string.replay_gain_calculate_cancelled))
                        }
                        is AlbumReplayGainCalculateState.Failed -> {
                            _events.emit(mapErrorToUiMessage(state.mimeType, state.error))
                        }
                        is AlbumReplayGainCalculateState.Success -> {
                            writeAlbumReplayGain(songs, state)
                        }
                    }
                }
            } finally {
                val duration = if (replayGainStartedAt > 0L) {
                    System.currentTimeMillis() - replayGainStartedAt
                } else {
                    0L
                }
                _uiState.update {
                    it.copy(
                        isCalculatingAlbumReplayGain = false,
                        albumReplayGainProgress = it.albumReplayGainProgress ?: 0f,
                        albumReplayGainTotalTimeMillis = duration
                    )
                }
            }
        }
    }

    fun cancelAlbumReplayGain() {
        replayGainJob?.cancel()
        _uiState.update {
            it.copy(
                isCalculatingAlbumReplayGain = false,
                albumReplayGainProgress = 0f
            )
        }
        viewModelScope.launch {
            _events.emit(UiMessage.StringResource(R.string.replay_gain_calculate_cancelled))
        }
    }

    fun closeAlbumReplayGainProgressDialog() {
        if (_uiState.value.isCalculatingAlbumReplayGain) return
        _uiState.update { it.copy(showAlbumReplayGainProgressDialog = false) }
    }

    fun clearAlbumReplayGainProgressDialog() {
        if (_uiState.value.isCalculatingAlbumReplayGain || _uiState.value.showAlbumReplayGainProgressDialog) return
        _uiState.update {
            it.copy(
                albumReplayGainProgress = null,
                albumReplayGainSongCount = 0,
                albumReplayGainWrittenCount = 0,
                albumReplayGainTotalTimeMillis = 0L
            )
        }
    }

    private suspend fun writeAlbumReplayGain(
        songs: List<SongEntity>,
        state: AlbumReplayGainCalculateState.Success
    ) {
        val targetLoudness = settingsRepository.replayGainTargetLoudness.first()
        val tagData = AudioTagData(
            replayGainAlbumGain = replayGainScanner.formatGain(state.analysis.loudnessLufs, targetLoudness),
            replayGainAlbumPeak = replayGainScanner.formatPeak(state.analysis.peak),
            replayGainReferenceLoudness = replayGainScanner.formatReferenceLoudness(targetLoudness)
        )

        var written = 0
        for (song in songs) {
            when (val result = patchSongTagsUseCase(song.uri, tagData)) {
                is SaveAudioTagsResult.Success -> {
                    written += 1
                    val writeProgress = if (songs.isNotEmpty()) {
                        WRITE_PROGRESS_WEIGHT * written.toFloat() / songs.size.toFloat()
                    } else {
                        WRITE_PROGRESS_WEIGHT
                    }
                    _uiState.update {
                        it.copy(
                            albumReplayGainWrittenCount = written,
                            albumReplayGainProgress = (ANALYZE_PROGRESS_WEIGHT + writeProgress).coerceIn(0f, 1f)
                        )
                    }
                }
                is SaveAudioTagsResult.PermissionRequired -> {
                    _events.emit(UiMessage.StringResource(R.string.album_replay_gain_write_permission_required))
                    return
                }
                is SaveAudioTagsResult.Failed -> {
                    _events.emit(
                        UiMessage.StringResource(
                            R.string.album_replay_gain_write_failed,
                            result.error.message ?: result.error::class.java.simpleName
                        )
                    )
                    return
                }
            }
        }

        _events.emit(
            UiMessage.StringResource(
                R.string.album_replay_gain_success,
                written
            )
        )
    }

    private fun mapErrorToUiMessage(mimeType: String?, error: ReplayGainError): UiMessage {
        return when (error) {
            is ReplayGainError.NoAudioTrack -> UiMessage.StringResource(R.string.replay_gain_error_no_audio_track)
            is ReplayGainError.UnknownMimeType -> UiMessage.StringResource(R.string.replay_gain_error_unknown_mime_type)
            is ReplayGainError.ZeroSampleCount -> UiMessage.StringResource(R.string.replay_gain_error_zero_sample_count)
            is ReplayGainError.UnsupportedCodec -> UiMessage.StringResource(
                R.string.replay_gain_error_unsupported_codec,
                mimeType ?: error.mimeType ?: "unknown"
            )
            is ReplayGainError.CodecException -> {
                if (error.isAlacIssue) {
                    UiMessage.StringResource(R.string.replay_gain_error_alac_issue)
                } else {
                    UiMessage.StringResource(R.string.replay_gain_error_codec_exception, error.message ?: "")
                }
            }
            is ReplayGainError.GeneralException -> UiMessage.StringResource(
                R.string.replay_gain_error_general,
                error.message ?: ""
            )
        }
    }

    private companion object {
        const val ANALYZE_PROGRESS_WEIGHT = 0.9f
        const val WRITE_PROGRESS_WEIGHT = 0.1f
    }
}
