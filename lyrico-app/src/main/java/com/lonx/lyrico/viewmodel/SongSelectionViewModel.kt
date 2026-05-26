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
import kotlinx.coroutines.launch

class SongSelectionViewModel(
    private val songRepository: SongRepository,
    private val playbackRepository: PlaybackRepository,
    private val selectionManager: SharedSelectionManager
) : ViewModel() {

    val selectedSongUris = selectionManager.selectedUris
    val isSelectionMode = selectionManager.isSelectionMode
    private var preDragSelectedUris = emptySet<String>()

    fun toggleSelection(uri: String) {
        selectionManager.toggle(uri)
    }

    fun exitSelectionMode() {
        selectionManager.exitSelectionMode()
    }

    fun deselectAll() {
        selectionManager.deselectAll()
    }

    fun selectAll(songs: List<SongEntity>) {
        selectionManager.selectAll(songs.map { it.uri }.toSet())
    }

    fun setSelectionUris(): Boolean {
        val selectedUris = selectedSongUris.value
        if (selectedUris.isEmpty()) return false

        selectionManager.setUris(selectedUris)
        return true
    }

    fun startDragSelection(index: Int, songs: List<SongEntity>) {
        val song = songs.getOrNull(index) ?: return
        preDragSelectedUris = selectedSongUris.value

        val rangeUris = setOf(song.uri)
        selectionManager.setUris((preDragSelectedUris - rangeUris) + (rangeUris - preDragSelectedUris))
    }

    fun updateDragSelection(startIndex: Int, endIndex: Int, songs: List<SongEntity>) {
        val start = minOf(startIndex, endIndex).coerceAtLeast(0)
        val end = maxOf(startIndex, endIndex).coerceAtMost(songs.size - 1)
        if (start > end) return

        val rangeUris = songs.subList(start, end + 1).map { it.uri }.toSet()
        selectionManager.setUris((preDragSelectedUris - rangeUris) + (rangeUris - preDragSelectedUris))
    }

    fun endDragSelection() {
        preDragSelectedUris = emptySet()
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
        val selectedUris = selectedSongUris.value
        val toDelete = songs.filter { it.uri in selectedUris }

        viewModelScope.launch {
            songRepository.deleteSongs(toDelete)
            exitSelectionMode()
        }
    }

    fun batchShare(context: Context, songs: List<SongEntity>) {
        val selectedUris = selectedSongUris.value
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
