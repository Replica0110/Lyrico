package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.editfield.CustomTagKey
import com.lonx.lyrico.data.editfield.EditFieldConfigRepository
import com.lonx.lyrico.data.editfield.EditFieldListItem
import com.lonx.lyrico.data.model.dao.CustomTagKeyCount
import com.lonx.lyrico.data.repository.CustomTagKeyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CustomTagKeyError { EMPTY, INVALID, DUPLICATE }

data class EditFieldSettingsUiState(
    val items: List<EditFieldListItem> = emptyList(),
    val enabledCount: Int = 0,
    val componentOverrides: Map<String, Boolean> = emptyMap(),
    val availableKeys: List<String> = emptyList(),
    val inputError: CustomTagKeyError? = null,
)

class EditFieldSettingsViewModel(
    private val configRepository: EditFieldConfigRepository,
    private val customTagKeyRepository: CustomTagKeyRepository,
) : ViewModel() {
    private val inputError = MutableStateFlow<CustomTagKeyError?>(null)

    val uiState = combine(
        configRepository.configFlow, customTagKeyRepository.observeKeyCounts(), inputError,
    ) { config, counts, error ->
        val items = config.fieldItems()
        EditFieldSettingsUiState(
            items = items,
            enabledCount = config.allFields.count { config.isEffectivelyEnabled(it) },
            componentOverrides = config.componentOverrides,
            availableKeys = counts.filterNot { it.key in config.customTags }
                .sortedWith(compareByDescending<CustomTagKeyCount> { it.songCount }.thenBy { it.key })
                .map { it.key },
            inputError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditFieldSettingsUiState())

    fun setEnabled(code: String, enabled: Boolean) {
        viewModelScope.launch { configRepository.setEnabled(code, enabled) }
    }

    fun setComponentEnabled(key: String, enabled: Boolean) {
        viewModelScope.launch { configRepository.setComponentEnabled(key, enabled) }
    }

    fun setBlockOrder(keys: List<String>) {
        viewModelScope.launch { configRepository.setBlockOrder(keys) }
    }

    fun setComponentOrder(key: String, codes: List<String>) {
        viewModelScope.launch { configRepository.setComponentOrder(key, codes) }
    }

    suspend fun addCustomTag(input: String): Boolean {
        val normalized = CustomTagKey.normalize(input)
        if (normalized == null) {
            inputError.value = if (input.isBlank()) CustomTagKeyError.EMPTY else CustomTagKeyError.INVALID
            return false
        }
        if (normalized in configRepository.configFlow.first().customTags) {
            inputError.value = CustomTagKeyError.DUPLICATE
            return false
        }
        inputError.value = null
        configRepository.addCustomTag(normalized)
        return true
    }

    fun removeCustomTag(key: String) {
        viewModelScope.launch { configRepository.removeCustomTag(key) }
    }

    fun resetAll() {
        viewModelScope.launch { configRepository.resetAll() }
    }

    fun clearInputError() { inputError.value = null }
}
