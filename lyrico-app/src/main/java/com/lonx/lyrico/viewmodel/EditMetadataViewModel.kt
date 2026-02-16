package com.lonx.lyrico.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.LyricsSearchResult
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.data.repository.PlaybackRepository
import com.lonx.lyrico.data.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class EditMetadataUiState(
    val songInfo: SongInfo? = null,

    val originalTagData: AudioTagData? = null,
    val editingTagData: AudioTagData? = null,

    val isEditing: Boolean = false,

    /**
     * 编辑态封面（只要不为 null，就代表用户替换过封面）
     */
    val coverUri: Any? = null,

    val isSaving: Boolean = false,
    val saveSuccess: Boolean? = null,
    val originalCover: Any? = null,
    val picture: AudioPicture? = null,
)

class EditMetadataViewModel(
    private val songRepository: SongRepository,
    private val playbackRepository: PlaybackRepository,
    application: Application
) : ViewModel() {
    private val contentResolver = application.contentResolver
    private val TAG = "EditMetadataViewModel"
    private var currentSong: SongEntity? = null
    private val _uiState = MutableStateFlow(EditMetadataUiState())
    val uiState: StateFlow<EditMetadataUiState> = _uiState.asStateFlow()

    private var currentSongPath: String? = null


    fun readMetadata(filePath: String) {
        currentSongPath = filePath

        viewModelScope.launch {
            try {
                val song = songRepository.getSongByFilePath(filePath)
                currentSong = song
                val audioTagData = songRepository.readAudioTagData(filePath)
                val firstPicture = audioTagData.pictures.firstOrNull()?.data

                _uiState.update { state ->
                    state.copy(
                        songInfo = SongInfo(
                            filePath = filePath,
                            tagData = audioTagData
                        ),
                        originalTagData = audioTagData,

                        // 初始化 editingTagData 只有未编辑时才设置
                        editingTagData = if (state.isEditing) state.editingTagData else audioTagData,

                        picture = audioTagData.pictures.firstOrNull(),

                        originalCover = firstPicture,

                        // coverUri 初始化为原始封面（未编辑时）
                        coverUri = if (state.isEditing) state.coverUri else firstPicture
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "读取音频元数据失败", e)
            }
        }
    }


    fun onUpdateEditingTagData(audioTagData: AudioTagData) {
        _uiState.update {
            it.copy(
                editingTagData = audioTagData,
                isEditing = true
            )
        }
    }
    fun updateTag(block: AudioTagData.() -> AudioTagData) {
        _uiState.update { state ->
            val current = state.editingTagData ?: return@update state
            state.copy(
                editingTagData = current.block(),
                isEditing = true
            )
        }
    }

    fun updateMetadataFromSearchResult(result: LyricsSearchResult) {
        _uiState.update { state ->
            val current = state.editingTagData ?: AudioTagData()

            state.copy(
                isEditing = true,

                editingTagData = current.copy(
                    title = result.title?.takeIf { it.isNotBlank() } ?: current.title,
                    artist = result.artist?.takeIf { it.isNotBlank() } ?: current.artist,
                    album = result.album?.takeIf { it.isNotBlank() } ?: current.album,
                    lyrics = result.lyrics?.takeIf { it.isNotBlank() } ?: current.lyrics,
                    date = result.date?.takeIf { it.isNotBlank() } ?: current.date,
                    trackerNumber = result.trackerNumber?.takeIf { it.isNotBlank() }
                        ?: current.trackerNumber,
                    picUrl = result.picUrl?.takeIf { it.isNotBlank() } ?: current.picUrl
                ),

                // 只要 picUrl 存在，就认为封面被修改
                coverUri = result.picUrl?.takeIf { it.isNotBlank() }?.toUri()
            )
        }
    }
    fun revertCover() {
        _uiState.update {
            it.copy(
                coverUri = it.originalCover,
                editingTagData = it.editingTagData?.copy(picUrl = null)
            )
        }
    }


    fun saveMetadata() {
        val songInfo = _uiState.value.songInfo ?: return
        val audioTagData = _uiState.value.editingTagData ?: return
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveSuccess = null) }

            try {
                val success = songRepository.writeAudioTagData(
                    songInfo.filePath,
                    audioTagData
                )

                if (success) {
                    val lastModified = getFileLastModified(songInfo.filePath)

                    songRepository.updateSongMetadata(
                        audioTagData,
                        songInfo.filePath,
                        lastModified
                    )

                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = true,

                            // 编辑会话结束
                            isEditing = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存失败", e)
                _uiState.update { it.copy(isSaving = false, saveSuccess = false) }
            }
        }
    }

    fun clearSaveStatus() {
        _uiState.update { it.copy(saveSuccess = null) }
    }


    private fun getFileLastModified(filePath: String): Long {
        val uri = filePath.toUri()

        return try {
            if (uri.scheme == "content") {
                contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DATE_MODIFIED),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                        if (idx != -1) cursor.getLong(idx) * 1000
                        else System.currentTimeMillis()
                    } else System.currentTimeMillis()
                } ?: System.currentTimeMillis()
            } else {
                val file = File(uri.path ?: "")
                if (file.exists()) file.lastModified()
                else System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取修改时间失败", e)
            System.currentTimeMillis()
        }
    }

    fun play(context: Context) {
        val song = currentSong ?: return
        playbackRepository.play(context, song.getUri)
    }

}

