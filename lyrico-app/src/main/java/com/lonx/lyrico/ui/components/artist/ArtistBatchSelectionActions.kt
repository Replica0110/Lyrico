package com.lonx.lyrico.ui.components.artist

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.fab.ExpandableFabMenu
import com.lonx.lyrico.ui.components.fab.FabMenuItem
import com.lonx.lyrico.ui.components.poster.ArtistPosterRemovalHost
import com.lonx.lyrico.viewmodel.ArtistPosterEditViewModel
import com.lonx.lyrico.viewmodel.ArtistSelectionViewModel

@Composable
fun BoxScope.ArtistBatchSelectionActions(
    selection: ArtistSelectionViewModel,
    editor: ArtistPosterEditViewModel,
    visible: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit
) {
    val selectedIds by selection.selectedIds.collectAsStateWithLifecycle()
    val selecting by selection.isSelectionMode.collectAsStateWithLifecycle()
    val operation by editor.state.collectAsStateWithLifecycle()
    var pendingRemoval by remember { mutableStateOf<Set<Long>>(emptySet()) }
    ExpandableFabMenu(
        visible = visible && selecting, expanded = expanded,
        enabled = selectedIds.isNotEmpty() && !operation.isRunning,
        itemCount = 1, modifier = modifier, onExpandedChange = onExpandedChange
    ) {
        FabMenuItem(label = stringResource(R.string.poster_remove_artwork), icon = LucideIcons.Trash, onClick = {
            onExpandedChange(false)
            pendingRemoval = selectedIds.map { it.toLong() }.toSet()
        })
    }
    ArtistPosterRemovalHost(editor, pendingRemoval, { pendingRemoval = emptySet() })
}
