package com.lonx.lyrico.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedSelectionManager {

    private val _selectedUris = MutableStateFlow<Set<String>>(emptySet())
    val selectedUris: StateFlow<Set<String>> = _selectedUris.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private var swipeAnchorUri: String? = null

    fun setUris(uris: Set<String>) {
        _selectedUris.value = uris
        _isSelectionMode.value = uris.isNotEmpty()
        if (swipeAnchorUri !in uris) {
            swipeAnchorUri = null
        }
    }

    fun toggle(uri: String) {
        _isSelectionMode.value = true
        val selectedUris = if (_selectedUris.value.contains(uri)) {
            _selectedUris.value - uri
        } else {
            _selectedUris.value + uri
        }
        _selectedUris.value = selectedUris
        _isSelectionMode.value = selectedUris.isNotEmpty()
        if (swipeAnchorUri !in selectedUris) {
            swipeAnchorUri = null
        }
    }

    fun selectAll(uris: Set<String>) {
        setUris(uris)
    }

    fun deselectAll() {
        _selectedUris.value = emptySet()
        swipeAnchorUri = null
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedUris.value = emptySet()
        swipeAnchorUri = null
    }

    fun selectSwipeRange(uri: String, visibleUris: List<String>) {
        val selectedUris = _selectedUris.value

        if (!_isSelectionMode.value) {
            _isSelectionMode.value = true
            _selectedUris.value = selectedUris + uri
            swipeAnchorUri = uri
            return
        }

        if (uri in selectedUris) {
            _selectedUris.value = selectedUris + uri
            swipeAnchorUri = uri
            return
        }

        val anchorUri = swipeAnchorUri
        if (anchorUri == null || anchorUri !in selectedUris) {
            _selectedUris.value = selectedUris + uri
            swipeAnchorUri = uri
            return
        }

        val anchorIndex = visibleUris.indexOf(anchorUri)
        val uriIndex = visibleUris.indexOf(uri)
        if (anchorIndex == -1 || uriIndex == -1) {
            _selectedUris.value = selectedUris + uri
            swipeAnchorUri = uri
            return
        }

        val start = minOf(anchorIndex, uriIndex)
        val end = maxOf(anchorIndex, uriIndex)
        _selectedUris.value = selectedUris + visibleUris.subList(start, end + 1)
        _isSelectionMode.value = true
        swipeAnchorUri = null
    }

    fun replaceUris(uriMapping: Map<String, String>) {
        if (uriMapping.isEmpty()) return
        _selectedUris.value = _selectedUris.value.map { uri ->
            uriMapping[uri] ?: uri
        }.toSet()
        swipeAnchorUri = swipeAnchorUri?.let { uriMapping[it] ?: it }
    }

    fun clearAll() {
        exitSelectionMode()
    }
}
