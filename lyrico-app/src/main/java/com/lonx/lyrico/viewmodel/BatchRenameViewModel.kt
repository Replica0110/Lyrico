package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.BatchTaskStatus
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.CharacterMappingConfig
import com.lonx.lyrico.data.model.RenamePreview
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.BatchTaskRepository
import com.lonx.lyrico.data.repository.SettingsDefaults
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.utils.RenameEngine
import com.lonx.lyrico.worker.BatchTaskScheduler
import com.lonx.lyrico.worker.processor.RenameFilesTaskConfig
import com.lonx.lyrico.worker.processor.RenameFilesTaskResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

data class BatchRenameUiState(
    val songCount: Int = 0,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: com.lonx.lyrico.utils.UiMessage? = null,
    val songs: List<SongForBatchRename> = emptyList(),
    val presetFormats: List<String> = emptyList(),
    val previews: List<RenamePreview> = emptyList(),
    val isGeneratingPreview: Boolean = false,
    val characterMappingConfig: CharacterMappingConfig? = null,
    val currentTaskId: String? = null,
    val isRenamingInProgress: Boolean = false,
    val renameProgress: Pair<Int, Int>? = null,
    val currentFile: String = "",
    val renameTimeMillis: Long = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skippedCount: Int = 0
)

data class SongForBatchRename(
    val uri: String,
    val filePath: String,
    val fileName: String,
    val tagData: AudioTagData?
)

