package com.lonx.lyrico.ui.screens.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.type
import com.lonx.lyrico.R
import com.lonx.lyrico.data.artwork.ArtistPosterReader
import com.lonx.lyrico.data.artwork.CoverSourceType
import com.lonx.lyrico.data.artwork.getCoverSourceType
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.data.artwork.CoverRequest
import com.lonx.lyrico.domain.poster.ResolvedArtistPoster
import com.lonx.lyrico.ui.components.PagerDotsIndicator
import com.lonx.lyrico.ui.components.image.ImageViewerItem
import com.lonx.lyrico.ui.components.image.PreviewableImage
import com.lonx.lyrico.ui.components.image.resolveForPreview
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.theme.LyricoColors
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.URL

/** 艺术家海报描述对不上任何艺术家时，角标的警示底色。 */
private val ArtistImageUnmatchedBadgeColor = Color(0xFFB45309).copy(alpha = 0.9f)

/** 当前正在裁剪的目标。 */
internal sealed interface CropRequest {
    data object Cover : CropRequest

    /** 只替换这一张艺术家图片的数据，保留它的归属。 */
    data class ArtistImage(val target: AudioPicture) : CropRequest
    data class ExternalArtistImage(val target: ResolvedArtistPoster.File) : CropRequest
}

/** 这张艺术家海报和原始标签里的同名海报是否已经不同（新增或数据/描述被改过）。 */
internal fun isArtistPictureModified(
    picture: AudioPicture,
    originals: List<AudioPicture>
): Boolean = originals.none {
    it.type == picture.type &&
        it.description == picture.description &&
        it.data.contentEquals(picture.data)
}

internal data class PicturePagerItem(
    val label: String,
    val editLabel: String,
    val source: Any?,
    val isModified: Boolean,
    /** 描述对不上任何已有艺术家，标签会用警示色提示。 */
    val isUnmatched: Boolean = false,
    /** 图片来源说明（内嵌在标签里 / 来自外置海报文件夹），显示在尺寸旁边。 */
    val sourceLabel: String? = null,
    val onClick: () -> Unit,
    val onRevertClick: () -> Unit
)

