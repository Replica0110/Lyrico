package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginConfigFieldType
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import com.lonx.lyrico.data.model.plugin.displaySourceTypes
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.utils.isSatisfied
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchSourceConfigUiState(
    val pluginId: String = "",
    val title: String = "",
    val apiVersion: Int = 1,
    val minHostApiVersion: Int = 1,
    val sourceTypes: Set<PluginSourceType> = emptySet(),
    val configFields: List<PluginConfigField> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val pluginEnabled: Boolean = true,
    val validationErrors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val saved: Boolean = false,
    val errorMessage: String? = null
)

class SearchSourceConfigViewModel(
    private val settingsRepository: SettingsRepository,
    private val searchSourceProvider: SearchSourceProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchSourceConfigUiState())
    val uiState: StateFlow<SearchSourceConfigUiState> = _uiState.asStateFlow()

    fun load(pluginId: String) {
        viewModelScope.launch {
            val sourceWithState = searchSourceProvider.getSourceWithState(pluginId)
            if (sourceWithState == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "无效的搜索源")
                }
                return@launch
            }
            val sourceImpl = sourceWithState.source
            val fields = sourceImpl.configFields
            val valueFields = fields.filter { it.type != PluginConfigFieldType.MARKDOWN }
            val defaults = valueFields.associate { it.key to it.defaultValue }
            val saved = settingsRepository.getSourceSettings(sourceImpl.id).values

            _uiState.update {
                it.copy(
                    pluginId = sourceImpl.id,
                    title = sourceImpl.name,
                    apiVersion = sourceImpl.apiVersion,
                    minHostApiVersion = sourceImpl.minHostApiVersion,
                    sourceTypes = sourceImpl.capabilities.displaySourceTypes(),
                    configFields = fields,
                    values = defaults + saved,
                    pluginEnabled = sourceWithState.enabled,
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
            _uiState.update { it.copy(saved = true, validationErrors = emptyMap()) }
        }
    }
}