class BatchRenameViewModel(
    private val settingsRepository: SettingsRepository,
    private val songLibraryRepository: SongLibraryRepository,
    private val selectionManager: com.lonx.lyrico.data.SharedSelectionManager,
    private val batchTaskRepository: BatchTaskRepository,
    private val batchTaskScheduler: BatchTaskScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchRenameUiState())
    val uiState: StateFlow<BatchRenameUiState> = _uiState

    private val songsFlow = MutableStateFlow<List<SongForBatchRename>>(emptyList())
    private var observeJob: Job? = null

    val renameFormat = settingsRepository.renameFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDefaults.RENAME_FORMAT)

    init {
        _uiState.value = _uiState.value.copy(
            presetFormats = RenameEngine.getPresetFormats()
        )

        val selectedUris = selectionManager.selectedUris.value
        if (selectedUris.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val songList = selectedUris.mapNotNull { path ->
                    val songEntity = songLibraryRepository.getSongByUri(path)
                    songEntity?.let {
                        SongForBatchRename(
                            uri = it.uri,
                            filePath = it.filePath,
                            fileName = it.fileName,
                            tagData = convertToAudioTagData(it)
                        )
                    }
                }
                setSongs(songList)
            }
        }

        viewModelScope.launch {
            settingsRepository.characterMappingConfig.collect { config ->
                _uiState.update { it.copy(characterMappingConfig = config) }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            combine(
                songsFlow,
                renameFormat,
                settingsRepository.characterMappingConfig
            ) { songs, format, mapping ->
                Triple(songs, format, mapping)
            }.collectLatest { (songs, format, mapping) ->
                if (songs.isEmpty()) {
                    _uiState.update { it.copy(previews = emptyList()) }
                    return@collectLatest
                }

                _uiState.update { it.copy(isGeneratingPreview = true) }

                try {
                    val songsForRename = songs.mapNotNull { song ->
                        song.tagData?.let { tagData ->
                            RenameEngine.SongForRename(
                                originalPath = song.filePath,
                                tagData = tagData
                            )
                        }
                    }

                    if (songsForRename.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                previews = emptyList(),
                                isGeneratingPreview = false,
                                errorMessage = com.lonx.lyrico.utils.UiMessage.StringResource(com.lonx.lyrico.R.string.no_tag_data)
                            )
                        }
                        return@collectLatest
                    }

                    val request = RenameEngine.RenameRequest(
                        songs = songsForRename,
                        format = format,
                        characterMappingRules = mapping.rules
                    )

                    val previews = RenameEngine.generatePreviews(request)

                    _uiState.update {
                        it.copy(
                            previews = previews,
                            isGeneratingPreview = false
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isGeneratingPreview = false,
                            errorMessage = com.lonx.lyrico.utils.UiMessage.DynamicString(e.message)
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            val runningTask = batchTaskRepository.getRunningTaskByType(BatchTaskType.RENAME_FILES)
            if (runningTask != null) {
                resumeObservingTask(runningTask.taskId)
            }
        }
    }

    private fun resumeObservingTask(taskId: String) {
        observeJob?.cancel()
        _uiState.update {
            it.copy(
                currentTaskId = taskId,
                isRenamingInProgress = true,
                renameProgress = 0 to 0
            )
        }
        observeJob = viewModelScope.launch {
            batchTaskRepository.observeTask(taskId).collect { task ->
                if (task == null) return@collect
                _uiState.update {
                    it.copy(
                        renameProgress = task.current to task.total,
                        successCount = task.successCount,
                        failureCount = task.failureCount,
                        skippedCount = task.skippedCount,
                        currentFile = task.currentFile ?: "",
                        isRenamingInProgress = task.status == BatchTaskStatus.RUNNING || task.status == BatchTaskStatus.QUEUED
                    )
                }
                if (task.status == BatchTaskStatus.SUCCEEDED ||
                    task.status == BatchTaskStatus.FAILED ||
                    task.status == BatchTaskStatus.CANCELLED
                ) {
                    val duration = if (task.startedAt != null && task.finishedAt != null) {
                        task.finishedAt - task.startedAt
                    } else 0L
                    refreshSongsAfterRename(task.taskId)
                    _uiState.update {
                        it.copy(
                            isRenamingInProgress = false,
                            renameTimeMillis = duration
                        )
                    }
                    observeJob?.cancel()
                }
            }
        }
    }

    private suspend fun refreshSongsAfterRename(taskId: String) {
        data class RenameRefreshInfo(
            val originalUri: String?,
            val newUri: String?,
            val originalPath: String?,
            val newPath: String
        )

        val renameResults = batchTaskRepository.observeItems(taskId)
            .first()
            .filter { it.status == BatchTaskStatus.SUCCEEDED && it.resultJson != null }
            .mapNotNull { item ->
                val result = runCatching {
                    Json.decodeFromString(
                        RenameFilesTaskResult.serializer(),
                        item.resultJson.orEmpty()
                    )
                }.getOrNull()

                result?.let {
                    RenameRefreshInfo(
                        originalUri = it.originalUri ?: item.songUri,
                        newUri = it.newUri,
                        originalPath = it.originalPath ?: item.filePath,
                        newPath = it.newPath
                    )
                }
            }

        if (renameResults.isEmpty()) return

        val renamedByOriginalUri = renameResults
            .mapNotNull { result ->
                val originalUri = result.originalUri
                if (originalUri == null) null else originalUri to result
            }
            .toMap()

        val renamedByOriginalPath = renameResults
            .mapNotNull { result ->
                val originalPath = result.originalPath
                if (originalPath == null) null else originalPath to result
            }
            .toMap()

        val refreshedSongs = songsFlow.value.map { song ->
            val result = renamedByOriginalUri[song.uri]
                ?: renamedByOriginalPath[song.filePath]
                ?: return@map song
            val newName = File(result.newPath).name
            song.copy(
                uri = result.newUri ?: song.uri,
                filePath = result.newPath,
                fileName = newName,
                tagData = song.tagData?.copy(fileName = newName)
            )
        }

        setSongs(refreshedSongs)

        selectionManager.replaceUris(
            renamedByOriginalUri.mapValues { (_, result) -> result.newUri ?: result.originalUri.orEmpty() }
                .filterValues { it.isNotBlank() }
        )
    }

    private fun setSongs(songs: List<SongForBatchRename>) {
        songsFlow.value = songs
        _uiState.update {
            it.copy(
                songs = songs,
                errorMessage = null
            )
        }
    }

    fun saveFormat(format: String) {
        viewModelScope.launch {
            settingsRepository.saveRenameFormat(format)
        }
    }

    fun executeRename() {
        val selectedUris = selectionManager.selectedUris.value
        if (selectedUris.isEmpty()) return

        _uiState.update {
            it.copy(
                isRenamingInProgress = true,
                renameProgress = 0 to 0,
                renameTimeMillis = 0,
                successCount = 0,
                failureCount = 0,
                skippedCount = 0,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val songsToRename = selectedUris.mapNotNull { uri ->
                songLibraryRepository.getSongByUri(uri)
            }
            if (songsToRename.isEmpty()) {
                _uiState.update { it.copy(isRenamingInProgress = false) }
                return@launch
            }

            val currentFormat = renameFormat.value
            val currentMapping = settingsRepository.characterMappingConfig.first()

            val configJson = Json.encodeToString(
                RenameFilesTaskConfig.serializer(),
                RenameFilesTaskConfig(
                    renameFormat = currentFormat,
                    characterMappingRules = currentMapping.rules
                )
            )

            val taskId = batchTaskRepository.createTask(
                type = BatchTaskType.RENAME_FILES,
                songs = songsToRename,
                configJson = configJson
            )
            batchTaskScheduler.enqueue(taskId)
            resumeObservingTask(taskId)
        }
    }

    fun abortRename() {
        val taskId = _uiState.value.currentTaskId ?: return
        batchTaskScheduler.cancel(taskId)
        viewModelScope.launch {
            batchTaskRepository.markCancelled(taskId)
        }
    }

    fun closeRenameDialog() {
        _uiState.update {
            it.copy(
                renameProgress = null,
                currentFile = "",
                isRenamingInProgress = false,
                renameTimeMillis = 0,
                successCount = 0,
                failureCount = 0,
                skippedCount = 0
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun updateCharacterMappingInRule(
        ruleId: String,
        character: String,
        replacementChar: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentConfig = _uiState.value.characterMappingConfig ?: return@launch
            val rule = currentConfig.rules.find { it.id == ruleId } ?: return@launch
            val updatedMappings = rule.charMappings.toMutableMap()
            updatedMappings[character] = replacementChar ?: ""
            settingsRepository.updateCharacterMappingInRule(ruleId, updatedMappings)
        }
    }

    private fun convertToAudioTagData(song: SongEntity): AudioTagData {
        return AudioTagData(
            title = song.title,
            artist = song.artist,
            album = song.album,
            albumArtist = song.albumArtist,
            genre = song.genre,
            date = song.date,
            trackNumber = song.trackerNumber,
            discNumber = song.discNumber,
            composer = song.composer,
            lyricist = song.lyricist,
            comment = song.comment,
            lyrics = song.lyrics,
            copyright = song.copyright,
            rating = song.rating,
            replayGainTrackGain = song.replayGainTrackGain,
            replayGainTrackPeak = song.replayGainTrackPeak,
            replayGainAlbumGain = song.replayGainAlbumGain,
            replayGainAlbumPeak = song.replayGainAlbumPeak,
            replayGainReferenceLoudness = song.replayGainReferenceLoudness,
            fileName = song.fileName,
            durationMilliseconds = song.durationMilliseconds,
            bitrate = song.bitrate,
            sampleRate = song.sampleRate,
            channels = song.channels
        )
    }

}
