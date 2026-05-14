package com.lonx.lyrico.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.R
import com.lonx.lyrico.data.SharedSelectionManager
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.data.repository.PlaybackRepository
import com.lonx.lyrico.data.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SongSelectionViewModel(
    private val songRepository: SongRepository,
    private val playbackRepository: PlaybackRepository,
    private val selectionManager: SharedSelectionManager
) : ViewModel() {

    private val _selectedSongUris = MutableStateFlow<Set<String>>(emptySet())
    val selectedSongUris = _selectedSongUris.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    fun toggleSelection(uri: String) {
        if (!_isSelectionMode.value) _isSelectionMode.value = true
        _selectedSongUris.update { selected ->
            if (selected.contains(uri)) selected - uri else selected + uri
        }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedSongUris.value = emptySet()
    }

    fun deselectAll() {
        _selectedSongUris.value = emptySet()
    }

    fun selectAll(songs: List<SongEntity>) {
        _selectedSongUris.value = songs.map { it.uri }.toSet()
    }

    fun setSelectionUris(): Boolean {
        val selectedUris = _selectedSongUris.value
        if (selectedUris.isEmpty()) return false

        selectionManager.setUris(selectedUris)
        return true
    }

    fun play(context: Context, song: SongEntity) {
        playbackRepository.play(context, song.getUri)
    }

    fun delete(song: SongEntity) {
        viewModelScope.launch {
            songRepository.deleteSong(song)
        }
    }

    fun batchDelete(songs: List<SongEntity>) {
        val selectedUris = _selectedSongUris.value
        val toDelete = songs.filter { it.uri in selectedUris }

        viewModelScope.launch {
            songRepository.deleteSongs(toDelete)
            exitSelectionMode()
        }
    }

    fun batchShare(context: Context, songs: List<SongEntity>) {
        val selectedUris = _selectedSongUris.value
        val toShare = songs.filter { it.uri in selectedUris }
        if (toShare.isEmpty()) return

        val uris = toShare.map { it.uri.toUri() }.toCollection(ArrayList())

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(
                intent,
                context.getString(R.string.share_chooser_title)
            )
        )
    }

    fun renameSong(song: SongEntity, newFileName: String) {
        viewModelScope.launch {
            songRepository.renameSong(song, newFileName)
        }
    }
}
