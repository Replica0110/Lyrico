package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.repository.CustomTagKeyRepository
import com.lonx.lyrico.data.repository.CustomTagSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomTagManagementUiState(
    val visibleKeys: List<String> = emptyList(),
    val keyCounts: Map<String, Int> = emptyMap(),
    val searchQuery: String = "",
    val inputError: String? = null,
)

class CustomTagManagementViewModel(
    private val customTagSettingsRepository: CustomTagSettingsRepository,
    private val customTagKeyRepository: CustomTagKeyRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val inputError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CustomTagManagementUiState> =
        combine(
            customTagSettingsRepository.settingsFlow,
            customTagKeyRepository.observeKeyCounts(),
            searchQuery,
            inputError,
        ) { settings, counts, query, error ->
            CustomTagManagementUiState(
                visibleKeys = settings.visibleKeys,
                keyCounts = counts.associate { it.key to it.songCount },
                searchQuery = query,
                inputError = error,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CustomTagManagementUiState(),
        )

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun addVisibleKey(input: String) {
        viewModelScope.launch {
            runCatching {
                customTagSettingsRepository.addVisibleKey(input)
            }.onSuccess {
                inputError.value = null
            }.onFailure {
                inputError.value = it.message
            }
        }
    }

    fun removeVisibleKey(key: String) {
        viewModelScope.launch {
            customTagSettingsRepository.removeVisibleKey(key)
        }
    }

    fun setVisibleKeys(keys: List<String>) {
        viewModelScope.launch {
            customTagSettingsRepository.setVisibleKeys(keys)
        }
    }

    fun clearInputError() {
        inputError.update { null }
    }
}
