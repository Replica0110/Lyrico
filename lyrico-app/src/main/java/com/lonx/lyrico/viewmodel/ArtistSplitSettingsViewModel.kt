package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.data.model.artist.ArtistSplitDefaults
import com.lonx.lyrico.data.model.artist.CustomArtistSeparator
import com.lonx.lyrico.data.model.artist.CustomNoSplitArtist
import com.lonx.lyrico.data.model.artist.normalizedArtistKey
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ArtistSplitSettingsUiState(
    val config: ArtistSplitConfig = ArtistSplitConfig(),
    val isRebuildingIndex: Boolean = false,
    val hasPendingIndexRebuild: Boolean = false,
    val error: ArtistSplitValidationError? = null,
    val rebuildCompleted: Boolean = false
)

enum class ArtistSplitValidationError {
    EMPTY,
    DUPLICATE,
    DUPLICATE_BUILTIN,
    REBUILD_FAILED
}

class ArtistSplitSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val libraryIndexRepository: LibraryIndexRepository
) : ViewModel() {
    private val hasPendingIndexRebuild = MutableStateFlow(false)
    private val isRebuildingIndex = MutableStateFlow(false)
    private val error = MutableStateFlow<ArtistSplitValidationError?>(null)
    private val rebuildCompleted = MutableStateFlow(false)

    val uiState: StateFlow<ArtistSplitSettingsUiState> =
        combine(
            settingsRepository.artistSplitConfigFlow,
            hasPendingIndexRebuild,
            isRebuildingIndex,
            error,
            rebuildCompleted
        ) { config, pending, rebuilding, currentError, completed ->
            ArtistSplitSettingsUiState(
                config = config,
                hasPendingIndexRebuild = pending,
                isRebuildingIndex = rebuilding,
                error = currentError,
                rebuildCompleted = completed
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ArtistSplitSettingsUiState()
        )

    fun clearError() {
        error.value = null
    }

    fun clearRebuildCompleted() {
        rebuildCompleted.value = false
    }

    fun setEnabled(enabled: Boolean) {
        updateConfig { it.copy(enabled = enabled) }
    }

    fun setBuiltinSeparatorEnabled(id: String, enabled: Boolean) {
        updateConfig { config ->
            config.copy(
                builtinSeparatorOverrides = config.builtinSeparatorOverrides + (id to enabled)
            )
        }
    }

    fun removeBuiltinSeparator(id: String) {
        updateConfig { config ->
            config.copy(
                hiddenBuiltinSeparatorIds = config.hiddenBuiltinSeparatorIds + id
            )
        }
    }

    fun resetSeparators() {
        updateConfig { config ->
            config.copy(
                builtinSeparatorOverrides = emptyMap(),
                hiddenBuiltinSeparatorIds = emptySet(),
                customSeparators = emptyList()
            )
        }
    }

    fun addCustomSeparator(value: String): Boolean {
        val config = uiState.value.config
        val validationError = validateCustomSeparatorInput(value, config)
        if (validationError != null) {
            error.value = validationError
            return false
        }
        updateConfig { current ->
            current.copy(
                customSeparators = current.customSeparators + CustomArtistSeparator(value = value)
            )
        }
        return true
    }

    fun updateCustomSeparator(id: String, value: String): Boolean {
        val config = uiState.value.config
        val validationError = validateCustomSeparatorInput(value, config, id)
        if (validationError != null) {
            error.value = validationError
            return false
        }
        updateConfig { current ->
            current.copy(
                customSeparators = current.customSeparators.map {
                    if (it.id == id) it.copy(value = value) else it
                }
            )
        }
        return true
    }

    fun setCustomSeparatorEnabled(id: String, enabled: Boolean) {
        updateConfig { config ->
            config.copy(
                customSeparators = config.customSeparators.map {
                    if (it.id == id) it.copy(enabled = enabled) else it
                }
            )
        }
    }

    fun removeCustomSeparator(id: String) {
        updateConfig { config ->
            config.copy(customSeparators = config.customSeparators.filterNot { it.id == id })
        }
    }

    fun addCustomNoSplitArtist(name: String): Boolean {
        val config = uiState.value.config
        val validationError = validateNoSplitArtistInput(name, config)
        if (validationError != null) {
            error.value = validationError
            return false
        }
        updateConfig { current ->
            current.copy(
                customNoSplitArtists = current.customNoSplitArtists + CustomNoSplitArtist(name = name)
            )
        }
        return true
    }

    fun updateCustomNoSplitArtist(id: String, name: String): Boolean {
        val config = uiState.value.config
        val validationError = validateNoSplitArtistInput(name, config, id)
        if (validationError != null) {
            error.value = validationError
            return false
        }
        updateConfig { current ->
            current.copy(
                customNoSplitArtists = current.customNoSplitArtists.map {
                    if (it.id == id) it.copy(name = name) else it
                }
            )
        }
        return true
    }

    fun setCustomNoSplitArtistEnabled(id: String, enabled: Boolean) {
        updateConfig { config ->
            config.copy(
                customNoSplitArtists = config.customNoSplitArtists.map {
                    if (it.id == id) it.copy(enabled = enabled) else it
                }
            )
        }
    }

    fun removeCustomNoSplitArtist(id: String) {
        updateConfig { config ->
            config.copy(customNoSplitArtists = config.customNoSplitArtists.filterNot { it.id == id })
        }
    }

    fun rebuildArtistIndex() {
        viewModelScope.launch {
            isRebuildingIndex.value = true
            rebuildCompleted.value = false
            runCatching {
                libraryIndexRepository.rebuildArtistIndex()
            }.onSuccess {
                hasPendingIndexRebuild.value = false
                rebuildCompleted.value = true
            }.onFailure {
                error.value = ArtistSplitValidationError.REBUILD_FAILED
            }
            isRebuildingIndex.value = false
        }
    }

    private fun updateConfig(transform: (ArtistSplitConfig) -> ArtistSplitConfig) {
        viewModelScope.launch {
            val current = settingsRepository.getArtistSplitConfig()
            settingsRepository.saveArtistSplitConfig(transform(current))
            hasPendingIndexRebuild.value = true
            rebuildCompleted.value = false
        }
    }

    private fun validateCustomSeparatorInput(
        input: String,
        config: ArtistSplitConfig,
        editingId: String? = null
    ): ArtistSplitValidationError? {
        if (input.isBlank()) return ArtistSplitValidationError.EMPTY
        val key = input.trim()
        if (config.customSeparators.any { it.id != editingId && it.value.trim() == key }) {
            return ArtistSplitValidationError.DUPLICATE
        }
        val duplicateVisibleBuiltin = ArtistSplitDefaults.BUILTIN_SEPARATORS.any {
            it.id !in config.hiddenBuiltinSeparatorIds &&
                (config.builtinSeparatorOverrides[it.id] ?: it.defaultEnabled) &&
                it.value.trim() == key
        }
        if (duplicateVisibleBuiltin) {
            return ArtistSplitValidationError.DUPLICATE
        }
        return null
    }

    private fun validateNoSplitArtistInput(
        input: String,
        config: ArtistSplitConfig,
        editingId: String? = null
    ): ArtistSplitValidationError? {
        if (input.isBlank()) return ArtistSplitValidationError.EMPTY
        val key = input.normalizedArtistKey()
        if (config.customNoSplitArtists.any { it.id != editingId && it.name.normalizedArtistKey() == key }) {
            return ArtistSplitValidationError.DUPLICATE
        }
        return null
    }
}
