package com.lonx.lyrico.ui.screens.artist

import androidx.lifecycle.ViewModel
import com.lonx.lyrico.ui.SharedSelectionManager
import com.lonx.lyrico.data.db.entity.ArtistEntity

class ArtistSelectionViewModel : ViewModel() {
    private val selection = SharedSelectionManager()
    val selectedIds = selection.selectedUris
    val isSelectionMode = selection.isSelectionMode
    val swipeAnchorId = selection.swipeAnchorUri
    fun toggle(id: Long) = selection.toggle(id.toString())
    fun swipeSelect(artist: ArtistEntity, visible: List<ArtistEntity>) =
        selection.selectSwipeRange(artist.id.toString(), visible.map { it.id.toString() })
    fun selectAll(artists: List<ArtistEntity>) = selection.selectAll(artists.map { it.id.toString() }.toSet())
    fun deselectAll() = selection.deselectAll()
    fun exitSelectionMode() = selection.exitSelectionMode()
    fun retainVisible(artists: List<ArtistEntity>) {
        val retained = selectedIds.value.intersect(artists.map { it.id.toString() }.toSet())
        if (retained != selectedIds.value) selection.setUris(retained)
    }
}
