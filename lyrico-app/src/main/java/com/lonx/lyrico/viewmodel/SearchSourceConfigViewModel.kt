package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.plugin.PluginCapability
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginConfigFieldType
import com.lonx.lyrico.data.model.plugin.PluginFieldProcessConfig
import com.lonx.lyrico.data.model.plugin.PluginMetadataField
import com.lonx.lyrico.data.repository.PluginFieldProcessConfigRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.utils.isSatisfied
import com.lonx.lyrico.data.model.lyrics.SearchSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchSourceConfigUiState(
    val pluginId: String = "",
    val title: String = "",
    val capabilities: Set<PluginCapability> = emptySet(),
    val configFields: List<PluginConfigField> = emptyList(),
    val fieldProcessFields: List<PluginMetadataField> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val fieldProcessConfig: PluginFieldProcessConfig? = null,
    val validationErrors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val saved: Boolean = false,
    val errorMessage: String? = null
)

class SearchSourceConfigViewModel(
    private val settingsRepository: SettingsRepository,
    private val pluginFieldProcessConfigRepository: PluginFieldProcessConfigRepository,
    private val searchSourceProvider: SearchSourceProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchSourceConfigUiState())
    val uiState: StateFlow<SearchSourceConfigUiState> = _uiState.asStateFlow()

    fun load(pluginId: String) {
        viewModelScope.launch {
            val allSources = searchSourceProvider.getAllSources()
            val sourceImpl = findSource(pluginId, allSources)
            if (sourceImpl == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "无效的搜索源")
                }
                return@launch
            }
            val fields = sourceImpl.configFields
            val valueFields = fields.filter { it.type != PluginConfigFieldType.MARKDOWN }
            val defaults = valueFields.associate { it.key to it.defaultValue }
            val saved = settingsRepository.getSourceSettings(sourceImpl.id).values
            val fieldProcessConfig = pluginFieldProcessConfigRepository.getConfig(sourceImpl.id)
            val fieldProcessFields = if (PluginCapability.GET_LYRICS in sourceImpl.capabilities) {
                listOf(
                    PluginMetadataField(
                        key = "lyrics",
                        title = "歌词",
                        defaultTarget = MetadataFieldTarget.LYRICS
                    )
                )
            } else {
                emptyList()
            }

            _uiState.update {
                it.copy(
                    pluginId = sourceImpl.id,
                    title = sourceImpl.name,
                    capabilities = sourceImpl.capabilities,
                    configFields = fields,
                    fieldProcessFields = fieldProcessFields,
                    values = defaults + saved,
                    fieldProcessConfig = fieldProcessConfig,
                    validationErrors = emptyMap(),
                    isLoading = false,
                    saved = false,
                    errorMessage = null
                )
            }
        }
    }

    fun updateValue(key: String, value: String) {
        _uiState.update {
            it.copy(
                values = it.values + (key to value),
                validationErrors = it.validationErrors - key,
                saved = false
            )
        }
    }

    fun updateFieldProcessConfig(config: PluginFieldProcessConfig) {
        _uiState.update {
            it.copy(
                fieldProcessConfig = config.copy(
                    pluginId = config.pluginId.ifBlank { it.pluginId }
                ),
                saved = false
            )
        }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    fun save(requiredMessage: String = "必填") {
        val state = _uiState.value
        val pluginId = state.pluginId.takeIf { it.isNotBlank() } ?: return
        val valueFieldKeys = state.configFields
            .filter { it.type != PluginConfigFieldType.MARKDOWN }
            .mapTo(mutableSetOf()) { it.key }
        val visibleFields = state.configFields.filter {
            it.type != PluginConfigFieldType.MARKDOWN && it.dependency.isSatisfied(state.values)
        }
        val errors = visibleFields
            .filter { it.required && state.values[it.key].isNullOrBlank() }
            .associate { it.key to requiredMessage }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(validationErrors = errors, saved = false) }
            return
        }

        viewModelScope.launch {
            settingsRepository.saveSourceSettings(
                pluginId,
                state.values.filterKeys { it in valueFieldKeys }
            )
            state.fieldProcessConfig?.let { config ->
                pluginFieldProcessConfigRepository.updateConfig(
                    config.copy(pluginId = config.pluginId.ifBlank { pluginId })
                )
            }
            _uiState.update { it.copy(saved = true, validationErrors = emptyMap()) }
        }
    }

    private fun findSource(pluginId: String, sources: List<SearchSource>): SearchSource? {
        return sources.firstOrNull { searchSource -> searchSource.id == pluginId }
    }
}
