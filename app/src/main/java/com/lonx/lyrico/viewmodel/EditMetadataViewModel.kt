package com.lonx.lyrico.viewmodel

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.audiotag.rw.AudioTagWriter
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrico.data.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EditMetadataUiState(
    val songInfo: SongInfo? = null,
    val originalTagData: AudioTagData? = null,
    val editingTagData: AudioTagData? = null,
    val currentLyrics: LyricsResult? = null,
    val coverUri: Uri? = null,
    val filePath: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean? = null,
    val permissionRequest: IntentSender? = null
)

class EditMetadataViewModel(
    private val songRepository: SongRepository,
    private val applicationContext: Context
) : ViewModel() {

    private val TAG = "EditMetadataViewModel"
    private val _uiState = MutableStateFlow(EditMetadataUiState())
    val uiState: StateFlow<EditMetadataUiState> = _uiState.asStateFlow()

    private var currentSongPath: String? = null

    fun loadSongInfo(filePath: String) {
        if (filePath == currentSongPath) {
            return
        }
        currentSongPath = filePath
        viewModelScope.launch {
            try {
                val audioTagData = withContext(Dispatchers.IO) {
                    readAudioTagDataFromFile(filePath)
                }


                _uiState.update {
                    it.copy(
                        songInfo = SongInfo(filePath = filePath, tagData = audioTagData, fileName = ""),
                        originalTagData = audioTagData,
                        editingTagData = audioTagData,
                        filePath = filePath
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "读取音频元数据失败", e)
            }
        }
    }
    private fun isUriPath(path: String): Boolean {
        return try {
            val uri = path.toUri()
            uri.scheme != null && (uri.scheme == "content" || uri.scheme == "file")
        } catch (e: Exception) {
            false
        }
    }

    fun onUpdateEditingTagData(audioTagData: AudioTagData) {
        _uiState.update { it.copy(editingTagData = audioTagData) }
    }

    fun updateMetadataFromSearchResult(result: com.lonx.lyrico.data.model.LyricsSearchResult) {
        _uiState.update { currentState ->
            val currentData = currentState.editingTagData ?: AudioTagData()
            currentState.copy(
                editingTagData = currentData.copy(
                    title = result.title?.takeIf { it.isNotBlank() } ?: currentData.title,
                    artist = result.artist?.takeIf { it.isNotBlank() } ?: currentData.artist,
                    album = result.album?.takeIf { it.isNotBlank() } ?: currentData.album,
                    lyrics = result.lyrics?.takeIf { it.isNotBlank() } ?: currentData.lyrics
                )
            )
        }
    }


    fun clearSaveStatus() {
        _uiState.update { it.copy(saveSuccess = null) }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun onPermissionResult(granted: Boolean) {
        clearPermissionRequest()
        if (granted) {
            saveMetadata()
        } else {
            _uiState.update {
                it.copy(saveSuccess = false)
            }
        }
    }

    fun clearPermissionRequest() {
        _uiState.update { it.copy(permissionRequest = null) }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveMetadata() {
        val songInfo = _uiState.value.songInfo ?: return
        val audioTagData = _uiState.value.editingTagData ?: return

        // Prevent multiple saves
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveSuccess = null) }
            try {
                val success = withContext(Dispatchers.IO) {
                    // 第一步：写入文件（音频标签）
                    val fileSuccess = writeMetadataToFile(songInfo.filePath, audioTagData)

                    if (fileSuccess) {
                        // 第二步：立即更新数据库（避免等待列表重扫）
                        Log.d(TAG, "文件标签已保存，立即更新数据库")
                        songRepository.updateSongMetadata(audioTagData, songInfo.filePath)
                        Log.d(TAG, "数据库已更新")
                        true
                    } else {
                        Log.e(TAG, "文件标签保存失败")
                        false
                    }
                }
                _uiState.update { it.copy(isSaving = false, saveSuccess = success) }
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
                    if (e is RecoverableSecurityException) {
                        Log.w(TAG, "需要用户授权才能写入文件", e)
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                permissionRequest = e.userAction.actionIntent.intentSender
                            )
                        }
                    }
                } else {
                    Log.e(TAG, "保存元数据失败", e)
                    _uiState.update { it.copy(isSaving = false, saveSuccess = false) }
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun writeMetadataToFile(filePath: String, audioTagData: AudioTagData): Boolean {
        return try {
            (if (isUriPath(filePath)) {
                applicationContext.contentResolver.openFileDescriptor(filePath.toUri(), "rw")
            } else {
                ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_WRITE)
            })?.use { pfdDescriptor ->
                val updates = mutableMapOf<String, String>()
                audioTagData.title?.let { updates["TITLE"] = it }
                audioTagData.artist?.let { updates["ARTIST"] = it }
                audioTagData.album?.let { updates["ALBUM"] = it }
                audioTagData.genre?.let { updates["GENRE"] = it }
                audioTagData.date?.let { updates["DATE"] = it }

                AudioTagWriter.writeTags(pfdDescriptor, updates)

                // Write lyrics if they exist in the editing data
                audioTagData.lyrics?.let { lyricsString ->
                    AudioTagWriter.writeLyrics(pfdDescriptor, lyricsString)
                }
                
                true
            } ?: false
        } catch (e: RecoverableSecurityException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "写入文件失败", e)
            false
        }
    }
    private suspend fun readAudioTagDataFromFile(filePath: String): AudioTagData {
        return withContext(Dispatchers.IO) {
            try {
                (if (isUriPath(filePath)) {
                    applicationContext.contentResolver.openFileDescriptor(filePath.toUri(), "r")
                } else {
                    ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
                })?.use { descriptor ->
                    // 调用 AudioTagReader.read 解析元数据
                    AudioTagReader.read(descriptor, true)
                } ?: AudioTagData()  // 若打开失败返回空元数据
            } catch (e: Exception) {
                Log.e(TAG, "读取音频元数据失败: $filePath", e)
                AudioTagData()
            }
        }
    }

}
