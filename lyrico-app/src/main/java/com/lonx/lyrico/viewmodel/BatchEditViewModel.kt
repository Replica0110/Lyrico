package com.lonx.lyrico.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.R
import com.lonx.lyrico.data.SharedSelectionManager
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 可批量编辑的标签字段枚举
 */
enum class BatchEditField(val labelResId: Int) {
    TITLE(R.string.label_title),
    ARTIST(R.string.label_artists),
    ALBUM_ARTIST(R.string.label_album_artist),
    ALBUM(R.string.label_album),
    DATE(R.string.label_date),
    GENRE(R.string.label_genre),
    TRACK_NUMBER(R.string.label_track_number),
    DISC_NUMBER(R.string.label_disc_number),
    COMPOSER(R.string.label_composer),
    LYRICIST(R.string.label_lyricist),
    COPYRIGHT(R.string.label_copyright),
    COMMENT(R.string.label_comment),
    LYRICS(R.string.label_lyrics),
    COVER(R.string.label_cover),
    RATING(R.string.label_rating),
}

data class BatchEditUiState(
    val songCount: Int = 0,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveProgress: Int = 0,
    val saveTotal: Int = 0,
    val saveSuccess: Boolean? = null,
    val saveResultMessage: UiMessage? = null,
    val errorMessage: UiMessage? = null,

    /** 各字段当前编辑值（"<keep>"表示不修改） */
    val title: String = "<keep>",
    val artist: String = "<keep>",
    val albumArtist: String = "<keep>",
    val album: String = "<keep>",
    val date: String = "<keep>",
    val genre: String = "<keep>",
    val trackNumber: String = "<keep>",
    val discNumber: String = "<keep>",
    val composer: String = "<keep>",
    val lyricist: String = "<keep>",
    val copyright: String = "<keep>",
    val comment: String = "<keep>",
    val lyrics: String = "<keep>",
    val rating: Int = 0,
    val ratingModified: Boolean = false,

    /** 封面相关 */
    val coverUri: Any? = null,
    val removeCover: Boolean = false
)

