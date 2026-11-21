@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.search

import android.content.ClipData
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator as MaterialCircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.metadata.StandardPluginField
import com.lonx.lyrico.data.song.search.SongSearchResult
import com.lonx.lyrico.ui.components.sheet.ActionBottomSheet
import com.lonx.lyrico.ui.components.button.PillButton
import com.lonx.lyrico.ui.components.button.PillButtonDefaults
import com.lonx.lyrico.ui.components.button.PillButtonSize
import com.lonx.lyrico.ui.components.sheet.SheetActionBar
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.image.PreviewableImage
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.lyrics.LyricsPreviewPane
import com.lonx.lyrico.ui.components.image.rememberTintedPainter
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.ui.theme.isDarkTheme
import com.moriafly.salt.ui.icons.Check
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.icons.Uncheck
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class SearchResultApplyOption(
    val target: MetadataFieldTarget,
    val value: String,
    val imageSize: Pair<Int, Int>? = null,
    val enabled: Boolean = true
)

/**
 * 搜索结果应用面板：选定一条结果后挑选要写入的字段并应用。
 * 页面负责当前歌曲、歌词状态与回调；勾选状态、面板页签和封面尺寸由面板内部持有。
 */
@Composable
internal fun SearchResultApplyBottomSheet(
    show: Boolean,
    song: SongSearchResult?,
    supportsLyrics: Boolean,
    lyricsState: LyricsUiState,
    showAllFields: Boolean,
    onLoadLyrics: (SongSearchResult) -> Unit,
    onOpenLyricsConfig: () -> Unit,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onApply: (song: SongSearchResult, lyrics: String?, targets: Set<MetadataFieldTarget>) -> Unit
) {
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
        com.lonx.lyrico.data.settings.UiEngine.SALT
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val applyPagerState = rememberPagerState { if (supportsLyrics) 2 else 1 }
    val imageSize by rememberArtworkDimensions(song?.picUrl.orEmpty(), enabled = show)
    var selectedTargets by remember(song?.id, song?.pluginId) {
        mutableStateOf(emptySet<MetadataFieldTarget>())
    }
    var knownTargets by remember(song?.id, song?.pluginId) {
        mutableStateOf(emptySet<MetadataFieldTarget>())
    }

    LaunchedEffect(song?.id, song?.pluginId, supportsLyrics) {
        val targetSong = song ?: return@LaunchedEffect
        applyPagerState.scrollToPage(0)
        if (supportsLyrics) {
            onLoadLyrics(targetSong)
        }
    }

    val lyricsText =
        if (lyricsState.song?.id == song?.id && lyricsState.song?.pluginId == song?.pluginId) {
            lyricsState.content?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    val lyricsLoadingText = stringResource(R.string.lyrics_loading)
    val lyricsLoadFailedText = stringResource(R.string.fetch_lyrics_failed)
    val lyricsEmptyText = stringResource(R.string.lyrics_empty)
    val emptyFieldText = stringResource(R.string.search_result_empty_value)
    val lyricsStatusText = when {
        lyricsState.isLoading -> lyricsLoadingText
        lyricsState.error != null -> lyricsState.error.asString()?.ifBlank { lyricsLoadFailedText }
            ?: lyricsLoadFailedText

        else -> lyricsEmptyText
    }

    val options = remember(
        song,
        lyricsText,
        lyricsState.isLoading,
        lyricsState.error,
        lyricsStatusText,
        imageSize,
        showAllFields,
        emptyFieldText,
        supportsLyrics
    ) {
        val targetSong = song ?: return@remember emptyList()
        val fields = targetSong.normalizedFields().toMutableMap()
        lyricsText?.let { fields["lyrics"] = it }

        val fieldOptions = StandardPluginField.entries
            .asSequence()
            .filter { it.target != MetadataFieldTarget.LYRICS }
            .sortedBy { field -> if (field.target == MetadataFieldTarget.COVER) 0 else 1 }
            .mapNotNull { field ->
                val value = fields[field.key].orEmpty()
                when {
                    value.isNotBlank() -> SearchResultApplyOption(
                        target = field.target,
                        value = value,
                        imageSize = if (field.target == MetadataFieldTarget.COVER) imageSize else null
                    )

                    showAllFields -> SearchResultApplyOption(
                        target = field.target,
                        value = emptyFieldText
                    )

                    else -> null
                }
            }
            .distinctBy { it.target }
            .toMutableList()

        if (supportsLyrics && lyricsText != null) {
            fieldOptions += SearchResultApplyOption(
                target = MetadataFieldTarget.LYRICS,
                value = lyricsText
            )
        } else if (supportsLyrics && (
            showAllFields ||
            lyricsState.isLoading ||
            lyricsState.error != null ||
            lyricsState.song != null
        )) {
            fieldOptions += SearchResultApplyOption(
                target = MetadataFieldTarget.LYRICS,
                value = if (showAllFields && lyricsState.song == null) {
                    emptyFieldText
                } else {
                    lyricsStatusText
                }
            )
        }

        fieldOptions.sortedBy { if (it.target == MetadataFieldTarget.COVER) 0 else 1 }
    }

    LaunchedEffect(options) {
        val targets = options.filter { it.enabled }.map { it.target }.toSet()
        val newTargets = targets - knownTargets
        selectedTargets = when {
            knownTargets.isEmpty() -> targets
            else -> (selectedTargets intersect targets) + newTargets
        }
        knownTargets = targets
    }

    val enabledTargets = options.filter { it.enabled }.map { it.target }.toSet()
    val allSelected = enabledTargets.isNotEmpty() && selectedTargets.containsAll(enabledTargets)
    val dataOptions = options.filter { it.target != MetadataFieldTarget.LYRICS }
    val lyricsOption = options.firstOrNull { it.target == MetadataFieldTarget.LYRICS }

    val toggleAll = {
        selectedTargets = if (allSelected) emptySet() else enabledTargets
    }
    val applySelection = {
        song?.let { onApply(it, lyricsText?.takeIf { MetadataFieldTarget.LYRICS in selectedTargets }, selectedTargets) }
        Unit
    }
    val applyLyricsOnly = {
        song?.let {
            selectedTargets = setOf(MetadataFieldTarget.LYRICS)
            if (lyricsText.isNullOrBlank()) onLoadLyrics(it)
            else onApply(it, lyricsText, setOf(MetadataFieldTarget.LYRICS))
        }
        Unit
    }
    val renderPage: @Composable (Int) -> Unit = { page ->
        when (page) {
            0 -> SearchResultApplyDataPage(
                options = dataOptions,
                selectedTargets = selectedTargets,
                onSelectedTargetsChange = { selectedTargets = it }
            )

            1 -> SearchResultApplyLyricsPage(
                option = lyricsOption,
                selected = MetadataFieldTarget.LYRICS in selectedTargets,
                lyricsText = lyricsText,
                lyricsState = lyricsState,
                loadingText = lyricsLoadingText,
                failedText = lyricsLoadFailedText,
                onSelectedChange = { selected ->
                    if (lyricsOption?.enabled == true) {
                        selectedTargets = if (selected) {
                            selectedTargets + MetadataFieldTarget.LYRICS
                        } else {
                            selectedTargets - MetadataFieldTarget.LYRICS
                        }
                    }
                },
                onOpenLyricsConfig = onOpenLyricsConfig,
                onCopyLyrics = {
                    scope.launch {
                        val clipData = ClipData.newPlainText("copy lyrics", lyricsText)
                        val clipEntry = ClipEntry(clipData)
                        clipboardManager.setClipEntry(clipEntry)
                    }
                }
            )
        }
    }
    val actions: @Composable () -> Unit = {
        val buttonStyle = PillButtonDefaults.style(if (salt) PillButtonSize.Medium else PillButtonSize.Large)
        SheetActionBar(
            leading = {
                PillButton(
                    text = stringResource(if (allSelected) R.string.action_deselect_all else R.string.action_select_all),
                    onClick = toggleAll,
                    style = buttonStyle,
                )
            },
            trailing = {
                if (supportsLyrics) {
                    PillButton(
                        text = stringResource(R.string.apply_lyrics_only_action),
                        enabled = !lyricsState.isLoading && song != null,
                        leading = if (lyricsState.isLoading) {
                            {
                                MaterialCircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = if (salt) com.moriafly.salt.ui.SaltTheme.colors.highlight
                                        else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    strokeWidth = 2.dp,
                                )
                            }
                        } else null,
                        onClick = applyLyricsOnly,
                        style = buttonStyle,
                    )
                }
                PillButton(
                    text = stringResource(R.string.apply_action),
                    enabled = song != null && selectedTargets.isNotEmpty(),
                    onClick = applySelection,
                    style = buttonStyle,
                )
            },
        )
    }
    ActionBottomSheet(
        show = show,
        title = song?.title,
        scrollContent = false,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        enableNestedScroll = false,
        tools = actions,
        content = {
            Column(Modifier.fillMaxWidth()) {
                if (supportsLyrics) {
                    Box(if (salt) Modifier.padding(horizontal = 16.dp, vertical = 8.dp) else Modifier) {
                        ApplySheetPillTabs(
                            selectedTabIndex = applyPagerState.targetPage,
                            onTabSelected = { index ->
                                scope.launch {
                                    applyPagerState.animateScrollToPage(index)
                                }
                            }
                        )
                    }
                }
                HorizontalPager(
                    state = applyPagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (salt) Modifier.weight(1f, fill = false).heightIn(max = 420.dp)
                        else Modifier.height(420.dp))
                ) { page ->
                    renderPage(page)
                    Spacer(modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    )
}

@Composable
private fun ApplySheetPillTabs(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        stringResource(R.string.search_apply_tab_metadata),
        stringResource(R.string.label_lyrics)
    )
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
            com.lonx.lyrico.data.settings.UiEngine.SALT

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedTabIndex
            PillButton(
                text = label,
                selected = selected,
                onClick = { onTabSelected(index) },
                style = if (salt) PillButtonDefaults.style(PillButtonSize.Medium)
                else PillButtonDefaults.style(PillButtonSize.Medium).copy(
                    textStyle = MiuixTheme.textStyles.body2.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    selectedTextStyle = MiuixTheme.textStyles.body2.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            )
        }
    }
}

@Composable
private fun SearchResultApplyDataPage(
    options: List<SearchResultApplyOption>,
    selectedTargets: Set<MetadataFieldTarget>,
    onSelectedTargetsChange: (Set<MetadataFieldTarget>) -> Unit
) {
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current == com.lonx.lyrico.data.settings.UiEngine.SALT
    val listContent: @Composable () -> Unit = {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (salt) 0.dp else 8.dp)
        ) {
            if (options.isEmpty()) {
                item("empty") {
                    IllustratedEmptyState(
                        kind = EmptyStateKind.Search,
                        title = stringResource(R.string.search_no_results),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                items(options, key = { it.target.name }) { option ->
                    SearchResultApplyOptionItem(
                        option = option,
                        selected = option.enabled && option.target in selectedTargets,
                        onSelectedChange = { selected ->
                            if (!option.enabled) return@SearchResultApplyOptionItem
                            onSelectedTargetsChange(
                                if (selected) {
                                    selectedTargets + option.target
                                } else {
                                    selectedTargets - option.target
                                }
                            )
                        }
                    )
                }
            }
        }
    }
    if (salt) com.moriafly.salt.ui.RoundedColumn(Modifier.fillMaxSize()) { listContent() }
    else listContent()

}

@Composable
private fun SearchResultApplyLyricsPage(
    option: SearchResultApplyOption?,
    selected: Boolean,
    lyricsText: String?,
    lyricsState: LyricsUiState,
    loadingText: String,
    failedText: String,
    onSelectedChange: (Boolean) -> Unit,
    onOpenLyricsConfig: () -> Unit,
    onCopyLyrics: () -> Unit
) {
    if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == com.lonx.lyrico.data.settings.UiEngine.SALT) {
        // 歌词选中行和歌词正文是同一个 Item：同一个 RoundedColumn，不再拆成两块。
        com.moriafly.salt.ui.RoundedColumn(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                com.moriafly.salt.ui.ItemCheck(
                    text = stringResource(R.string.label_lyrics), state = selected && option?.enabled == true,
                    enabled = option?.enabled == true, onChange = onSelectedChange,
                    modifier = Modifier.weight(1f),
                )
                com.lonx.lyrico.ui.components.button.salt.SaltIconButton(
                    icon = LucideIcons.Copy, label = stringResource(R.string.action_copy_lyrics),
                    onClick = onCopyLyrics, enabled = !lyricsText.isNullOrBlank(),
                    size = 32.dp, iconSize = 18.dp,
                )
                Box(Modifier.padding(end = 8.dp)) {
                    com.lonx.lyrico.ui.components.button.salt.SaltIconButton(
                        icon = LucideIcons.Settings, label = stringResource(R.string.search_settings),
                        onClick = onOpenLyricsConfig, size = 32.dp, iconSize = 18.dp,
                    )
                }
            }
            LyricsPreviewPane(
                lyricsText = lyricsText, isLoading = lyricsState.isLoading, error = lyricsState.error,
                loadingText = loadingText, failedText = failedText,
                emptyText = stringResource(R.string.lyrics_empty), onCopyLyrics = onCopyLyrics,
                // 与上方 ItemCheck 的文字左缘对齐；RoundedColumn 不提供内容内边距。
                modifier = Modifier.weight(1f).fillMaxWidth(),
                showCopyAction = false,
                contentPadding = PaddingValues(horizontal = 16.dp),
            )
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
                    com.lonx.lyrico.data.settings.UiEngine.SALT
            Column(modifier = Modifier.weight(1f)) {
                if (salt) {
                    com.moriafly.salt.ui.Text(
                        text = stringResource(R.string.label_lyrics),
                        style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                        fontWeight = FontWeight.SemiBold,
                        color = com.moriafly.salt.ui.SaltTheme.colors.text
                    )
                    com.moriafly.salt.ui.Text(
                        text = option?.value ?: stringResource(R.string.lyrics_empty),
                        style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                        color = com.moriafly.salt.ui.SaltTheme.colors.subText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = stringResource(R.string.label_lyrics),
                        style = MiuixTheme.textStyles.main,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurfaceContainer
                    )
                    Text(
                        text = option?.value ?: stringResource(R.string.lyrics_empty),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (salt) {
                com.lonx.lyrico.ui.components.button.salt.SaltIconButton(
                    icon = LucideIcons.Settings,
                    label = "",
                    onClick = onOpenLyricsConfig,
                    size = 36.dp,
                    iconSize = 20.dp
                )
            } else IconButton(onClick = onOpenLyricsConfig) {
                Icon(
                    imageVector = LucideIcons.Settings,
                    contentDescription = null
                )
            }
            if (salt) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable(enabled = option?.enabled == true) {
                            if (option?.enabled == true) {
                                onSelectedChange(!selected)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    com.moriafly.salt.ui.Icon(
                        painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(
                            if (selected && option?.enabled == true) com.moriafly.salt.ui.icons.SaltIcons.Check
                            else com.moriafly.salt.ui.icons.SaltIcons.Uncheck
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = com.moriafly.salt.ui.SaltTheme.colors.highlight
                    )
                }
            } else Checkbox(
                state = if (selected && option?.enabled == true) ToggleableState.On else ToggleableState.Off,
                onClick = {
                    if (option?.enabled == true) {
                        onSelectedChange(!selected)
                    }
                }
            )
        }

        LyricsPreviewPane(
            lyricsText = lyricsText,
            isLoading = lyricsState.isLoading,
            error = lyricsState.error,
            loadingText = loadingText,
            failedText = failedText,
            emptyText = stringResource(R.string.lyrics_empty),
            onCopyLyrics = onCopyLyrics,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SearchResultApplyOptionItem(
    option: SearchResultApplyOption,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    val isCover = option.target == MetadataFieldTarget.COVER
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
            com.lonx.lyrico.data.settings.UiEngine.SALT

    if (salt) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .toggleable(value = selected, enabled = option.enabled, role = Role.Checkbox,
                    onValueChange = onSelectedChange)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCover) PreviewableImage(
                model = option.value, contentDescription = stringResource(R.string.cd_cover),
                onClick = { if (option.enabled) onSelectedChange(!selected) },
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                    .background(LyricoColors.coverPlaceholder), contentScale = ContentScale.Crop,
                placeholder = rememberTintedPainter(painterResource(R.drawable.ic_album_24dp), LyricoColors.coverPlaceholderIcon),
                error = rememberTintedPainter(painterResource(R.drawable.ic_album_24dp), LyricoColors.coverPlaceholderIcon),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                com.moriafly.salt.ui.Text(stringResource(option.target.labelRes),
                    style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                    color = if (option.enabled) com.moriafly.salt.ui.SaltTheme.colors.text
                        else com.moriafly.salt.ui.SaltTheme.colors.subText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val valueText = if (isCover && option.imageSize != null)
                    "${option.imageSize.first}×${option.imageSize.second}" else option.value
                com.moriafly.salt.ui.Text(valueText,
                    style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                    color = com.moriafly.salt.ui.SaltTheme.colors.subText,
                    maxLines = if (isCover) 1 else 2, overflow = TextOverflow.Ellipsis)
            }
            com.moriafly.salt.ui.Icon(
                painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(
                    if (selected) SaltIcons.Check else SaltIcons.Uncheck),
                contentDescription = null, modifier = Modifier.size(20.dp),
                tint = if (option.enabled) com.moriafly.salt.ui.SaltTheme.colors.highlight
                    else com.moriafly.salt.ui.SaltTheme.colors.subText,
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (option.enabled) {
                    onSelectedChange(!selected)
                }
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isCover) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LyricoColors.coverPlaceholder)
            ) {
                PreviewableImage(
                    model = option.value,
                    onClick = { if (option.enabled) onSelectedChange(!selected) },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberTintedPainter(
                        painter = painterResource(R.drawable.ic_album_24dp),
                        tint = LyricoColors.coverPlaceholderIcon
                    ),
                    error = rememberTintedPainter(
                        painter = painterResource(R.drawable.ic_album_24dp),
                        tint = LyricoColors.coverPlaceholderIcon
                    )
                )
                option.imageSize?.let { size ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        if (salt) com.moriafly.salt.ui.SaltTheme.colors.subBackground
                                        else MiuixTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            )
                    ) {
                        Text(
                            text = "${size.first}×${size.second}",
                            color = if (isDarkTheme) Color.Black else Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            val labelText = stringResource(option.target.labelRes)
            if (salt) {
                com.moriafly.salt.ui.Text(
                    text = labelText,
                    style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                    fontWeight = FontWeight.SemiBold,
                    color = if (option.enabled) com.moriafly.salt.ui.SaltTheme.colors.text
                    else com.moriafly.salt.ui.SaltTheme.colors.subText
                )
                com.moriafly.salt.ui.Text(
                    text = option.value,
                    style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                    color = com.moriafly.salt.ui.SaltTheme.colors.subText,
                    maxLines = if (option.target == MetadataFieldTarget.LYRICS) 4 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = labelText,
                    style = MiuixTheme.textStyles.main,
                    fontWeight = FontWeight.SemiBold,
                    color = if (option.enabled) {
                        MiuixTheme.colorScheme.onSurfaceContainer
                    } else {
                        MiuixTheme.colorScheme.onSurfaceContainerVariant
                    }
                )
                Text(
                    text = option.value,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    maxLines = if (option.target == MetadataFieldTarget.LYRICS) 4 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (salt) {
            // SaltUI 没有独立 Checkbox；沿用官方 SaltIcons 勾选图标，语义与 Miuix 一致。
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(enabled = option.enabled) {
                        if (option.enabled) {
                            onSelectedChange(!selected)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                com.moriafly.salt.ui.Icon(
                    painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(
                        if (selected) com.moriafly.salt.ui.icons.SaltIcons.Check
                        else com.moriafly.salt.ui.icons.SaltIcons.Uncheck
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = com.moriafly.salt.ui.SaltTheme.colors.highlight
                )
            }
        } else Checkbox(
            state = if (selected) ToggleableState.On else ToggleableState.Off,
            onClick = {
                if (option.enabled) {
                    onSelectedChange(!selected)
                }
            }
        )
    }
}
