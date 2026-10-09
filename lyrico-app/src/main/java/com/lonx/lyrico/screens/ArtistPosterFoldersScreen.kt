package com.lonx.lyrico.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.viewmodel.ArtistPosterFoldersViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.jeziellago.compose.markdowntext.MarkdownText
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
@Destination<RootGraph>(route = "artist_poster_folders")
fun ArtistPosterFoldersScreen(navigator: DestinationsNavigator) {
    val viewModel: ArtistPosterFoldersViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(it, flags)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            viewModel.addFolder(it)
        }
    }
    var showRemoveDialog by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showFolderDetails by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val topBarBackdrop = rememberBarBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredTopBar(backdrop = topBarBackdrop) {
                SmallTopAppBar(
                    title = stringResource(R.string.artist_poster_folders),
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        IconButton(onClick = navigator::navigateUp) {
                            Icon(MiuixIcons.Back, stringResource(R.string.artist_poster_back))
                        }
                    },
                    actions = {
                        OverlayIconDropdownMenu(
                            entry = DropdownEntry(
                                items = buildList {
                                    add(DropdownItem(
                                        text = stringResource(R.string.artist_poster_naming),
                                        onClick = { showHelp = true }
                                    ))
                                    if (state.folder != null) {
                                        add(DropdownItem(
                                            text = stringResource(R.string.artist_poster_folder_details),
                                            onClick = { showFolderDetails = true }
                                        ))
                                        add(DropdownItem(
                                            text = stringResource(R.string.action_refresh_folder),
                                            onClick = viewModel::refresh
                                        ))
                                        add(DropdownItem(
                                            text = stringResource(R.string.artist_poster_folder_change),
                                            onClick = { picker.launch(null) }
                                        ))
                                        add(DropdownItem(
                                            text = stringResource(R.string.folder_action_remove),
                                            onClick = { showRemoveDialog = true }
                                        ))
                                    }
                                }
                            ),
                            enabled = !state.isLoading
                        ) {
                            Icon(MiuixIcons.More, stringResource(R.string.cd_more_actions))
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().blurSource(topBarBackdrop)) {
            val folder = state.folder
            if (folder != null) {
                ArtistPosterFolderContents(
                    folder = folder,
                    revision = state.revision,
                    isLoading = state.isLoading,
                    hasError = state.error,
                    onChangeFolder = { picker.launch(null) },
                    onRefresh = viewModel::refresh,
                    padding = scaffoldContentPadding(padding, horizontalExtra = 12.dp, bottomExtra = 12.dp),
                    scrollBehavior = scrollBehavior
                )
            } else {
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize().padding(scaffoldContentPadding(padding)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    ArtistPosterEmptyState(
                        title = stringResource(R.string.artist_poster_folder_not_selected),
                        summary = stringResource(if (state.error) R.string.artist_poster_folder_error else R.string.artist_poster_folders_empty),
                        actionText = stringResource(R.string.artist_poster_folder_add),
                        onAction = { picker.launch(null) },
                        modifier = Modifier.fillMaxSize().padding(scaffoldContentPadding(padding))
                    )
                }
            }
        }
        WindowDialog(
            title = stringResource(R.string.artist_poster_naming),
            show = showHelp,
            onDismissRequest = { showHelp = false }
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.artist_poster_folders_summary),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                MarkdownText(
                    modifier = Modifier.fillMaxWidth(),
                    markdown = stringResource(R.string.artist_poster_folders_help),
                    linkColor = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.body2.copy(
                        color = MiuixTheme.colorScheme.onSurface
                    )
                )
            }
        }
        val folder = state.folder
        if (folder != null) {
            WindowDialog(
                title = stringResource(R.string.artist_poster_folder_details),
                show = showFolderDetails,
                onDismissRequest = { showFolderDetails = false }
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(folder.name, style = MiuixTheme.textStyles.title4)
                    Text(
                        folder.path,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        stringResource(R.string.artist_poster_folder_status, folder.posters.orEmpty().size, folder.matchedPosterCount),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
        if (showRemoveDialog && folder != null) {
            WindowDialog(
                title = stringResource(R.string.dialog_remove_folder_title),
                show = true,
                onDismissRequest = { showRemoveDialog = false }
            ) {
                Column {
                    Text(folder.path, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.artist_poster_folder_remove_tip),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.body2.fontSize
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            text = stringResource(R.string.cancel),
                            onClick = { showRemoveDialog = false },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(20.dp))
                        TextButton(
                            text = stringResource(R.string.confirm),
                            onClick = {
                                viewModel.removeFolder()
                                showRemoveDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArtistPosterEmptyState(
    title: String,
    summary: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ArtistPosterEmptyIllustration()
            Spacer(Modifier.height(28.dp))
            Text(
                title,
                style = MiuixTheme.textStyles.title4,
                textAlign = TextAlign.Center
            )
            Text(summary, style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(20.dp))
            TextButton(text = actionText, onClick = onAction,
                modifier = Modifier.widthIn(min = 180.dp).heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColorsPrimary())
        }
    }
}

@Composable
private fun ArtistPosterEmptyIllustration() {
    val colors = MiuixTheme.colorScheme
    val frameShape = RoundedCornerShape(16.dp)
    Box(Modifier.size(width = 188.dp, height = 152.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.offset(x = (-22).dp, y = 6.dp).graphicsLayer { rotationZ = -12f }
            .size(width = 100.dp, height = 126.dp)
            .background(colors.primary.copy(alpha = 0.08f), frameShape)
            .border(1.dp, colors.primary.copy(alpha = 0.12f), frameShape))
        Box(Modifier.offset(x = 22.dp, y = 6.dp).graphicsLayer { rotationZ = 12f }
            .size(width = 100.dp, height = 126.dp)
            .background(colors.primary.copy(alpha = 0.12f), frameShape)
            .border(1.dp, colors.primary.copy(alpha = 0.16f), frameShape))
        Column(
            Modifier.size(width = 108.dp, height = 136.dp)
                .shadow(4.dp, frameShape).background(colors.surfaceContainer, frameShape)
                .border(1.dp, colors.primary.copy(alpha = 0.12f), frameShape).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)
                .background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center) {
                Icon(MiuixIcons.Image, null, tint = colors.primary, modifier = Modifier.size(40.dp))
            }
            Box(Modifier.fillMaxWidth(0.7f).height(5.dp)
                .background(colors.onSurface.copy(alpha = 0.16f), RoundedCornerShape(3.dp)))
            Box(Modifier.fillMaxWidth(0.45f).height(4.dp)
                .background(colors.onSurface.copy(alpha = 0.08f), RoundedCornerShape(3.dp)))
        }
    }
}
