package com.lonx.lyrico.ui.screens.artist.poster

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.dialog.YesNoDialog

@Composable
fun ArtistPosterRemovalHost(
    viewModel: ArtistPosterEditViewModel,
    pendingIds: Set<Long>,
    onDismissConfirmation: () -> Unit,
    showProgress: Boolean = true
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val titleRes by viewModel.titleRes.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.retry() else viewModel.cancel()
    }
    YesNoDialog(
        show = pendingIds.isNotEmpty(),
        title = stringResource(R.string.poster_remove_artwork),
        summary = stringResource(R.string.poster_remove_confirm, pendingIds.size),
        onDismissRequest = onDismissConfirmation,
        onConfirm = { viewModel.start(pendingIds) }
    )
    ArtistPosterProgressSheet(
        state = state, show = showProgress && state.hasContent,
        title = stringResource(titleRes),
        onDismissRequest = viewModel::clear, onDismissFinished = {}, onCancel = viewModel::cancel,
        onGrantPermission = { state.permissionIntentSender?.let { permission.launch(IntentSenderRequest.Builder(it).build()) } }
    )
}
