package com.lonx.lyrico.viewmodel

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.os.Build
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
    val permissionIntentSender: IntentSender? = null
)

class EditMetadataViewModel(
    private val songRepository: SongRepository,
    private val playbackRepository: PlaybackRepository,
    application: Application
) : ViewModel() {

    private val contentResolver = application.contentResolver
    private val TAG = "EditMetadataVM"

    private var currentSong: SongEntity? = null

    // 存储当前正在操作的 URI 字符串
    private var currentSongUri: String? = null

    private val _uiState = MutableStateFlow(EditMetadataUiState())
    val uiState: StateFlow<EditMetadataUiState> = _uiState.asStateFlow()

    fun readMetadata(uriString: String) {
        currentSongUri = uriString

        viewModelScope.launch {
            try {
                // 1. 获取数据库实体
                val song = songRepository.getSongByUri(uriString)
                currentSong = song

                // 2. 读取文件标签
                val audioTagData = songRepository.readAudioTagData(uriString)
                val firstPicture = audioTagData.pictures.firstOrNull()?.data

                _uiState.update { state ->
                    state.copy(
                        songInfo = SongInfo(
                            filePath = uriString, // 这里的 filePath 字段实际存的是 URI
                            tagData = audioTagData
                        ),
                        originalTagData = audioTagData,

                        // 如果当前没有在编辑，才重置 editingTagData
                        editingTagData = if (state.isEditing) state.editingTagData else audioTagData,

                        picture = audioTagData.pictures.firstOrNull(),
                        originalCover = if (state.isEditing) state.originalCover else firstPicture,
                        coverUri = if (state.isEditing) state.coverUri else firstPicture
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "读取音频元数据失败: $uriString", e)
                // 这里可以考虑加一个 error message 状态
            }
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

    // ... updateMetadataFromSearchResult 保持不变 ...
    fun updateMetadataFromSearchResult(result: LyricsSearchResult) {
        _uiState.update { state ->
            val current = state.editingTagData ?: AudioTagData()

            if (result.lyricsOnly) {
                return@update state.copy(
                    isEditing = true,
                    editingTagData = current.copy(
                        lyrics = result.lyrics?.takeIf { it.isNotBlank() } ?: current.lyrics
                    )
                )
            }

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

    /**
     * 保存元数据
     * 核心修改：处理 RecoverableSecurityException
     */
    fun saveMetadata() {
        // 从 State 中获取 SongInfo (其中 filePath 存的是 uri)
        val uriString = _uiState.value.songInfo?.filePath ?: return
        val audioTagData = _uiState.value.editingTagData ?: return

        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveSuccess = null, permissionIntentSender = null) }

            try {
                // 1. 尝试写入文件
                // 注意：Repository 需要抛出异常，而不是只返回 false，否则无法捕获权限请求
                val success = songRepository.writeAudioTagData(uriString, audioTagData)

                if (success) {
                    // 2. 写入成功，使用当前时间作为最后修改时间
                    val newModifiedTime = System.currentTimeMillis()

                    // 3. 更新数据库
                    songRepository.updateSongMetadata(
                        audioTagData,
                        uriString, // 传入 URI
                        newModifiedTime
                    )

                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = true,
                            isEditing = false
                        )
                    }
                } else {
                    // 逻辑上的写入失败（非权限问题）
                    _uiState.update { it.copy(isSaving = false, saveSuccess = false) }
                }

            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    Log.w(TAG, "需要用户授权修改文件: $uriString")
                    _uiState.update {
                        it.copy(
                            isSaving = false, // 暂停保存状态
                            permissionIntentSender = e.userAction.actionIntent.intentSender
                        )
                    }
                } else {
                    Log.e(TAG, "保存元数据发生未知错误", e)
                    _uiState.update { it.copy(isSaving = false, saveSuccess = false) }
                }
            }
        }
    }

    /**
     * UI层在成功发起弹窗或处理完权限请求后调用此方法清理状态
     */
    fun consumePermissionRequest() {
        _uiState.update { it.copy(permissionIntentSender = null) }
    }

    fun clearSaveStatus() {
        _uiState.update { it.copy(saveSuccess = null) }
    }

    fun play(context: Context) {
        val uriStr = currentSong?.uri ?: currentSongUri ?: return
        playbackRepository.play(context, uriStr.toUri())
    }
}
