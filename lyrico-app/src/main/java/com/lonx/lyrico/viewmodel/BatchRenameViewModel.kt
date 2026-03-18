package com.lonx.lyrico.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.lyrico.data.model.CharacterMappingConfig
import com.lonx.lyrico.data.model.RenamePreview
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.utils.RenameEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.net.Uri
import com.lonx.lyrico.R
import com.lonx.lyrico.utils.UiMessage
import java.io.File

data class BatchRenameUiState(
    val songs: List<SongForBatchRename> = emptyList(),
    val format: String = "@1 - @2",
    val presetFormats: List<String> = emptyList(),
    val previews: List<RenamePreview> = emptyList(),
    val isGeneratingPreview: Boolean = false,
    val isRenamingInProgress: Boolean = false,
    val renameResult: RenameEngine.Result? = null,
    val errorMessage: UiMessage? = null,
    val characterMappingConfig: CharacterMappingConfig? = null
)

data class SongForBatchRename(
    val filePath: String,
    val fileName: String,
    val tagData: AudioTagData?
)

class BatchRenameViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchRenameUiState())
    val uiState: StateFlow<BatchRenameUiState> = _uiState

    init {
        _uiState.value = _uiState.value.copy(
            presetFormats = RenameEngine.getPresetFormats()
        )

        viewModelScope.launch {
            settingsRepository.characterMappingConfig.collect { config ->
                _uiState.value = _uiState.value.copy(
                    characterMappingConfig = config
                )
            }
        }
    }
    fun setSongs(context: Context, songs: List<SongForBatchRename>) {
        _uiState.value = _uiState.value.copy(
            songs = songs,
            previews = emptyList(),
            renameResult = null,
            errorMessage = null
        )
        generatePreviews(context)
    }

    fun setFormat(context: Context, format: String) {
        _uiState.value = _uiState.value.copy(format = format)
        generatePreviews(context)
    }

    fun generatePreviews(context: Context) {
        val currentState = _uiState.value
        if (currentState.songs.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(
                    isGeneratingPreview = true,
                    errorMessage = null
                )

                val songsForRename = currentState.songs.mapNotNull { song ->
                    val tagData = song.tagData ?: loadTagData(context, song.filePath)
                    if (tagData != null) {
                        RenameEngine.SongForRename(
                            originalPath = song.filePath,
                            tagData = tagData
                        )
                    } else {
                        null
                    }
                }

                if (songsForRename.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingPreview = false,
                        errorMessage = UiMessage.StringResource(R.string.no_tag_data)
                    )
                    return@launch
                }

                val mappingRules = currentState.characterMappingConfig?.rules ?: emptyList()

                val request = RenameEngine.RenameRequest(
                    songs = songsForRename,
                    format = currentState.format,
                    characterMappingRules = mappingRules
                )

                val previews = RenameEngine.generatePreviews(request)

                _uiState.value = _uiState.value.copy(
                    previews = previews,
                    isGeneratingPreview = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingPreview = false,
                    errorMessage = UiMessage.DynamicString(e.message)
                )
            }
        }
    }

    fun executeRename() {
        val previews = _uiState.value.previews
        if (previews.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(
                    isRenamingInProgress = true,
                    errorMessage = null
                )

                val result = RenameEngine.renameFiles(previews)

                _uiState.value = _uiState.value.copy(
                    renameResult = result,
                    isRenamingInProgress = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRenamingInProgress = false,
                    errorMessage = UiMessage.DynamicString(e.message)
                )
            }
        }
    }

    private suspend fun loadTagData(context: Context,filePath: String): AudioTagData? {
        return try {
            val uri = Uri.fromFile(File(filePath))
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                AudioTagReader.read(descriptor, true)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(
            renameResult = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null
        )
    }

    fun updateCharacterMappingInRule(ruleId: String, character: String, replacementChar: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            // 获取当前规则
            val currentConfig = _uiState.value.characterMappingConfig ?: return@launch
            val rule = currentConfig.rules.find { it.id == ruleId } ?: return@launch
            
            // 更新该字符的映射
            val updatedMappings = rule.charMappings.toMutableMap()
            // 保持映射关系，使用空字符串表示"移除"，不删除键
            updatedMappings[character] = replacementChar ?: ""
            
            settingsRepository.updateCharacterMappingInRule(ruleId, updatedMappings)
        }
    }
}
