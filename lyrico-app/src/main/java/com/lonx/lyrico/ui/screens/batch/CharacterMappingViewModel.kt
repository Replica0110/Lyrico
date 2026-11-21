package com.lonx.lyrico.ui.screens.batch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.rename.CharacterMappingConfig
import com.lonx.lyrico.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CharacterMappingViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _characterMappingConfig = MutableStateFlow<CharacterMappingConfig?>(null)
    val characterMappingConfig: StateFlow<CharacterMappingConfig?> = _characterMappingConfig

    init {
        viewModelScope.launch {
            settingsRepository.characterMappingConfig.collect { config ->
                _characterMappingConfig.value = config
            }
        }
    }

    fun updateCharacterMappingInRule(
        ruleId: String,
        character: String,
        replacementChar: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentConfig = _characterMappingConfig.value ?: return@launch
            val rule = currentConfig.rules.find { it.id == ruleId } ?: return@launch
            val updatedMappings = rule.charMappings.toMutableMap()
            updatedMappings[character] = replacementChar ?: ""
            settingsRepository.updateCharacterMappingInRule(ruleId, updatedMappings)
        }
    }
}
