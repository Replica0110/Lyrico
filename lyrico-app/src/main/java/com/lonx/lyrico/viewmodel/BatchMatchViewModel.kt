package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.SharedSelectionManager
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.BatchMatchConfigDefaults
import com.lonx.lyrico.data.model.MetadataFieldWriteRule
import com.lonx.lyrico.data.model.MetadataFieldWriteRuleFactory
import com.lonx.lyrico.data.model.BatchTaskStatus
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.BatchTaskRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.worker.BatchTaskScheduler
import com.lonx.lyrico.worker.processor.MatchMetadataTaskConfig
import com.lonx.lyrico.data.model.lyrics.SourceRuntimeConfig
import com.lonx.lyrico.data.model.lyrics.SearchSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class BatchMatchUiState(
    val showBatchConfigDialog: Boolean = false,
    val isRunning: Boolean = false,
    val batchProgress: Pair<Int, Int>? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skippedCount: Int = 0,
    val currentFile: String = "",
    val batchTimeMillis: Long = 0,
    val currentTaskId: String? = null,
    val fileProgressMap: Map<String, Float> = emptyMap(),
)

class BatchMatchViewModel(
    private val settingsRepository: SettingsRepository,
    private val selectionManager: SharedSelectionManager,
    private val batchTaskRepository: BatchTaskRepository,
    private val batchTaskScheduler: BatchTaskScheduler,
    private val searchSourceProvider: SearchSourceProvider
) : ViewModel() {

    val batchMatchConfig: StateFlow<BatchMatchConfig> = settingsRepository.batchMatchConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, BatchMatchConfigDefaults.DEFAULT_CONFIG)

    private val metadataFieldWriteRules: StateFlow<List<MetadataFieldWriteRule>> =
        settingsRepository.metadataFieldWriteRules
            .combineWithDefaults()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val sourceSettings: StateFlow<Map<String, SourceRuntimeConfig>> =
        settingsRepository.sourceSettingsByIdFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    private val allSources: StateFlow<List<SearchSource>> =
        searchSourceProvider.observeAllSources()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val separator: StateFlow<String> = settingsRepository.separator
        .stateIn(viewModelScope, SharingStarted.Eagerly, "/")

    private val _uiState = MutableStateFlow(BatchMatchUiState())
    val uiState: StateFlow<BatchMatchUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            val runningTask = batchTaskRepository.getRunningTaskByType(BatchTaskType.MATCH_METADATA)
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
                isRunning = true,
                batchProgress = 0 to 0
            )
        }
        observeJob = viewModelScope.launch {
            combine(
                batchTaskRepository.observeTask(taskId),
                batchTaskRepository.observeItems(taskId)
            ) { task, items ->
                task to items
            }.collect { (task, items) ->
                if (task == null) return@collect
                val progressMap = items
                    .filter { it.status == BatchTaskStatus.RUNNING && it.progress != null }
                    .associate { it.fileName to it.progress!! }
                _uiState.update {
                    it.copy(
                        batchProgress = task.current to task.total,
                        successCount = task.successCount,
                        failureCount = task.failureCount,
                        skippedCount = task.skippedCount,
                        currentFile = task.currentFile ?: "",
                        isRunning = task.status == BatchTaskStatus.RUNNING || task.status == BatchTaskStatus.QUEUED,
                        fileProgressMap = progressMap
                    )
                }
                if (task.status == BatchTaskStatus.SUCCEEDED ||
                    task.status == BatchTaskStatus.FAILED ||
                    task.status == BatchTaskStatus.CANCELLED
                ) {
                    val duration = if (task.startedAt != null && task.finishedAt != null) {
                        task.finishedAt - task.startedAt
                    } else 0L
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            batchTimeMillis = duration,
                            fileProgressMap = emptyMap()
                        )
                    }
                    observeJob?.cancel()
                }
            }
        }
    }

    fun openBatchMatchConfig() {
        val selectedIds = selectionManager.selectedUris.value
        if (selectedIds.isNotEmpty()) {
            _uiState.update { it.copy(showBatchConfigDialog = true) }
        }
    }

    fun closeBatchMatchConfig() {
        _uiState.update { it.copy(showBatchConfigDialog = false) }
    }

    fun saveBatchMatchConfig(matchConfig: BatchMatchConfig) {
        viewModelScope.launch {
            settingsRepository.saveBatchMatchConfig(matchConfig)
        }
    }

    fun batchMatch(songs: List<SongEntity>, matchConfig: BatchMatchConfig) {
        val selectedIds = selectionManager.selectedUris.value
        if (selectedIds.isEmpty()) return

        closeBatchMatchConfig()

        _uiState.update {
            it.copy(
                isRunning = true,
                successCount = 0,
                failureCount = 0,
                skippedCount = 0,
                batchProgress = 0 to 0,
                batchTimeMillis = 0,
                fileProgressMap = emptyMap()
            )
        }

        viewModelScope.launch {
            val songsToMatch = songs.filter { it.uri in selectedIds }
            if (songsToMatch.isEmpty()) {
                _uiState.update { it.copy(isRunning = false) }
                return@launch
            }

            val currentOrderIds = buildEnabledSourceOrderIds()
            val configJson = Json.encodeToString(
                MatchMetadataTaskConfig.serializer(),
                MatchMetadataTaskConfig(
                    matchConfig = matchConfig,
                    separator = separator.value,
                    enabledSourceOrderIds = currentOrderIds,
                    metadataFieldWriteRules = metadataFieldWriteRules.value,
                    sourceSettings = sourceSettings.value.mapValues { it.value.values },
                    concurrency = matchConfig.concurrency
                )
            )
            val taskId = batchTaskRepository.createTask(
                type = BatchTaskType.MATCH_METADATA,
                songs = songsToMatch,
                configJson = configJson
            )
            batchTaskScheduler.enqueue(taskId)
            resumeObservingTask(taskId)
        }
    }

    fun abortBatchMatch() {
        val taskId = _uiState.value.currentTaskId
        if (taskId != null) {
            batchTaskScheduler.cancel(taskId)
            viewModelScope.launch {
                batchTaskRepository.markCancelled(taskId)
            }
        }
    }

    fun closeBatchMatchDialog() {
        _uiState.update {
            it.copy(
                batchProgress = null,
                currentFile = "",
                isRunning = false,
                batchTimeMillis = 0,
                fileProgressMap = emptyMap()
            )
        }
    }
    fun clearFinishedTasks() {
        _uiState.update {
            it.copy(
                batchProgress = null,
                currentFile = "",
                isRunning = false,
                batchTimeMillis = 0,
                fileProgressMap = emptyMap()
            )
        }
    }

    private fun kotlinx.coroutines.flow.Flow<List<MetadataFieldWriteRule>>.combineWithDefaults() =
        map { savedRules ->
            MetadataFieldWriteRuleFactory.mergeWithDeclaredFields(
                savedRules = savedRules,
                searchSources = allSources.value
            )
        }

    private fun buildEnabledSourceOrderIds(): List<String> {
        return allSources.value.map { it.id }
    }
}
