package com.lonx.lyrico.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedSelectionManager {

    private val _selectedUris = MutableStateFlow<Set<String>>(emptySet())
    val selectedUris: StateFlow<Set<String>> = _selectedUris.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    fun setUris(uris: Set<String>) {
        _selectedUris.value = uris
        _isSelectionMode.value = uris.isNotEmpty()
    }

    fun toggle(uri: String) {
        _isSelectionMode.value = true
        _selectedUris.value = if (_selectedUris.value.contains(uri)) {
            _selectedUris.value - uri
        } else {
            _selectedUris.value + uri
        }
    }

    fun selectAll(uris: Set<String>) {
        setUris(uris)
    }

    fun deselectAll() {
        _selectedUris.value = emptySet()
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedUris.value = emptySet()
    }

    fun replaceUris(uriMapping: Map<String, String>) {
        if (uriMapping.isEmpty()) return
        _selectedUris.value = _selectedUris.value.map { uri ->
            uriMapping[uri] ?: uri
        }.toSet()
    }

    fun clearAll() {
        exitSelectionMode()
    }
}
