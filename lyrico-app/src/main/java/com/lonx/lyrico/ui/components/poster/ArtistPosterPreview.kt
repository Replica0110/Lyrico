@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.poster

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.crop.ImageCropperSheet
import com.lonx.lyrico.ui.theme.LocalUiEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.enabledAlpha
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class PreviewCrop(val key: String, val media: PosterPreviewMedia, val bitmap: android.graphics.Bitmap)

/**
 * 预览顶栏菜单的一项，与具体 UI 引擎无关：Miuix 渲染下拉菜单，Salt 渲染原生弹出菜单。
 * Salt 3.0.0-beta02 的 PopupMenuItem 没有禁用参数，禁用项由调用方在这里表达。
 */
internal data class PosterPreviewMenuAction(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tone: PreferenceIconTone = PreferenceIconTone.Tools,
    val enabled: Boolean = true,
    val summary: String? = null,
    val onClick: () -> Unit,
)

@Composable
internal fun ArtistPosterPreview(
    items: List<PosterPreviewMedia>,
    initialPage: Int = 0,
    title: (Int) -> String = { items[it].name },
    onCrop: (Int, android.graphics.Bitmap) -> Unit,
    editingEnabled: Boolean = true,
    menuActions: ((Int) -> List<PosterPreviewMenuAction>)? = null,
    onCurrentChanged: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(items.indices), pageCount = { items.size })
    val currentPage = pager.currentPage.coerceIn(items.indices)
    val current = items[currentPage]
    LaunchedEffect(current.key) { onCurrentChanged?.invoke(currentPage) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chromeVisible by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val infoProgress = remember { Animatable(0f) }
    var info by remember { mutableStateOf<PosterMediaInfo?>(null) }
    var infoLoading by remember { mutableStateOf(false) }
    var infoError by remember { mutableStateOf(false) }
    var infoRetry by remember { mutableIntStateOf(0) }
    var showCrop by remember { mutableStateOf(false) }
    var crop by remember { mutableStateOf<PreviewCrop?>(null) }
    var infoDragging by remember { mutableStateOf(false) }
    var previewMenuExpanded by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val infoOpen = infoProgress.targetValue > 0f

    fun setInfo(open: Boolean) {
        chromeVisible = true
        scope.launch { infoProgress.animateTo(if (open) 1f else 0f, tween(320, easing = FastOutSlowInEasing)) }
    }
    fun toggleChrome() {
        if (infoProgress.value > 0f || infoOpen) {
            setInfo(false)
            chromeVisible = false
        } else chromeVisible = !chromeVisible
    }
    fun dismiss() { if (infoProgress.value > 0f || infoOpen) setInfo(false) else onDismiss() }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                android.widget.Toast.makeText(context, R.string.artist_poster_preview_error, android.widget.Toast.LENGTH_LONG).show()
            } finally { busy = false }
        }
    }
    LaunchedEffect(current, infoOpen, infoRetry) {
        if (infoOpen) {
            info = null
            infoError = false
            infoLoading = true
            try { info = withContext(Dispatchers.IO) { current.readInfo(context) } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { infoError = true }
            finally { infoLoading = false }
        }
    }
    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            val panelHeight = maxHeight * 0.48f
            val panelPixels = with(androidx.compose.ui.platform.LocalDensity.current) { panelHeight.roundToPx().toFloat() }
            val fullImageSize = with(androidx.compose.ui.platform.LocalDensity.current) { Size(maxWidth.roundToPx().toFloat(), maxHeight.roundToPx().toFloat()) }
            val infoImageSize = Size(fullImageSize.width, fullImageSize.height - panelPixels)
            val dragEnabled = !busy && crop == null
            val dragState = rememberDraggableState { delta ->
                dragPosition = (dragPosition - delta / panelPixels.coerceAtLeast(1f)).coerceIn(0f, 1f)
                scope.launch { infoProgress.snapTo(dragPosition) }
            }
            val verticalGesture = Modifier.draggable(
                state = dragState, orientation = Orientation.Vertical, enabled = dragEnabled,
                onDragStarted = { infoDragging = true; dragPosition = infoProgress.value; chromeVisible = true },
                onDragStopped = { velocity ->
                    infoDragging = false
                    setInfo(when { velocity < -400f -> true; velocity > 400f -> false; else -> dragPosition > 0.4f })
                }
            )
            val progress = infoProgress.value
            // Salt 走自身语义色板；压在图片上的黑底/白字渐变与海报合成，保持引擎中立。
            val salt = LocalUiEngine.current == UiEngine.SALT
            val panelColor = if (salt) SaltTheme.colors.background else MiuixTheme.colorScheme.surface
            val actionBackground = lerp(Color(0xFF202020),
                if (salt) SaltTheme.colors.subBackground else MiuixTheme.colorScheme.surfaceVariant, progress)
            val actionTint = lerp(Color.White.copy(alpha = 0.9f),
                if (salt) SaltTheme.colors.text else MiuixTheme.colorScheme.onSurface, progress)
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().then(verticalGesture)) {
                    HorizontalPager(pager, modifier = Modifier.fillMaxSize(), key = { items[it].key },
                        userScrollEnabled = dragEnabled) { page ->
                        val item = items[page]
                        if (item.isVideo) {
                            if (page == currentPage) {
                                var failed by remember(item.key) { mutableStateOf(false) }
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    AndroidView(factory = { videoContext ->
                                        VideoView(videoContext).apply {
                                            setVideoURI(item.uri.toUri())
                                            setMediaController(MediaController(videoContext).also { it.setAnchorView(this) })
                                            setOnPreparedListener { start() }
                                            setOnErrorListener { _, _, _ -> failed = true; true }
                                        }
                                    }, modifier = Modifier.fillMaxSize(), onRelease = { it.stopPlayback() })
                                    if (failed) Text(stringResource(R.string.artist_poster_preview_error), color = Color.White)
                                }
                            }
                        } else {
                            PosterPreviewImage(item, Modifier.fillMaxSize(), fillProgress = progress,
                                fullImageSize = fullImageSize, infoImageSize = infoImageSize,
                                gesturesEnabled = dragEnabled, transitioning = infoDragging || infoProgress.isRunning,
                                selected = page == currentPage, onTap = ::toggleChrome)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(panelHeight * progress).clipToBounds().background(panelColor)) {
                    PosterInformation(current, info, infoLoading, infoError, { infoRetry++ }, verticalGesture)
                }
            }
            AnimatedVisibility(chromeVisible && progress < 0.02f, modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it / 3 },
                exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { -it / 3 }) {
                Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                    .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::dismiss, modifier = Modifier.size(48.dp)) {
                        Icon(LucideIcons.ArrowLeft, stringResource(R.string.artist_poster_back), tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(title(currentPage), color = Color.White,
                            fontSize = if (salt) SaltTheme.textStyles.main.fontSize else MiuixTheme.textStyles.body1.fontSize,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (items.size > 1) Text("${currentPage + 1} / ${items.size}", color = Color.White.copy(alpha = 0.6f),
                            fontSize = if (salt) SaltTheme.textStyles.sub.fontSize else MiuixTheme.textStyles.footnote1.fontSize,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                    menuActions?.let { provideActions ->
                        if (LocalUiEngine.current == UiEngine.SALT) {
                            val actions = provideActions(currentPage)
                            Box {
                                Box(Modifier.size(48.dp).clip(CircleShape)
                                    .clickable(enabled = !busy && editingEnabled, role = Role.Button,
                                        onClick = { previewMenuExpanded = true }),
                                    contentAlignment = Alignment.Center) {
                                    com.moriafly.salt.ui.Icon(rememberVectorPainter(LucideIcons.EllipsisVertical),
                                        stringResource(R.string.cd_more_actions),
                                        modifier = Modifier.size(24.dp), tint = Color.White)
                                }
                                com.moriafly.salt.ui.popup.PopupMenu(
                                    expanded = previewMenuExpanded,
                                    onDismissRequest = { previewMenuExpanded = false }) {
                                    actions.forEach { action ->
                                        // Keep disabled actions visible and inert; the summary explains why.
                                        Box(Modifier.enabledAlpha(action.enabled)) {
                                            com.moriafly.salt.ui.popup.PopupMenuItem(
                                                text = action.title, sub = action.summary,
                                                iconPainter = rememberVectorPainter(action.icon),
                                                onClick = {
                                                    previewMenuExpanded = false
                                                    if (action.enabled) action.onClick()
                                                })
                                        }
                                    }
                                }
                            }
                        } else {
                            val actions = provideActions(currentPage)
                            val entry = DropdownEntry(items = actions.map { action ->
                                DropdownItem(
                                    icon = { modifier ->
                                        PreferenceIcon(action.icon, action.tone, modifier = modifier, enabled = action.enabled)
                                    },
                                    text = action.title, summary = action.summary, enabled = action.enabled,
                                    onClick = action.onClick)
                            })
                            WindowIconDropdownMenu(entry = entry, enabled = !busy && editingEnabled,
                                minWidth = 48.dp, minHeight = 48.dp) {
                                Icon(LucideIcons.EllipsisVertical, stringResource(R.string.cd_more_actions), tint = Color.White)
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(chromeVisible, modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 },
                exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { it / 3 }) {
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent,
                    lerp(Color.Black.copy(alpha = 0.92f), panelColor, progress))))
                    .padding(top = 16.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(items.size > 1 && progress < 0.02f, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
                        PosterThumbnailStrip(items, currentPage, enabled = !busy && crop == null) { index ->
                            scope.launch { pager.animateScrollToPage(index) }
                        }
                    }
                    Row(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        PreviewAction(LucideIcons.Share2, stringResource(R.string.action_share), !busy, actionBackground, actionTint) {
                            action {
                                val intent = withContext(Dispatchers.IO) { current.shareIntent(context) }
                                context.startActivity(android.content.Intent.createChooser(intent, null))
                            }
                        }
                        if (!current.isVideo) PreviewAction(LucideIcons.Crop, stringResource(R.string.poster_preview_edit),
                            !busy && editingEnabled, actionBackground, actionTint, wide = true) {
                            action {
                                crop = PreviewCrop(current.key, current, requireNotNull(withContext(Dispatchers.IO) { current.readBitmap(context) }))
                                showCrop = true
                            }
                        }
                        PreviewAction(LucideIcons.Info, stringResource(R.string.poster_preview_info), !busy,
                            if (infoOpen) lerp(Color(0xFF202020),
                                if (salt) SaltTheme.colors.text else MiuixTheme.colorScheme.onSurface,
                                progress) else actionBackground,
                            if (infoOpen) lerp(Color.White, panelColor, progress) else actionTint) { setInfo(!infoOpen) }
                    }
                }
            }
            if (busy) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            ImageCropperSheet(show = showCrop, bitmap = crop?.bitmap, title = stringResource(R.string.label_crop_artist_image),
                onCrop = { bitmap ->
                    val target = crop
                    val index = items.indexOfFirst { target != null && it.key == target.key && it == target.media }
                    if (index >= 0) onCrop(index, bitmap)
                    else android.widget.Toast.makeText(context, R.string.poster_operation_failed, android.widget.Toast.LENGTH_LONG).show()
                    onDismiss()
                }, onDismissRequest = { showCrop = false }, onDismissFinished = { if (!showCrop) crop = null })
        }
    }
}
