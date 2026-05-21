package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.ExtraMetadataWriteRuleFactory
import com.lonx.lyrico.data.model.ExtraMetadataWriteRule
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.utils.isSatisfied
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.model.SourceConfigField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchSourceConfigUiState(
    val source: Source? = null,
    val title: String = "",
    val fields: List<SourceConfigField> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val extraRules: List<ExtraMetadataWriteRule> = emptyList(),
    val validationErrors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val saved: Boolean = false,
    val errorMessage: String? = null
)

class SearchSourceConfigViewModel(
    private val settingsRepository: SettingsRepository,
    private val searchSources: List<SearchSource>
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchSourceConfigUiState())
    val uiState: StateFlow<SearchSourceConfigUiState> = _uiState.asStateFlow()

    private var allExtraRules: List<ExtraMetadataWriteRule> = emptyList()

    fun load(sourceName: String) {
        viewModelScope.launch {
            val source = Source.fromNameOrNull(sourceName)
            val sourceImpl = source?.let(::findSource)
            if (source == null || sourceImpl == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "无效的搜索源")
                }
                return@launch
            }

            val fields = sourceImpl.getConfigFields()
            val defaults = fields.associate { it.key to it.defaultValue }
            val saved = settingsRepository.getSourceSettings(source).values
            allExtraRules = ExtraMetadataWriteRuleFactory.mergeWithDeclaredFields(
                savedRules = settingsRepository.getExtraMetadataWriteRules(),
                searchSources = searchSources
            )
            val writeableExtraKeys = sourceImpl.extraFields
                .filter { it.writeable }
                .mapTo(mutableSetOf()) { it.key }
            val extraRules = allExtraRules.filter {
                it.source == source && it.normalizedKey in writeableExtraKeys
            }

            _uiState.update {
                it.copy(
                    source = source,
                    title = sourceImpl.sourceType.name,
                    fields = fields,
                    values = defaults + saved,
                    extraRules = extraRules,
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

    fun updateExtraRule(rule: ExtraMetadataWriteRule) {
        _uiState.update {
            it.copy(
                extraRules = it.extraRules.map { old ->
                    if (old.source == rule.source && old.normalizedKey == rule.normalizedKey) rule else old
                },
                saved = false
            )
        }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    fun save(requiredMessage: String = "必填") {
        val state = _uiState.value
        val source = state.source ?: return
        val visibleFields = state.fields.filter { it.dependency.isSatisfied(state.values) }
        val errors = visibleFields
            .filter { it.required && state.values[it.key].isNullOrBlank() }
            .associate { it.key to requiredMessage }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(validationErrors = errors, saved = false) }
            return
        }

        viewModelScope.launch {
            settingsRepository.saveSourceSettings(source, state.values)
            val updatedRules = allExtraRules.map { old ->
                state.extraRules.firstOrNull {
                    it.source == old.source && it.normalizedKey == old.normalizedKey
                } ?: old
            }
            settingsRepository.saveExtraMetadataWriteRules(updatedRules)
            allExtraRules = settingsRepository.getExtraMetadataWriteRules()
            _uiState.update { it.copy(saved = true, validationErrors = emptyMap()) }
        }
    }

    private fun findSource(source: Source): SearchSource? {
        return searchSources.firstOrNull { it.sourceType == source }
    }
}
