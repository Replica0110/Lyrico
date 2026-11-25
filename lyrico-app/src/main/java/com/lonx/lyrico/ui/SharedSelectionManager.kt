package com.lonx.lyrico.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedSelectionManager {

    private val _selectedUris = MutableStateFlow<Set<String>>(emptySet())
    val selectedUris: StateFlow<Set<String>> = _selectedUris.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _swipeAnchorUri = MutableStateFlow<String?>(null)
    val swipeAnchorUri: StateFlow<String?> = _swipeAnchorUri.asStateFlow()

    private var swipeAnchorUriValue: String?
        get() = _swipeAnchorUri.value
        set(value) {
            _swipeAnchorUri.value = value
        }

    fun setUris(uris: Set<String>) {
        _selectedUris.value = uris
        if (uris.isNotEmpty()) {
            _isSelectionMode.value = true
        }
        if (swipeAnchorUriValue !in uris) {
            swipeAnchorUriValue = null
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
        if (swipeAnchorUriValue !in selectedUris) {
            swipeAnchorUriValue = null
        }
    }

    fun selectAll(uris: Set<String>) {
        setUris(uris)
    }

    fun deselectAll() {
        _selectedUris.value = emptySet()
        swipeAnchorUriValue = null
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedUris.value = emptySet()
        swipeAnchorUriValue = null
    }

    fun selectSwipeRange(uri: String, visibleUris: List<String>) {
        val selectedUris = _selectedUris.value

        if (!_isSelectionMode.value) {
            _isSelectionMode.value = true
            _selectedUris.value = selectedUris + uri
            swipeAnchorUriValue = uri
            return
        }

        if (uri in selectedUris) {
            _selectedUris.value = selectedUris + uri
            swipeAnchorUriValue = uri
            return
        }

        val anchorUri = swipeAnchorUriValue
        if (anchorUri == null || anchorUri !in selectedUris) {
            _selectedUris.value = selectedUris + uri
            swipeAnchorUriValue = uri
            return
        }

        val anchorIndex = visibleUris.indexOf(anchorUri)
        val uriIndex = visibleUris.indexOf(uri)
        if (anchorIndex == -1 || uriIndex == -1) {
            _selectedUris.value = selectedUris + uri
            swipeAnchorUriValue = uri
            return
        }

        val start = minOf(anchorIndex, uriIndex)
        val end = maxOf(anchorIndex, uriIndex)
        _selectedUris.value = selectedUris + visibleUris.subList(start, end + 1)
        _isSelectionMode.value = true
        swipeAnchorUriValue = null
    }

    fun replaceUris(uriMapping: Map<String, String>) {
        if (uriMapping.isEmpty()) return
        _selectedUris.value = _selectedUris.value.map { uri ->
            uriMapping[uri] ?: uri
        }.toSet()
        swipeAnchorUriValue = swipeAnchorUriValue?.let { uriMapping[it] ?: it }
    }

    fun clearAll() {
        exitSelectionMode()
    }
}
