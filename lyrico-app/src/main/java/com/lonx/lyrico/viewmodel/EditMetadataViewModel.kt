package com.lonx.lyrico.viewmodel

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.LyricsSearchResult
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.PlaybackRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.LyricsUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditMetadataUiState(
    val songInfo: SongInfo? = null,

    val originalTagData: AudioTagData? = null,
    val editingTagData: AudioTagData? = null,

    val isEditing: Boolean = false,

    /**
     * 编辑态封面（只要不为 null，就代表用户替换过封面）
     */
    val coverUri: Any? = null,
    val exportCoverResult: Boolean? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean? = null,
    val originalCover: Any? = null,
    val picture: AudioPicture? = null,
    val permissionIntentSender: IntentSender? = null
)

class EditMetadataViewModel(
    private val songRepository: SongRepository,
    private val playbackRepository: PlaybackRepository
) : ViewModel() {

    private val TAG = "EditMetadataVM"

    private var currentSong: SongEntity? = null

    // 存储当前正在操作的 URI 字符串
    private var currentSongUri: String? = null
    private var preOffsetLyrics: String? = null

    // 记录当前的累计偏移量，供 UI 显示
    private val _currentShiftOffset = MutableStateFlow(0L)
    val currentShiftOffset: StateFlow<Long> = _currentShiftOffset.asStateFlow()
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
                            uriString = uriString,
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
    /**
     * 打开弹窗前准备：拍快照，并重置累计偏移量
     */
    fun prepareLyricsOffset() {
        preOffsetLyrics = _uiState.value.editingTagData?.lyrics
        _currentShiftOffset.value = 0L
    }

    /**
     * 应用绝对偏移量（相对于快照）
     */
    fun applyLyricsOffset(totalOffset: Long) {
        val originalLyrics = preOffsetLyrics ?: return

        // 1. 更新当前显示的数值
        _currentShiftOffset.value = totalOffset

        // 2. 永远基于 originalLyrics (快照) 进行偏移，避免来回计算导致的精度丢失或触底失真
        val shiftedLyrics = LyricsUtils.shiftLyricsOffset(originalLyrics, totalOffset)

        _uiState.update { state ->
            state.copy(
                editingTagData = state.editingTagData?.copy(lyrics = shiftedLyrics),
                isEditing = true
            )
        }
    }
    /**
     * 重置回打开 BottomSheet 时的状态
     */
    fun resetLyricsOffset() {
        _currentShiftOffset.value = 0L
        preOffsetLyrics?.let { original ->
            _uiState.update { state ->
                state.copy(
                    editingTagData = state.editingTagData?.copy(lyrics = original)
                )
            }
        }
    }
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
                    trackNumber = result.trackerNumber?.takeIf { it.isNotBlank() }
                        ?: current.trackNumber,
                    picUrl = result.picUrl?.takeIf { it.isNotBlank() } ?: current.picUrl
                ),
                coverUri = result.picUrl?.takeIf { it.isNotBlank() }?.toUri()
            )
        }
    }
    /**
     * 更新封面（从本地选择）
     */
    fun updateCover(uri: Uri) {
        _uiState.update { state ->
            state.copy(
                coverUri = uri,
                isEditing = true,
                editingTagData = state.editingTagData?.copy(picUrl = uri.toString())
            )
        }
    }

    /**
     * 移除封面
     */
    fun removeCover() {
        _uiState.update { state ->
            state.copy(
                coverUri = null, // 清空 coverUri 表示被移除
                isEditing = true,
                editingTagData = state.editingTagData?.copy(picUrl = "")
            )
        }
    }

    /**
     * 导出当前封面到本地相册
     */
    fun exportCover(context: Context) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val coverSource = state.coverUri ?: state.originalCover ?: state.picture?.data

                if (coverSource == null) {
                    _uiState.update { it.copy(exportCoverResult = false) }
                    return@launch
                }

                val filename = "Cover_${System.currentTimeMillis()}.jpg"
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Covers")
                }

                val resolver = context.contentResolver
                val destUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (destUri != null) {
                    resolver.openOutputStream(destUri)?.use { outputStream ->
                        when (coverSource) {
                            is ByteArray -> {
                                outputStream.write(coverSource)
                            }
                            is Uri -> { // 如果是Uri，读取流然后拷贝
                                resolver.openInputStream(coverSource)?.use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            is String -> { // 如果是String(Url/Uri string)
                                resolver.openInputStream(coverSource.toUri())?.use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }
                    }
                    _uiState.update { it.copy(exportCoverResult = true) }
                } else {
                    _uiState.update { it.copy(exportCoverResult = false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "导出封面失败", e)
                _uiState.update { it.copy(exportCoverResult = false) }
            }
        }
    }

    fun clearExportCoverStatus() {
        _uiState.update { it.copy(exportCoverResult = null) }
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
        val uriString = _uiState.value.songInfo?.uriString ?: return
        val audioTagData = _uiState.value.editingTagData ?: return

        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveSuccess = null, permissionIntentSender = null) }

            try {
                val success = songRepository.writeAudioTagData(uriString, audioTagData)

                if (success) {
                    val newModifiedTime = System.currentTimeMillis()

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
