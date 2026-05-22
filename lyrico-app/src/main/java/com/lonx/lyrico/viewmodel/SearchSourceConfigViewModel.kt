package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.MetadataFieldWriteRule
import com.lonx.lyrico.data.model.MetadataFieldWriteRuleFactory
import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginMetadataField
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
    val configFields: List<PluginConfigField> = emptyList(),
    val metadataFields: List<PluginMetadataField> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val metadataRules: List<MetadataFieldWriteRule> = emptyList(),
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

    private var allMetadataRules: List<MetadataFieldWriteRule> = emptyList()

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
            val defaults = fields.associate { it.key to it.defaultValue }
            val saved = settingsRepository.getSourceSettings(sourceImpl.id).values
            allMetadataRules = MetadataFieldWriteRuleFactory.mergeWithDeclaredFields(
                savedRules = settingsRepository.getMetadataFieldWriteRules(),
                searchSources = allSources
            )

            _uiState.update {
                it.copy(
                    pluginId = sourceImpl.id,
                    title = sourceImpl.name,
                    configFields = fields,
                    metadataFields = sourceImpl.metadataFields.filter { field ->
                        field.writeable && !field.internal
                    },
                    values = defaults + saved,
                    metadataRules = allMetadataRules.filter { rule -> rule.pluginId == sourceImpl.id },
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

    fun updateMetadataRule(rule: MetadataFieldWriteRule) {
        _uiState.update {
            it.copy(
                metadataRules = it.metadataRules.map { old ->
                    if (old.pluginId == rule.pluginId && old.normalizedKey == rule.normalizedKey) rule else old
                },
                saved = false
            )
        }
    }

    fun updateMetadataRuleTarget(rule: MetadataFieldWriteRule, target: com.lonx.lyrico.data.model.MetadataFieldTarget) {
        updateMetadataRule(rule.copy(target = target))
    }

    fun updateMetadataRuleMode(rule: MetadataFieldWriteRule, mode: com.lonx.lyrico.data.model.MetadataWriteMode) {
        updateMetadataRule(rule.copy(mode = mode))
    }

    fun updateMetadataRuleCustomTagKey(rule: MetadataFieldWriteRule, customTagKey: String) {
        updateMetadataRule(rule.copy(customTagKey = customTagKey.takeIf { it.isNotBlank() }))
    }

    fun consumeSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    fun save(requiredMessage: String = "必填") {
        val state = _uiState.value
        val pluginId = state.pluginId.takeIf { it.isNotBlank() } ?: return
        val visibleFields = state.configFields.filter { it.dependency.isSatisfied(state.values) }
        val errors = visibleFields
            .filter { it.required && state.values[it.key].isNullOrBlank() }
            .associate { it.key to requiredMessage }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(validationErrors = errors, saved = false) }
            return
        }

        viewModelScope.launch {
            settingsRepository.saveSourceSettings(pluginId, state.values)
            val updatedRules = allMetadataRules.map { old ->
                state.metadataRules.firstOrNull {
                    it.pluginId == old.pluginId && it.normalizedKey == old.normalizedKey
                } ?: old
            }
            settingsRepository.saveMetadataFieldWriteRules(updatedRules)
            allMetadataRules = settingsRepository.getMetadataFieldWriteRules()
            _uiState.update { it.copy(saved = true, validationErrors = emptyMap()) }
        }
    }

    private fun findSource(pluginId: String, sources: List<SearchSource>): SearchSource? {
        return sources.firstOrNull { searchSource -> searchSource.id == pluginId }
    }
}