@Composable
internal fun CoverSection(
    coverUri: Any?,
    title: String,
    artist: String,
    pages: List<PicturePagerItem>,
    pagerState: PagerState,
) {
    val surfaceVariant = MiuixTheme.colorScheme.surfaceVariant
    val onSurface = MiuixTheme.colorScheme.onSurface
    val onSurfaceDim = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val context = LocalContext.current
    val posterReader: ArtistPosterReader = org.koin.compose.koinInject()
    val picturePages = pages
    val pagerScope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage.coerceIn(0, picturePages.lastIndex)
    val viewerCandidates = picturePages.mapIndexedNotNull { index, picture ->
        ImageViewerItem.from(picture.source, picture.label)?.copy(key = "page:$index")
    }
    val viewerItems by key(viewerCandidates) {
        produceState(emptyList<ImageViewerItem>(), viewerCandidates) {
            value = viewerCandidates.mapNotNull { it.resolveForPreview(context, posterReader) }
        }
    }
    val currentImageSource = picturePages[currentPage].source
    var imageSize by remember(currentImageSource) { mutableStateOf<Pair<Int, Int>?>(null) }

    // 加载图片尺寸
    LaunchedEffect(currentImageSource) {
        if (currentImageSource != null) {
            imageSize = withContext(Dispatchers.IO) {
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

                    if (currentImageSource is CoverRequest) {
                        // 外置海报是文件夹里的文件，UI 手里没有字节：按文件名读出来量一下尺寸。
                        // 只有当前这一页会走到这里，读的是与取图相同的那份文件。
                        val bytes = posterReader.resolve(currentImageSource)?.bytes
                        if (bytes != null && bytes.isNotEmpty()) {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        }
                    } else {
                        when (getCoverSourceType(currentImageSource)) {
                            CoverSourceType.BYTE_ARRAY -> {
                                val bytes = currentImageSource as ByteArray
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                            }

                            CoverSourceType.BITMAP -> {
                                val bitmap = currentImageSource as Bitmap
                                return@withContext bitmap.width to bitmap.height
                            }

                            CoverSourceType.NETWORK_URL -> {
                                val source = currentImageSource.toString().trim()
                                URL(source).openStream().use { stream ->
                                    BitmapFactory.decodeStream(stream, null, options)
                                }
                            }

                            CoverSourceType.CONTENT_OR_FILE_URI,
                            CoverSourceType.URI -> {
                                val uri = when (currentImageSource) {
                                    is Uri -> currentImageSource
                                    is String -> currentImageSource.trim().toUri()
                                    else -> null
                                }
                                uri?.let {
                                    context.contentResolver.openInputStream(it)?.use { stream ->
                                        BitmapFactory.decodeStream(stream, null, options)
                                    }
                                }
                            }

                            CoverSourceType.FILE_PATH -> {
                                BitmapFactory.decodeFile(currentImageSource.toString().trim(), options)
                            }

                            CoverSourceType.UNSUPPORTED -> {}
                        }
                    }

                    if (options.outWidth > 0 && options.outHeight > 0) {
                        options.outWidth to options.outHeight
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        } else {
            imageSize = null
        }
    }

    if (LocalUiEngine.current == UiEngine.SALT) {
        // 两个引擎共用图片角标排列，尺寸与来源分行显示。
        RoundedColumn {
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                AsyncImage(
                    model = currentImageSource ?: coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = 0.15f }
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    SaltTheme.colors.subBackground.copy(alpha = 0.6f),
                                    SaltTheme.colors.subBackground.copy(alpha = 0.95f)
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SaltTheme.colors.subText.copy(alpha = 0.1f))
                        ) { page ->
                            val item = picturePages[page]
                            Box(Modifier.fillMaxSize().clickable { item.onClick() }) {
                                PreviewableImage(
                                    model = item.source,
                                    onClick = item.onClick,
                                    viewerItems = viewerItems,
                                    viewerInitialPage = viewerItems.indexOfFirst { it.key == "page:$page" }.coerceAtLeast(0),
                                    contentDescription = item.editLabel,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize(),
                                    placeholder = painterResource(id = R.drawable.ic_album_24dp),
                                    error = painterResource(id = R.drawable.ic_album_24dp)
                                )
                                PictureOverlayBadges(item, page == currentPage, imageSize)
                            }
                        }
                        if (picturePages.size > 1) {
                            Spacer(Modifier.height(8.dp))
                            PagerDotsIndicator(
                                pageCount = picturePages.size,
                                currentPage = currentPage,
                                activeColor = SaltTheme.colors.text,
                                inactiveColor = SaltTheme.colors.subText.copy(alpha = 0.4f),
                                pageDescriptions = picturePages.map { it.label },
                                onPageClick = { target ->
                                    pagerScope.launch { pagerState.animateScrollToPage(target) }
                                }
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        com.moriafly.salt.ui.Text(
                            text = title.ifEmpty { stringResource(R.string.edit_metadata_default_title) },
                            style = SaltTheme.textStyles.main,
                            color = SaltTheme.colors.text,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (artist.isNotEmpty()) {
                            com.moriafly.salt.ui.Text(
                                text = artist,
                                style = SaltTheme.textStyles.sub,
                                color = SaltTheme.colors.subText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {

                AsyncImage(
                    model = currentImageSource ?: coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = 0.15f }
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    surfaceVariant.copy(alpha = 0.6f),
                                    surfaceVariant.copy(alpha = 0.95f)
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MiuixTheme.colorScheme.onSurfaceContainerVariant)
                        ) { page ->
                            val item = picturePages[page]
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { item.onClick() }
                            ) {
                                PreviewableImage(
                                    model = item.source,
                                    onClick = item.onClick,
                                    viewerItems = viewerItems,
                                    viewerInitialPage = viewerItems.indexOfFirst { it.key == "page:$page" }.coerceAtLeast(0),
                                    contentDescription = item.editLabel,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize(),
                                    placeholder = rememberTintedPainter(
                                        painter = painterResource(id = R.drawable.ic_album_24dp),
                                        tint = LyricoColors.coverPlaceholderIcon
                                    ),
                                    error = rememberTintedPainter(
                                        painter = painterResource(id = R.drawable.ic_album_24dp),
                                        tint = LyricoColors.coverPlaceholderIcon
                                    )
                                )

                                PictureOverlayBadges(item, page == currentPage, imageSize)
                            }
                        }
                        if (picturePages.size > 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            PagerDotsIndicator(
                                pageCount = picturePages.size,
                                currentPage = currentPage,
                                pageDescriptions = picturePages.map { it.label },
                                onPageClick = { target ->
                                    pagerScope.launch { pagerState.animateScrollToPage(target) }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title.ifEmpty { "未知曲目" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (artist.isNotEmpty()) {
                            Text(
                                text = artist,
                                fontSize = 13.sp,
                                color = onSurfaceDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                    }
                }
            }
        }
    }
}

/** 类型与撤销分列上方，尺寸、来源分行放在下方；各角标随内容收缩。 */
@Composable
private fun BoxScope.PictureOverlayBadges(
    item: PicturePagerItem,
    isCurrentPage: Boolean,
    imageSize: Pair<Int, Int>?,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) {
                PictureBadge(
                    text = item.label,
                    background = if (item.isUnmatched) ArtistImageUnmatchedBadgeColor
                    else Color.Black.copy(alpha = 0.6f),
                )
            }
            AnimatedVisibility(
                visible = item.isModified,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Box(
                    modifier = Modifier.padding(start = 4.dp)
                        .clip(CircleShape)
                        .background(LyricoColors.modifiedBadgeBackground.copy(alpha = 0.95f))
                        .clickable { item.onRevertClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    PictureBadgeText(
                        text = stringResource(R.string.action_undo),
                        color = LyricoColors.modifiedText,
                    )
                }
            }
        }
        val info = listOfNotNull(
            imageSize?.takeIf { isCurrentPage }?.let { "${it.first}×${it.second}" },
            // 外部文件是候选来源；成功读到图片尺寸后才确认展示，避免空位误报来源。
            item.sourceLabel?.takeIf { isCurrentPage && imageSize != null },
        ).joinToString("\n")
        if (info.isNotEmpty()) PictureBadge(text = info, fontSize = 9.sp)
    }
}

@Composable
private fun PictureBadge(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Color.Black.copy(alpha = 0.6f),
    fontSize: TextUnit = 10.sp,
) {
    Box(
        modifier.background(background, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        PictureBadgeText(text, Color.White, fontSize)
    }
}

/** 角标文字：按当前引擎取原生文本控件，字号、字重与颜色保持一致。 */
@Composable
private fun PictureBadgeText(text: String, color: Color, fontSize: TextUnit = 10.sp) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        com.moriafly.salt.ui.Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
