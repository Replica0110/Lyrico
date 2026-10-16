@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.library.LocalLibraryUiEngine
import com.lonx.lyrico.ui.components.poster.PosterPreviewMenuAction
import com.lonx.lyrico.viewmodel.ArtistPosterFile
import com.lonx.lyrico.viewmodel.ArtistPosterFolder
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemValue
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
internal fun ArtistPosterFolderContents(
    folder: ArtistPosterFolder,
    revision: Long,
    isLoading: Boolean,
    enabled: Boolean,
    hasError: Boolean,
    onChangeFolder: () -> Unit,
    onRefresh: () -> Unit,
    onFileMenu: (ArtistPosterFile) -> Unit,
    onCrop: (ArtistPosterFile, android.graphics.Bitmap) -> Unit,
    onRename: (ArtistPosterFile) -> Unit,
    onArtistDetails: (ArtistPosterFile) -> Unit,
    onPreviewSelection: (String) -> Unit,
    previewUri: String?,
    onDismissPreview: () -> Unit,
    padding: PaddingValues,
    scrollBehavior: ScrollBehavior
) {
    val salt = LocalLibraryUiEngine.current == UiEngine.SALT
    val files = folder.posters.orEmpty()
    val scrollModifier = (if (salt) Modifier else Modifier
        .scrollEndHaptic()
        .overScrollVertical()
        .nestedScroll(scrollBehavior.nestedScrollConnection))
        .fillMaxSize()
    if (files.isEmpty()) {
        // 空/加载态也必须是可滚动容器：纯 Column 在内容不足一屏时完全不能滚动，
        // 和列表页（空状态作为列表项）不一致。
        LazyColumn(
            modifier = scrollModifier,
            contentPadding = padding,
            overscrollEffect = if (salt) rememberOverscrollEffect() else null,
        ) {
            item(key = "folder") {
                ArtistPosterDirectoryCard(folder, Modifier.padding(top = 12.dp))
            }
            item(key = "state") {
                // 撑满一屏，保证内容一定超过视口，短内容也能拖动。
                val blockModifier = Modifier.fillMaxWidth().fillParentMaxHeight()
                if (isLoading) {
                    Box(blockModifier, contentAlignment = Alignment.Center) {
                        if (salt) SaltLoadingIndicator()
                        else CircularProgressIndicator()
                    }
                } else {
                    val unavailable = folder.posters == null
                    IllustratedEmptyState(
                        kind = EmptyStateKind.Cover,
                        title = stringResource(if (unavailable) R.string.artist_poster_folder_unavailable else R.string.artist_poster_no_files),
                        summary = stringResource(if (hasError) R.string.artist_poster_folder_error else if (unavailable) R.string.artist_poster_folders_empty else R.string.artist_poster_empty_hint),
                        icon = if (unavailable) LucideIcons.Folder else LucideIcons.RefreshCw,
                        actionText = stringResource(if (unavailable) R.string.artist_poster_folder_change else R.string.action_refresh_folder),
                        onAction = if (unavailable) onChangeFolder else onRefresh,
                        modifier = blockModifier
                    )
                }
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = padding,
        modifier = scrollModifier,
        overscrollEffect = if (salt) rememberOverscrollEffect() else null
    ) {
        item(key = "folder", span = { GridItemSpan(maxLineSpan) }) {
            ArtistPosterDirectoryCard(folder, Modifier.padding(top = 12.dp))
        }
        if (isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (salt) SaltLoadingIndicator(Modifier.fillMaxWidth().padding(12.dp))
                else LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp))
            }
        }
        if (hasError) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (salt) com.moriafly.salt.ui.Text(
                    stringResource(R.string.artist_poster_folder_error),
                    color = SaltTheme.colors.error,
                    style = SaltTheme.textStyles.sub,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                else Text(
                    stringResource(R.string.artist_poster_folder_error),
                    color = MiuixTheme.colorScheme.error,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        items(files, key = { it.uri }) { file ->
            if (salt) {
                com.moriafly.salt.ui.Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()
                        .clickable(enabled = enabled && !isLoading, role = Role.Button) { onFileMenu(file) }) {
                        PosterImage(file, revision, Modifier.fillMaxWidth().aspectRatio(4f / 5f), ContentScale.Crop)
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            com.moriafly.salt.ui.Text(
                                file.artistName ?: stringResource(R.string.label_artist_image_unmatched),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = SaltTheme.textStyles.main, color = SaltTheme.colors.text,
                                modifier = Modifier.fillMaxWidth())
                            com.moriafly.salt.ui.Text(
                                file.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = SaltTheme.textStyles.sub, color = SaltTheme.colors.subText,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (enabled && !isLoading) { { onFileMenu(file) } } else null,
                    showIndication = true
                ) {
                    PosterImage(file, revision, Modifier.fillMaxWidth().aspectRatio(4f / 5f), ContentScale.Crop)
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            file.artistName ?: stringResource(R.string.label_artist_image_unmatched),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            file.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
    val previewIndex = files.indexOfFirst { it.uri == previewUri }
    if (previewIndex >= 0) {
        PosterPreview(files, previewIndex, revision,
            onRename = onRename, onArtistDetails = onArtistDetails, onPreviewSelection = onPreviewSelection,
            onCrop = onCrop, enabled = enabled,
            onDismiss = onDismissPreview)
    }
}

/** Salt 当前版本没有加载指示器组件，用已配置 Salt 颜色的回退控件。 */
@Composable
private fun SaltLoadingIndicator(modifier: Modifier = Modifier) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = modifier,
        color = SaltTheme.colors.highlight,
        trackColor = SaltTheme.colors.stroke,
    )
}

/**
 * 文件夹信息：只展示名称与完整路径，不换行截断、也不放操作。
 * 切换文件夹的入口在顶栏菜单里，页面上不再重复一个。
 */
@Composable
private fun ArtistPosterDirectoryCard(
    folder: ArtistPosterFolder,
    modifier: Modifier = Modifier
) {
    if (LocalLibraryUiEngine.current == UiEngine.SALT) {
        // 用 Card 而不是 ScreenCard：后者自带 16/8 外边距，和网格的 contentPadding 叠加会多出一圈。
        com.moriafly.salt.ui.Card(modifier = modifier.fillMaxWidth()) {
            ItemValue(text = folder.name, sub = folder.path)
        }
        return
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = folder.name,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Text(
                text = folder.path,
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun PosterImage(file: ArtistPosterFile, revision: Long, modifier: Modifier, scale: ContentScale) {
    val salt = LocalLibraryUiEngine.current == UiEngine.SALT
    val context = LocalContext.current
    val request = remember(file, revision, context) {
        ImageRequest.Builder(context)
            .data(if (file.name.endsWith(".mp4", true)) file else file.uri.toUri())
            .memoryCacheKey("poster:${file.uri}:${file.lastModified}:$revision")
            .diskCacheKey("poster:${file.uri}:${file.lastModified}:$revision")
            .build()
    }
    var failed by remember(file, revision) { mutableStateOf(false) }
    var loading by remember(file, revision) { mutableStateOf(true) }
    val background = if (salt) SaltTheme.colors.subBackground else MiuixTheme.colorScheme.surfaceVariant
    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        AsyncImage(request, file.name, Modifier.fillMaxSize(), contentScale = scale,
            onSuccess = { loading = false; failed = false }, onError = { loading = false; failed = true })
        if (loading) {
            if (salt) androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(24.dp), color = SaltTheme.colors.highlight, trackColor = SaltTheme.colors.stroke)
            else CircularProgressIndicator(size = 24.dp)
        }
        if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (salt) {
                com.moriafly.salt.ui.Icon(
                    androidx.compose.ui.graphics.vector.rememberVectorPainter(LucideIcons.Image), null,
                    tint = SaltTheme.colors.subText)
                com.moriafly.salt.ui.Text(stringResource(R.string.artist_poster_preview_error),
                    style = SaltTheme.textStyles.main, color = SaltTheme.colors.text)
            } else {
                Icon(LucideIcons.Image, null)
                Text(stringResource(R.string.artist_poster_preview_error), fontSize = MiuixTheme.textStyles.body2.fontSize)
            }
        }
    }
}

@Composable
private fun PosterPreview(files: List<ArtistPosterFile>, initialPage: Int, revision: Long,
    onRename: (ArtistPosterFile) -> Unit, onArtistDetails: (ArtistPosterFile) -> Unit,
    onPreviewSelection: (String) -> Unit, onCrop: (ArtistPosterFile, android.graphics.Bitmap) -> Unit,
    enabled: Boolean, onDismiss: () -> Unit) {
    val renameLabel = stringResource(R.string.action_rename)
    val detailsLabel = stringResource(R.string.poster_artist_details)
    val unmatchedLabel = stringResource(R.string.poster_artist_unmatched)
    com.lonx.lyrico.ui.components.poster.ArtistPosterPreview(
        items = remember(files, revision) { files.map { file ->
            com.lonx.lyrico.ui.components.poster.PosterPreviewMedia(file.name, file.uri,
                isVideo = file.name.endsWith(".mp4", true), revision = revision + file.lastModified)
        } }, initialPage = initialPage,
        editingEnabled = enabled,
        onCrop = { index, bitmap -> onCrop(files[index], bitmap) },
        menuActions = { index ->
            val file = files[index]
            listOf(
                PosterPreviewMenuAction(
                    title = renameLabel, icon = LucideIcons.Pencil, tone = PreferenceIconTone.Tools,
                    enabled = enabled, onClick = { onRename(file) }),
                PosterPreviewMenuAction(
                    title = detailsLabel, icon = LucideIcons.Users, tone = PreferenceIconTone.Library,
                    enabled = enabled && file.artistId != null,
                    summary = if (file.artistId == null) unmatchedLabel else null,
                    onClick = { onArtistDetails(file) }),
            )
        },
        onCurrentChanged = { onPreviewSelection(files[it].uri) }, onDismiss = onDismiss
    )
}
