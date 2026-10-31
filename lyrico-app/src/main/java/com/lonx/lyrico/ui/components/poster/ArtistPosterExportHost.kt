package com.lonx.lyrico.ui.components.poster

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.viewmodel.ArtistPosterEditViewModel

@Composable
fun ArtistPosterExportHost(viewModel: ArtistPosterEditViewModel, pendingIds: Set<Long>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exportSettings by viewModel.exportSettings.collectAsStateWithLifecycle()
    var exportIds by rememberSaveable(
        stateSaver = Saver<Set<Long>, LongArray>(save = { it.toLongArray() }, restore = { it.toSet() })
    ) { mutableStateOf<Set<Long>>(emptySet()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val targets = exportIds
        exportIds = emptySet()
        if (uri != null && targets.isNotEmpty()) {
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.isSuccess
            if (granted) viewModel.exportToFolder(targets, uri)
            else Toast.makeText(context, R.string.permission_denied_cannot_save, Toast.LENGTH_SHORT).show()
        }
    }
    YesNoDialog(
        show = pendingIds.isNotEmpty(), title = stringResource(R.string.poster_export_embedded),
        summary = stringResource(R.string.poster_export_confirm, pendingIds.size),
        confirmEnabled = exportSettings != null,
        onDismissRequest = onDismiss,
        onConfirm = {
            val targets = pendingIds.toSet()
            if (exportSettings?.folder != null) viewModel.export(targets)
            else { exportIds = targets; picker.launch(null) }
        }
    )
}
