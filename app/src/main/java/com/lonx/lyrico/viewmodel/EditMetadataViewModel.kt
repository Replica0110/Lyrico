package com.lonx.lyrico.viewmodel

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.rw.AudioTagWriter
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.source.kg.KgSource
import com.lonx.lyrics.source.qm.QmSource
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
    val isSaving: Boolean = false,
    val saveSuccess: Boolean? = null
)

class EditMetadataViewModel(
    private val kgSource: KgSource,
    private val qmSource: QmSource,
    private val songRepository: SongRepository,
    private val applicationContext: Context
) : ViewModel() {

    private val TAG = "EditMetadataViewModel"
    private val _uiState = MutableStateFlow(EditMetadataUiState())
    val uiState: StateFlow<EditMetadataUiState> = _uiState.asStateFlow()

    fun loadSongInfo(songInfo: SongInfo) {
        _uiState.update {
            it.copy(
                songInfo = songInfo,
                originalTagData = songInfo.tagData,
                editingTagData = songInfo.tagData,
                coverUri = songInfo.coverUri
            )
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

    fun onSelectSearchResultWithLyrics(result: SongSearchResult, formattedLyrics: String) {
        _uiState.update {
            it.copy(
                editingTagData = it.editingTagData?.copy(
                    title = result.title,
                    artist = result.artist,
                    album = result.album,
                    lyrics = formattedLyrics // Use the pre-formatted lyrics
                ) ?: AudioTagData(
                    title = result.title,
                    artist = result.artist,
                    album = result.album,
                    lyrics = formattedLyrics
                )
            )
        }
        // Do not call fetchLyrics, as the lyrics are already provided and formatted.
    }

    fun onSelectSearchResult(result: SongSearchResult) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    editingTagData = it.editingTagData?.copy(
                        title = result.title,
                        artist = result.artist,
                        album = result.album
                    ) ?: AudioTagData(
                        title = result.title,
                        artist = result.artist,
                        album = result.album
                    )
                )
            }
            fetchLyrics(result)
        }
    }

    private fun fetchLyrics(song: SongSearchResult) {
        viewModelScope.launch {
            try {
                val lyricsResult = when (song.source) {
                    Source.KG -> kgSource.getLyrics(song)
                    Source.QM -> qmSource.getLyrics(song)
                    else -> null
                }
                _uiState.update { it.copy(currentLyrics = lyricsResult) }

                lyricsResult?.original?.let { krcLines ->
                    val lyricsString = krcLines.joinToString("\n") { line ->
                        line.words.joinToString(" ") { it.text }
                    }
                    _uiState.update {
                        it.copy(
                            editingTagData = it.editingTagData?.copy(lyrics = lyricsString)
                                ?: AudioTagData(lyrics = lyricsString)
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(currentLyrics = null) }
            }
        }
    }

    fun clearSaveStatus() {
        _uiState.update { it.copy(saveSuccess = null) }
    }

    fun saveMetadata() {
        val songInfo = _uiState.value.songInfo ?: return
        val audioTagData = _uiState.value.editingTagData ?: return

        // Prevent multiple saves
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveSuccess = null) }
            val success = withContext(Dispatchers.IO) {
                try {
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
                } catch (e: Exception) {
                    Log.e(TAG, "保存元数据失败", e)
                    false
                }
            }
            _uiState.update { it.copy(isSaving = false, saveSuccess = success) }
        }
    }

    private suspend fun writeMetadataToFile(filePath: String, audioTagData: AudioTagData): Boolean {
        return try {
            val pfd: ParcelFileDescriptor? = if (isUriPath(filePath)) {
                applicationContext.contentResolver.openFileDescriptor(filePath.toUri(), "rw")
            } else {
                ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_WRITE)
            }

            pfd?.use { pfdDescriptor ->
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
        } catch (e: Exception) {
            Log.e(TAG, "写入文件失败", e)
            false
        }
    }
}
