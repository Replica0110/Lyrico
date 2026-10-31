package com.lonx.lyrico.ui.components.poster

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.parcelize.Parcelize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.viewmodel.ArtistDetailViewModel
import com.lonx.lyrico.viewmodel.ArtistImageSource

/** Shared local image picker, destination, permission retry and write progress. */
@Composable
fun ArtistPosterAddHost(
    viewModel: ArtistDetailViewModel,
    show: Boolean,
    onDismissRequest: () -> Unit,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val artist by viewModel.artist.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val artistName = artist?.name.orEmpty()
    val posterFolder by viewModel.artistPosterFolder.collectAsStateWithLifecycle()
    val artistPosterState by viewModel.artistPosterEmbedState.collectAsStateWithLifecycle()
    var showArtistPosterProgress by remember { mutableStateOf(false) }
    var pickedArtistImage by remember { mutableStateOf<ArtistImageSource?>(null) }

    fun applyPickedArtistImage(name: String, target: ArtistPosterTarget, uri: Uri?) {
        if (uri == null) return
        viewModel.prepareArtistImage(context, uri) { source ->
            pickedArtistImage = source
            showArtistPosterProgress = true
            when (target) {
                ArtistPosterTarget.EmbedIntoSongs ->
                    viewModel.embedArtistImageForAllSongs(name, songs, source)

                ArtistPosterTarget.SaveToFolder ->
                    viewModel.saveArtistImageToFolder(name, source)
            }
        }
    }

    val message = stringResource(R.string.permission_denied_cannot_save)
    val artistPosterPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val source = pickedArtistImage
        val name = artistName.trim()
        if (result.resultCode == Activity.RESULT_OK && source != null && name.isNotEmpty()) {
            showArtistPosterProgress = true
            viewModel.embedArtistImageForAllSongs(name, songs, source)
        } else {
            viewModel.clearArtistPosterStatus()
            Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 需要授权时拉起系统授权界面；只消费一次，重组不会重复弹窗
    LaunchedEffect(artistPosterState.permissionIntentSender) {
        val intentSender = artistPosterState.permissionIntentSender ?: return@LaunchedEffect
        showArtistPosterProgress = true
        artistPosterPermissionLauncher.launch(
            IntentSenderRequest.Builder(intentSender).build()
        )
        viewModel.consumeArtistPosterPermissionRequest()
    }

    val artistPosterMessage = artistPosterState.message
    LaunchedEffect(artistPosterMessage, showArtistPosterProgress) {
        if (artistPosterMessage != null && !showArtistPosterProgress) {
            Toast.makeText(context, artistPosterMessage, Toast.LENGTH_SHORT).show()
            viewModel.clearArtistPosterMessage()
        }
    }

    ArtistPosterPickerHost(
        show = show,
        artistName = artistName,
        posterFolder = posterFolder,
        onDismissRequest = onDismissRequest,
        enabled = enabled && !artistPosterState.isRunning && artist != null && songs.isNotEmpty(),
        onSetPosterFolder = viewModel::setArtistPosterFolder,
        onImagePicked = ::applyPickedArtistImage,
    )
    ArtistPosterProgressSheet(
        state = artistPosterState,
        show = showArtistPosterProgress && artistPosterState.hasContent,
        title = stringResource(R.string.label_set_artist_poster),
        onDismissRequest = { showArtistPosterProgress = false },
        onDismissFinished = {
            // 动画走完才丢掉结果，否则面板会先空一下再消失
            if (!artistPosterState.isRunning) {
                showArtistPosterProgress = false
                viewModel.clearArtistPosterStatus()
            }
        },
        onCancel = viewModel::cancelArtistPosterWrite,
        onGrantPermission = {
            artistPosterState.permissionIntentSender?.let { intentSender ->
                artistPosterPermissionLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
        }
    )
}

internal enum class ArtistPosterTarget { EmbedIntoSongs, SaveToFolder }

@Parcelize
private data class ArtistPosterPickRequest(val artistName: String, val target: ArtistPosterTarget) : Parcelable

/** Destination choice, folder permission and image selection shared by artist and song editors. */
@Composable
internal fun ArtistPosterPickerHost(
    show: Boolean,
    artistName: String,
    posterFolder: String?,
    onDismissRequest: () -> Unit,
    onSetPosterFolder: (Uri, () -> Unit) -> Unit,
    onImagePicked: (String, ArtistPosterTarget, Uri) -> Unit,
    enabled: Boolean = true,
    embedSummary: String = stringResource(R.string.artist_poster_embed_summary),
) {
    val context = LocalContext.current
    val permissionMessage = stringResource(R.string.permission_denied_cannot_save)
    var pendingRequest by rememberSaveable { mutableStateOf<ArtistPosterPickRequest?>(null) }
    val picked by rememberUpdatedState(onImagePicked)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val request = pendingRequest
        pendingRequest = null
        if (uri != null && request != null) picked(request.artistName, request.target, uri)
    }
    fun launchImagePicker(request: ArtistPosterPickRequest) {
        pendingRequest = request
        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val request = pendingRequest
        pendingRequest = null
        if (uri != null && request != null) {
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.isSuccess
            if (granted) onSetPosterFolder(uri) { launchImagePicker(request) }
            else Toast.makeText(context, permissionMessage, Toast.LENGTH_SHORT).show()
        }
    }
    com.lonx.lyrico.ui.components.base.ItemMenuBottomSheet(
        show = show,
        label = stringResource(R.string.artist_poster_destination_title),
        enableNestedScroll = false,
        onDismissRequest = onDismissRequest,
    ) {
        ArtistPosterDestinationPreferences(
            hasPosterFolder = posterFolder != null,
            enabled = enabled && artistName.isNotBlank(),
            embedSummary = embedSummary,
            onEmbedToSongs = {
                val request = ArtistPosterPickRequest(artistName, ArtistPosterTarget.EmbedIntoSongs)
                onDismissRequest()
                launchImagePicker(request)
            },
            onSaveToFolder = {
                val request = ArtistPosterPickRequest(artistName, ArtistPosterTarget.SaveToFolder)
                onDismissRequest()
                if (posterFolder == null) {
                    pendingRequest = request
                    folderPicker.launch(null)
                } else launchImagePicker(request)
            },
        )
    }
}