class BatchEditViewModel(
    private val songRepository: SongRepository,
    private val selectionManager: SharedSelectionManager
) : ViewModel() {

    private val TAG = "BatchEditVM"

    private val _uiState = MutableStateFlow(BatchEditUiState())
    val uiState: StateFlow<BatchEditUiState> = _uiState.asStateFlow()

    /** 保存选中的文件uri */
    private var selectedUris: List<String> = emptyList()

    init {
        val paths = selectionManager.selectedUris.value.toList()
        selectedUris = paths
        _uiState.update { it.copy(songCount = paths.size) }
    }



    // ── 标签值更新 ──────────────────────────────────────────

    fun updateTitle(value: String) { _uiState.update { it.copy(title = value) } }
    fun updateArtist(value: String) { _uiState.update { it.copy(artist = value) } }
    fun updateAlbumArtist(value: String) { _uiState.update { it.copy(albumArtist = value) } }
    fun updateAlbum(value: String) { _uiState.update { it.copy(album = value) } }
    fun updateDate(value: String) { _uiState.update { it.copy(date = value) } }
    fun updateGenre(value: String) { _uiState.update { it.copy(genre = value) } }
    fun updateTrackNumber(value: String) { _uiState.update { it.copy(trackNumber = value) } }
    fun updateDiscNumber(value: String) { _uiState.update { it.copy(discNumber = value) } }
    fun updateComposer(value: String) { _uiState.update { it.copy(composer = value) } }
    fun updateLyricist(value: String) { _uiState.update { it.copy(lyricist = value) } }
    fun updateCopyright(value: String) { _uiState.update { it.copy(copyright = value) } }
    fun updateComment(value: String) { _uiState.update { it.copy(comment = value) } }
    fun updateLyrics(value: String) { _uiState.update { it.copy(lyrics = value) } }
    fun updateRating(value: Int) { 
        _uiState.update { it.copy(rating = value, ratingModified = true) } 
    }
    fun resetRating() { 
        _uiState.update { it.copy(rating = 0, ratingModified = false) } 
    }


    // ── 封面管理 ──────────────────────────────────────────

    fun updateCover(uri: Uri) {
        _uiState.update { it.copy(coverUri = uri, removeCover = false) }
    }

    fun removeCover() {
        _uiState.update { it.copy(coverUri = null, removeCover = true) }
    }

    fun revertCover() {
        _uiState.update { it.copy(coverUri = null, removeCover = false) }
    }

    // ── 批量保存 ──────────────────────────────────────────

    fun saveBatchEdit() {
        val state = _uiState.value
        if (state.isSaving || selectedUris.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveProgress = 0,
                    saveTotal = selectedUris.size,
                    saveSuccess = null,
                    saveResultMessage = null,
                    errorMessage = null
                )
            }

            var successCount = 0
            var failCount = 0

            for ((index, path) in selectedUris.withIndex()) {
                try {
                    val success = withContext(Dispatchers.IO) {
                        updateAudioTags(path, state)
                    }
                    if (success) successCount++ else failCount++
                } catch (e: Exception) {
                    Log.e(TAG, "批量编辑失败: $path", e)
                    failCount++
                }

                _uiState.update { it.copy(saveProgress = index + 1) }
            }

            _uiState.update {
                it.copy(
                    isSaving = false,
                    saveSuccess = failCount == 0,
                    saveResultMessage = UiMessage.StringResource(
                        R.string.batch_edit_result_summary,
                        successCount,
                        selectedUris.size,
                        failCount
                    )
                )
            }
        }
    }

    /**
     * 处理单首歌曲的批量编辑
     * 先读取原始标签，再合并用户选择的字段，最后写回
     */
    private suspend fun updateAudioTags(uri: String, state: BatchEditUiState): Boolean {
        // 读取当前标签
        val uriString = uri
        val currentTag = try {
            songRepository.readAudioTagData(uriString)
        } catch (e: Exception) {
            Log.e(TAG, "无法读取标签: $uri", e)
            return false
        }

        // 按用户启用的字段合并数据
        val mergedTag = buildMergedTag(currentTag, state)

        // 写入文件
        return try {
            val success = songRepository.overwriteAudioTags(uriString, mergedTag)
            if (success) {
                songRepository.updateSongMetadata(mergedTag, uriString, System.currentTimeMillis())
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "写入标签失败: $uri", e)
            false
        }
    }

    /**
     * 根据用户编辑的值，将批量编辑值合并到原标签中
     * 值为"<keep>"时表示不修改该字段
     */
    private fun buildMergedTag(original: AudioTagData, state: BatchEditUiState): AudioTagData {
        var tag = original

        if (state.title != "<keep>") tag = tag.copy(title = state.title)
        if (state.artist != "<keep>") tag = tag.copy(artist = state.artist)
        if (state.albumArtist != "<keep>") tag = tag.copy(albumArtist = state.albumArtist)
        if (state.album != "<keep>") tag = tag.copy(album = state.album)
        if (state.date != "<keep>") tag = tag.copy(date = state.date)
        if (state.genre != "<keep>") tag = tag.copy(genre = state.genre)
        if (state.trackNumber != "<keep>") tag = tag.copy(trackNumber = state.trackNumber)
        if (state.discNumber != "<keep>") tag = tag.copy(discNumber = state.discNumber.toIntOrNull())
        if (state.composer != "<keep>") tag = tag.copy(composer = state.composer)
        if (state.lyricist != "<keep>") tag = tag.copy(lyricist = state.lyricist)
        if (state.copyright != "<keep>") tag = tag.copy(copyright = state.copyright)
        if (state.comment != "<keep>") tag = tag.copy(comment = state.comment)
        if (state.lyrics != "<keep>") tag = tag.copy(lyrics = state.lyrics)
        
        // 处理 rating - 只在明确修改时才更新
        if (state.ratingModified) tag = tag.copy(rating = state.rating)

        // 处理覆盖图
        if (state.removeCover) {
            tag = tag.copy(picUrl = "")
        } else if (state.coverUri != null) {
            tag = tag.copy(picUrl = state.coverUri.toString())
        }

        return tag
    }

    // ── 状态清理 ──────────────────────────────────────────

    fun clearSaveResult() {
        _uiState.update { it.copy(saveSuccess = null, saveResultMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        selectionManager.clearAll()
    }
}
