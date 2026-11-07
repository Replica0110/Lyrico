@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.data.model.artist.normalizedArtistKey
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.utils.ArtistNameSplitter
import com.lonx.lyrico.screens.library.LibrarySelectionState
import com.lonx.lyrico.ui.components.bar.SongBatchSelectionActions
import com.lonx.lyrico.ui.components.bar.SongSelectionTopAppBar
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.cover.CoverImage
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.library.AlbumReplayGainProgressBottomSheet
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.library.LibraryDetailIconButton
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.lonx.lyrico.ui.components.library.SelectableLibraryItem
import com.lonx.lyrico.ui.components.library.libraryDetailScroll
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.ui.components.song.SongActionSheets
import com.lonx.lyrico.ui.components.song.SongListItemActions
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.AlbumActionsViewModel
import com.lonx.lyrico.viewmodel.AlbumDetailViewModel
import com.lonx.lyrico.viewmodel.SongSelectionViewModel
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.popup.rememberPopupState
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ArtistDetailDestination
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
@Destination<RootGraph>(route = "album_detail")
fun AlbumDetailScreen(
    navigator: DestinationsNavigator,
    albumId: Long
) {
    val viewModel: AlbumDetailViewModel = koinViewModel(
        parameters = { parametersOf(albumId) }
    )
    val selectionViewModel: SongSelectionViewModel = koinViewModel()
    val albumActionsViewModel: AlbumActionsViewModel = koinViewModel()
    val album by viewModel.album.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val artistSplitConfig by viewModel.artistSplitConfig.collectAsStateWithLifecycle()
    val contributingArtistIds by viewModel.contributingArtistIds.collectAsStateWithLifecycle()
    val albumActionsUiState by albumActionsViewModel.uiState.collectAsStateWithLifecycle()
    val albumName = album?.name.orEmpty()
    val albumArtist = album?.albumArtist?.takeIf { it.isNotBlank() }
        ?: songs.firstNotNullOfOrNull { song ->
            song.albumArtist?.trim()?.takeIf { it.isNotBlank() }
        }
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongUris by selectionViewModel.selectedSongUris.collectAsStateWithLifecycle()
    val swipeAnchorUri by selectionViewModel.swipeAnchorUri.collectAsStateWithLifecycle()
    val swipeSelectionLabel = stringResource(
        if (!isSelectionMode) {
            R.string.swipe_selection_enter_selection
        } else if (swipeAnchorUri == null) {
            R.string.swipe_selection_range_start
        } else {
            R.string.swipe_selection_range_end
        }
    )
    val swipeSelectionSecondaryLabel = if (!isSelectionMode) {
        stringResource(R.string.swipe_selection_range_start)
    } else {
        null
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var isFabMenuExpanded by remember { mutableStateOf(false) }

    var selectedSong by remember { mutableStateOf<SongEntity?>(null) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteAlbumDialog by remember { mutableStateOf(false) }
    var albumBackdropHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val totalDurationMilliseconds = remember(songs) {
        songs.sumOf { it.durationMilliseconds.toLong() }
    }
    val songsByDisc = remember(songs) {
        songs.groupBy { it.discNumber ?: 1 }.toSortedMap()
    }
    val contributingArtists = remember(songs, artistSplitConfig, contributingArtistIds) {
        songs.asSequence()
            .flatMap { song ->
                ArtistNameSplitter.splitArtists(song.artist, artistSplitConfig)
                    .asSequence()
                    .map { artist -> artist to song }
            }
            .groupBy { it.first }
            .values
            .map { entries ->
                AlbumContributor(
                    name = entries.first().first,
                    songCount = entries.size,
                    artistId = contributingArtistIds[entries.first().first.normalizedArtistKey()],
                    coverUri = entries.first().second.uri,
                    coverLastModified = entries.first().second.fileLastModified,
                )
            }
    }

    LaunchedEffect(albumActionsViewModel, context) {
        albumActionsViewModel.events.collect { message ->
            message.asString(context)?.let { text ->
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = isSelectionMode) {
        if (isFabMenuExpanded) {
            isFabMenuExpanded = false
        } else {
            selectionViewModel.exitSelectionMode()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LibraryDetailPage(
            title = "",
            onBack = { navigator.popBackStack() },
            backgroundContent = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    AlbumDetailBackdrop(
                        coverSong = songs.firstOrNull(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                if (albumBackdropHeightPx > 0) {
                                    // 背景只铺到统计栏下沿，不盖住 DISC 区域
                                    with(density) { albumBackdropHeightPx.toDp() }
                                } else {
                                    360.dp
                                }
                            ),
                    )
                }
            },
            selection = if (isSelectionMode) {
                val allSelected = songs.isNotEmpty() && songs.all { it.uri in selectedSongUris }
                LibrarySelectionState(
                    title = stringResource(R.string.selection_mode_selected_count, selectedSongUris.size),
                    allSelected = allSelected,
                    onClose = selectionViewModel::exitSelectionMode,
                    onToggleAll = {
                        if (allSelected) selectionViewModel.deselectAll()
                        else selectionViewModel.selectAll(songs)
                    }
                )
            } else null,
            actions = {
                SaltAlbumActionsMenu(
                    isCalculatingReplayGain = albumActionsUiState.isCalculatingAlbumReplayGain,
                    onShare = { albumActionsViewModel.shareAlbum(context, songs) },
                    onDelete = { showDeleteAlbumDialog = true },
                    onCalculateReplayGain = { albumActionsViewModel.calculateAlbumReplayGain(songs) }
                )
            },
            miuixTopBar = {
                AnimatedContent(
                    targetState = isSelectionMode,
                    label = "AlbumDetailTopBarAnimation",
                    transitionSpec = {
                        val animationDuration = 300
                        val enter = fadeIn(tween(animationDuration)) +
                                slideInVertically(
                                    animationSpec = tween(
                                        animationDuration,
                                        easing = FastOutSlowInEasing
                                    ),
                                    initialOffsetY = { -it / 3 }
                                )
                        val exit = fadeOut(tween(animationDuration)) +
                                slideOutVertically(
                                    animationSpec = tween(
                                        animationDuration,
                                        easing = FastOutSlowInEasing
                                    ),
                                    targetOffsetY = { -it / 3 }
                                )

                        (enter togetherWith exit).using(null)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { selectionMode ->
                    if (selectionMode) {
                        SongSelectionTopAppBar(
                            songs = songs,
                            selectedSongUris = selectedSongUris,
                            scrollBehavior = topAppBarScrollBehavior,
                            onSelectAll = selectionViewModel::selectAll,
                            onDeselectAll = selectionViewModel::deselectAll,
                            onClose = selectionViewModel::exitSelectionMode
                        )
                    } else {
                        SmallTopAppBar(
                            title = "",
                            color = Color.Transparent,
                            navigationIcon = {
                                IconButton(onClick = { navigator.popBackStack() }) {
                                    Icon(
                                        imageVector = LucideIcons.ArrowLeft,
                                        contentDescription = stringResource(R.string.action_back)
                                    )
                                }
                            },
                            actions = {
                                OverlayIconDropdownMenu(
                                    entries = listOf(
                                        albumActionsDropdownEntry(
                                            isCalculatingReplayGain = albumActionsUiState.isCalculatingAlbumReplayGain,
                                            onShare = {
                                                albumActionsViewModel.shareAlbum(context, songs)
                                            },
                                            onDelete = {
                                                showDeleteAlbumDialog = true
                                            },
                                            onCalculateReplayGain = {
                                                albumActionsViewModel.calculateAlbumReplayGain(songs)
                                            }
                                        )
                                    )
                                ) {
                                    Icon(
                                        imageVector = LucideIcons.EllipsisVertical,
                                        contentDescription = stringResource(R.string.album_action_more)
                                    )
                                }
                            },
                            scrollBehavior = topAppBarScrollBehavior
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            scaffoldTopHorizontalPadding(
                                paddingValues = paddingValues,
                                // Salt 标题栏 56dp，Miuix 顶栏 52dp，补 4dp 让两个引擎的头部起点一致
                                topExtra = if (LocalUiEngine.current == UiEngine.MIUIX) 4.dp else 0.dp,
                            )
                        )
                        .libraryDetailScroll(topAppBarScrollBehavior),
                    state = listState,
                    contentPadding = PaddingValues(
                        bottom = paddingValues.calculateBottomPadding() + 12.dp
                    ),
                    overscrollEffect = if (LocalUiEngine.current == UiEngine.SALT) {
                        androidx.compose.foundation.rememberOverscrollEffect()
                    } else null
                ) {
                    item("album_header") {
                        AlbumDetailHeader(
                            album = albumName,
                            albumArtist = albumArtist,
                            songCount = songs.size,
                            year = formatAlbumYear(album?.year),
                            coverSong = songs.firstOrNull(),
                            totalDurationMilliseconds = totalDurationMilliseconds,
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                // 只在列表位于顶部时测量，避免滚动时背景高度跟着变化
                                if (listState.firstVisibleItemIndex == 0 &&
                                    listState.firstVisibleItemScrollOffset == 0
                                ) {
                                    albumBackdropHeightPx = (
                                        coordinates.positionInRoot().y + coordinates.size.height
                                    ).roundToInt()
                                }
                            },
                        )
                    }

                    if (songs.isEmpty()) {
                        item("empty") {
                            IllustratedEmptyState(
                                kind = EmptyStateKind.Songs,
                                title = stringResource(R.string.empty_songs_title),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    songsByDisc.forEach { (discNumber, discSongs) ->
                        item("disc-$discNumber") {
                            AlbumDiscHeader(
                                discNumber = discNumber,
                                songCount = discSongs.size,
                                durationMilliseconds = discSongs.sumOf {
                                    it.durationMilliseconds.toLong()
                                },
                            )
                        }
                        items(
                            items = discSongs,
                            key = { song ->
                                song.uri.takeIf { it.isNotBlank() && it != "0" }
                                    ?: "song-${song.id}"
                            }
                        ) { song ->
                            SelectableLibraryItem(
                                itemKey = song.uri,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedSongUris.contains(song.uri),
                                swipeSelectionLabel = swipeSelectionLabel,
                                swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                                onClick = {
                                    navigator.navigate(EditMetadataDestination(songFileUri = song.uri))
                                },
                                onToggleSelection = {
                                    selectionViewModel.toggleSelection(song.uri)
                                },
                                onSwipeSelection = {
                                    selectionViewModel.swipeSelect(song, songs)
                                }
                            ) {
                                AlbumSongRow(
                                    song = song,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedSongUris.contains(song.uri),
                                    onToggleSelection = {
                                        selectionViewModel.toggleSelection(song.uri)
                                    },
                                    onShowMenu = {
                                        selectedSong = song
                                        showMenuSheet = true
                                    },
                                )
                            }
                        }
                    }

                    if (contributingArtists.isNotEmpty()) {
                        item("contributors") {
                            AlbumContributorsSection(
                                artists = contributingArtists,
                                onArtistClick = { artistId ->
                                    navigator.navigate(ArtistDetailDestination(artistId = artistId))
                                },
                            )
                        }
                    }
                }

                SongActionSheets(
                    selectedSong = selectedSong,
                    showMenuSheet = showMenuSheet,
                    showDetailSheet = showDetailSheet,
                    showDeleteDialog = showDeleteDialog,
                    showRenameDialog = showRenameDialog,
                    onDismissMenu = { showMenuSheet = false },
                    onDismissMenuFinished = {
                        if (!showMenuSheet && !showDetailSheet && !showDeleteDialog && !showRenameDialog) {
                            selectedSong = null
                        }
                    },
                    onDismissDetail = { showDetailSheet = false },
                    onDismissDelete = { showDeleteDialog = false },
                    onDismissRename = { showRenameDialog = false },
                    onShowDetail = { showDetailSheet = true },
                    onShowDelete = { showDeleteDialog = true },
                    onShowRename = { showRenameDialog = true },
                    onPlay = { song -> selectionViewModel.play(context, song) },
                    onDelete = { song -> selectionViewModel.delete(song) },
                    onRename = { song, newFileName ->
                        selectionViewModel.renameSong(song, newFileName)
                    }
                )
            }
        }

        YesNoDialog(
            title = stringResource(R.string.dialog_delete_album_title),
            show = showDeleteAlbumDialog,
            summary = stringResource(
                R.string.dialog_delete_album_content,
                songs.size,
                albumName.ifBlank { stringResource(R.string.album_detail_title) }
            ),
            onConfirm = {
                showDeleteAlbumDialog = false
                albumActionsViewModel.deleteAlbum(songs)
                navigator.popBackStack()
            },
            onDismissRequest = { showDeleteAlbumDialog = false }
        )

        AlbumReplayGainProgressBottomSheet(
            uiState = albumActionsUiState,
            onDismissRequest = albumActionsViewModel::closeAlbumReplayGainProgressDialog,
            onDismissFinished = albumActionsViewModel::clearAlbumReplayGainProgressDialog,
            onAbort = albumActionsViewModel::cancelAlbumReplayGain
        )

        SongBatchSelectionActions(
            navigator = navigator,
            songs = songs,
            show = isSelectionMode,
            expanded = isFabMenuExpanded,
            selectedSongUris = selectedSongUris,
            onExpandedChange = { isFabMenuExpanded = it },
            onSetSelectionUris = selectionViewModel::setSelectionUris,
            onBatchDelete = selectionViewModel::batchDelete,
            onBatchShare = selectionViewModel::batchShare
        )
    }
}

@Composable
private fun SaltAlbumActionsMenu(
    isCalculatingReplayGain: Boolean,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCalculateReplayGain: () -> Unit
) {
    val popup = rememberPopupState()
    Box {
        LibraryDetailIconButton(
            icon = LucideIcons.EllipsisVertical,
            description = stringResource(R.string.album_action_more),
            onClick = popup::expend
        )
        PopupMenu(expanded = popup.expend, onDismissRequest = popup::dismiss) {
            if (isCalculatingReplayGain) {
                // This SaltUI version's popup row has no disabled API; Item supplies native disabled behavior.
                com.moriafly.salt.ui.Item(
                    text = stringResource(R.string.menu_action_calculate_album_replay_gain),
                    modifier = Modifier.widthIn(min = 180.dp, max = 280.dp),
                    enabled = false,
                    iconPainter = rememberVectorPainter(LucideIcons.Volume2),
                    iconColor = PreferenceIconTone.Appearance.color(),
                    arrowType = com.moriafly.salt.ui.ItemArrowType.None,
                    onClick = {}
                )
            } else {
                PopupMenuItem(
                    text = stringResource(R.string.menu_action_calculate_album_replay_gain),
                    iconPainter = rememberVectorPainter(LucideIcons.Volume2),
                    iconColor = PreferenceIconTone.Appearance.color(),
                    onClick = {
                        popup.dismiss()
                        onCalculateReplayGain()
                    }
                )
            }
            PopupMenuItem(
                text = stringResource(R.string.menu_action_share_album),
                iconPainter = rememberVectorPainter(LucideIcons.Share2),
                iconColor = PreferenceIconTone.Tools.color(),
                onClick = {
                    popup.dismiss()
                    onShare()
                }
            )
            PopupMenuItem(
                text = stringResource(R.string.menu_action_delete_album),
                iconPainter = rememberVectorPainter(LucideIcons.Trash),
                iconColor = SaltTheme.colors.error,
                onClick = {
                    popup.dismiss()
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun albumActionsDropdownEntry(
    isCalculatingReplayGain: Boolean,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCalculateReplayGain: () -> Unit
): DropdownEntry {
    return DropdownEntry(
        items = listOf(
            DropdownItem(
                icon = { modifier -> PreferenceIcon(LucideIcons.Volume2, PreferenceIconTone.Appearance, modifier = modifier, enabled = !isCalculatingReplayGain) },
                text = stringResource(R.string.menu_action_calculate_album_replay_gain),
                enabled = !isCalculatingReplayGain,
                onClick = {
                    if (!isCalculatingReplayGain) {
                        onCalculateReplayGain()
                    }
                }
            ),
            DropdownItem(
                icon = { modifier -> PreferenceIcon(LucideIcons.Share2, PreferenceIconTone.Tools, modifier = modifier) },
                text = stringResource(R.string.menu_action_share_album),
                onClick = onShare
            ),
            DropdownItem(
                icon = { modifier -> PreferenceIcon(LucideIcons.Trash, PreferenceIconTone.Danger, modifier = modifier) },
                text = stringResource(R.string.menu_action_delete_album),
                onClick = onDelete
            )
        )
    )
}

@Composable
private fun AlbumDetailBackdrop(
    coverSong: SongEntity?,
    modifier: Modifier = Modifier,
) {
    val background =
        if (LocalUiEngine.current == UiEngine.SALT) {
            SaltTheme.colors.background
        } else {
            MiuixTheme.colorScheme.background
        }
    val brightness =
        background.red * 0.299f + background.green * 0.587f + background.blue * 0.114f
    val dark = brightness < 0.5f
    Box(modifier = modifier) {
        if (coverSong != null) {
            CoverImage(
                uri = coverSong.uri,
                lastModified = coverSong.fileLastModified,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(68.dp),
                shape = RectangleShape,
                contentDescription = null,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    // 整段渐变到纯背景色，和下方列表无缝衔接
                    Brush.verticalGradient(
                        0f to background.copy(alpha = if (dark) 0.50f else 0.72f),
                        0.30f to background.copy(alpha = if (dark) 0.55f else 0.80f),
                        0.50f to background.copy(alpha = if (dark) 0.58f else 0.87f),
                        0.65f to background.copy(alpha = if (dark) 0.68f else 0.93f),
                        0.78f to background.copy(alpha = if (dark) 0.82f else 0.97f),
                        0.90f to background.copy(alpha = if (dark) 0.94f else 1f),
                        0.97f to background,
                        1f to background,
                    )
                )
        )
    }
}

@Composable
private fun AlbumDetailHeader(
    album: String,
    albumArtist: String?,
    songCount: Int,
    year: String?,
    coverSong: SongEntity?,
    totalDurationMilliseconds: Long,
    modifier: Modifier = Modifier,
) {
    val technicalInfo = listOfNotNull(
        coverSong?.bitrate?.takeIf { it > 0 }?.let { "$it kbps" },
        coverSong?.sampleRate?.takeIf { it > 0 }?.let { "$it Hz" },
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverImage(
                uri = coverSong?.uri,
                lastModified = coverSong?.fileLastModified ?: 0L,
                modifier = Modifier.size(120.dp),
                shape = RoundedCornerShape(10.dp),
                contentDescription = album.ifBlank { stringResource(R.string.album_detail_title) },
            )
            Column(modifier = Modifier.weight(1f)) {
                com.moriafly.salt.ui.Text(
                    text = album.ifBlank { stringResource(R.string.album_detail_title) },
                    style = SaltTheme.textStyles.main.copy(
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                    ),
                    color = SaltTheme.colors.text,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                com.moriafly.salt.ui.Text(
                    text = albumArtist?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.unknown_album_artist),
                    style = SaltTheme.textStyles.main.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    color = SaltTheme.colors.subText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (technicalInfo.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    com.moriafly.salt.ui.Text(
                        text = technicalInfo.joinToString("\n"),
                        style = SaltTheme.textStyles.sub.copy(
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                        ),
                        color = SaltTheme.colors.subText,
                        maxLines = technicalInfo.size,
                    )
                }
            }
        }
        AlbumStatsRow(
            songCount = songCount,
            durationMilliseconds = totalDurationMilliseconds,
            year = year,
        )
    }
}

@Composable
private fun AlbumStatsRow(
    songCount: Int,
    durationMilliseconds: Long,
    year: String?,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AlbumStat(
                value = songCount.toString(),
                label = stringResource(R.string.album_detail_tracks),
                modifier = Modifier.weight(1f),
            )
            AlbumStat(
                value = formatAlbumDuration(durationMilliseconds),
                label = stringResource(R.string.album_detail_duration),
                modifier = Modifier.weight(1f),
            )
            AlbumStat(
                value = year ?: "—",
                label = stringResource(R.string.album_detail_year),
                modifier = Modifier.weight(1f),
            )
        }
        AlbumDivider()
    }
}

@Composable
private fun AlbumStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        com.moriafly.salt.ui.Text(
            text = value,
            style = SaltTheme.textStyles.largeTitle.copy(
                fontSize = 18.sp,
                lineHeight = 21.sp,
            ),
            color = SaltTheme.colors.text,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        com.moriafly.salt.ui.Text(
            text = label,
            style = SaltTheme.textStyles.sub.copy(
                fontSize = 12.sp,
                lineHeight = 15.sp,
            ),
            color = SaltTheme.colors.subText,
            maxLines = 1,
        )
    }
}

@Composable
private fun AlbumDivider() {
    // Salt 的分隔线按 1 物理像素画，避免高密度屏上偏粗
    val hairline = with(LocalDensity.current) { (1f / density).dp }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(hairline)
            .background(SaltTheme.colors.text.copy(alpha = 0.12f))
    )
}

@Composable
private fun AlbumDiscHeader(
    discNumber: Int,
    songCount: Int,
    durationMilliseconds: Long,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.moriafly.salt.ui.Text(
            text = "DISC $discNumber",
            style = SaltTheme.textStyles.main.copy(fontSize = 12.sp),
            color = SaltTheme.colors.text,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        com.moriafly.salt.ui.Text(
            text = "($songCount / ${formatAlbumDuration(durationMilliseconds)})",
            style = SaltTheme.textStyles.sub.copy(fontSize = 12.sp),
            color = SaltTheme.colors.subText,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun AlbumSongRow(
    song: SongEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onShowMenu: () -> Unit,
) {
    val trackNumber = song.trackerNumber
        ?.substringBefore('/')
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: "—"
    val artist = song.artist?.trim()?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.unknown_artist)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp)
            // 行高对齐 Salt：56dp 内容 + 上下各 8dp 外边距 = 72dp
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        com.moriafly.salt.ui.Text(
            text = trackNumber,
            modifier = Modifier.width(40.dp),
            style = SaltTheme.textStyles.main.copy(fontSize = 15.sp),
            color = SaltTheme.colors.subText,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            com.moriafly.salt.ui.Text(
                text = song.title?.takeIf(String::isNotBlank) ?: song.fileName,
                style = SaltTheme.textStyles.main.copy(
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                ),
                color = SaltTheme.colors.text,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            com.moriafly.salt.ui.Text(
                text = artist,
                style = SaltTheme.textStyles.sub.copy(
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                ),
                color = SaltTheme.colors.subText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SongListItemActions(
            isSelectionMode = isSelectionMode,
            isSelected = isSelected,
            onToggleSelection = onToggleSelection,
            onShowMenu = onShowMenu,
        )
    }
}

@Composable
private fun AlbumContributorsSection(
    artists: List<AlbumContributor>,
    onArtistClick: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        AlbumDivider()
        com.moriafly.salt.ui.Text(
            text = stringResource(R.string.album_detail_contributors),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            style = SaltTheme.textStyles.main.copy(fontSize = 13.sp),
            color = SaltTheme.colors.highlight,
            fontWeight = FontWeight.SemiBold,
        )
        artists.forEach { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .then(
                        artist.artistId?.let { artistId ->
                            Modifier.clickable(
                                role = Role.Button,
                                onClick = { onArtistClick(artistId) },
                            )
                        } ?: Modifier
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CoverImage(
                    uri = artist.coverUri,
                    lastModified = artist.coverLastModified,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    shape = CircleShape,
                    pictureType = AudioPictureType.FrontCover,
                    fallbackToAny = true,
                    artistName = artist.name,
                    contentDescription = artist.name,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    com.moriafly.salt.ui.Text(
                        text = artist.name,
                        style = SaltTheme.textStyles.main.copy(fontSize = 17.sp),
                        color = SaltTheme.colors.text,
                        fontWeight = FontWeight.Medium,
                    )
                    com.moriafly.salt.ui.Text(
                        text = stringResource(R.string.song_count, artist.songCount),
                        style = SaltTheme.textStyles.sub.copy(fontSize = 14.sp),
                        color = SaltTheme.colors.subText,
                    )
                }
            }
        }
    }
}

private data class AlbumContributor(
    val name: String,
    val songCount: Int,
    val artistId: Long?,
    val coverUri: String,
    val coverLastModified: Long,
)

private fun formatAlbumDuration(durationMilliseconds: Long): String {
    val totalSeconds = durationMilliseconds.coerceAtLeast(0L) / 1000L
    val seconds = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val minutes = totalMinutes % 60
    val hours = totalMinutes / 60
    return if (hours > 0) {
        "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${totalMinutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}

private fun formatAlbumYear(value: String?): String? {
    val year = value?.trim()?.take(4) ?: return null
    return year.takeIf { it.length == 4 && it.all(Char::isDigit) }
}
