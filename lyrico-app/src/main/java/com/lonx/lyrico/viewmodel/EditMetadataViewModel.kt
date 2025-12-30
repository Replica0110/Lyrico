package com.lonx.lyrico.viewmodel

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrics.model.LyricsResult
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

    fun readMetadata(filePath: String) {
        if (filePath == currentSongPath) {
            return
        }
        currentSongPath = filePath
        viewModelScope.launch {
            try {
                val audioTagData = songRepository.readAudioTagData(filePath)

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
                // Step 1: Write to file via repository
                val fileSuccess = songRepository.writeAudioTagData(songInfo.filePath, audioTagData)

                if (fileSuccess) {
                    // Step 2: Get the actual modification time of the file
                    val lastModified = getFileLastModified(songInfo.filePath)
                    // Step 3: Immediately update the database with the new metadata and timestamp
                    Log.d(TAG, "File tags saved, updating database immediately")
                    songRepository.updateSongMetadata(audioTagData, songInfo.filePath, lastModified)
                    Log.d(TAG, "Database updated")
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                } else {
                    Log.e(TAG, "Failed to save file tags")
                    _uiState.update { it.copy(isSaving = false, saveSuccess = false) }
                }
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && e is RecoverableSecurityException) {
                    Log.w(TAG, "User permission required to write to the file", e)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            permissionRequest = e.userAction.actionIntent.intentSender
                        )
                    }
                } else {
                    Log.e(TAG, "Failed to save metadata", e)
                    _uiState.update { it.copy(isSaving = false, saveSuccess = false) }
                }
            }
        }
    }

    private fun getFileLastModified(filePath: String): Long {
        val fileUri = filePath.toUri()
        try {
            if (fileUri.scheme == "content") {
                applicationContext.contentResolver.query(
                    fileUri,
                    arrayOf(android.provider.MediaStore.MediaColumns.DATE_MODIFIED),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val modifiedDateColumn = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                        if (modifiedDateColumn != -1) {
                            // MediaStore timestamp is in seconds, convert to milliseconds
                            return cursor.getLong(modifiedDateColumn) * 1000
                        }
                    }
                }
            } else { // Fallback for file:// or direct paths
                val file = File(fileUri.path?: "")
                if (file.exists()) {
                    return file.lastModified()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not get last modified time for $filePath", e)
        }
        // Fallback to current time if everything else fails
        return System.currentTimeMillis()
    }
}
