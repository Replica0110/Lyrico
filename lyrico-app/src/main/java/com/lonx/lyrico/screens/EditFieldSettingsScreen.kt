@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens

import com.lonx.lyrico.ui.components.salt.SaltTextInput

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.salt.SaltBottomSheet
import com.lonx.lyrico.ui.components.salt.SaltIconButton
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.data.editfield.EditFieldBlock
import com.lonx.lyrico.data.editfield.EditFieldDefinition
import com.lonx.lyrico.data.editfield.EditFieldListItem
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import com.lonx.lyrico.data.editfield.toEditFieldBlocks
import com.lonx.lyrico.ui.components.ItemOrderState
import com.lonx.lyrico.ui.components.ManagedChip
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.base.PillButtonSize
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.CustomTagKeyError
import com.lonx.lyrico.viewmodel.EditFieldSettingsViewModel
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Switcher
import com.moriafly.salt.ui.dialog.BasicDialog
import com.moriafly.salt.ui.dialog.DialogTitle
import com.moriafly.salt.ui.dialog.YesNoDialog as SaltYesNoDialog
import com.moriafly.salt.ui.innerPadding
import com.moriafly.salt.ui.outerPadding
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.screen.ScreenCard
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FieldSongsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.window.WindowListPopup

/** 内置字段与自定义标签共用一份可排序列表。 */
@Composable
@Destination<RootGraph>(route = "edit_field_settings")
fun EditFieldSettingsScreen(
    navigator: DestinationsNavigator,
) {
    val viewModel: EditFieldSettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDeleteKey by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    var componentKey by remember { mutableStateOf<String?>(null) }
    var showComponentSheet by remember { mutableStateOf(false) }
    var pendingFieldSongs by remember { mutableStateOf<EditFieldDefinition?>(null) }
    val blocks = remember(uiState.items) { uiState.items.map { it.field }.toEditFieldBlocks() }
    val selectedComponent = blocks.firstOrNull { it.key == componentKey }

    val scrollBehavior = MiuixScrollBehavior()
    val topBarBackdrop = rememberBarBlurBackdrop()
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    val savedOrder = blocks.map { it.key }
    val orderState = remember { ItemOrderState(savedOrder) }
    val displayCodes = orderState.order
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (orderState.move(from.key, to.key)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(savedOrder) { orderState.updateSaved(savedOrder) }

    val salt = LocalUiEngine.current == UiEngine.SALT

    LibraryDetailPage(
        title = stringResource(R.string.edit_field_settings_title),
        onBack = { navigator.popBackStack() },
        actions = {
            // Salt 原生顶栏槽位：恢复默认与新增自定义标签。
            SaltIconButton(
                icon = LucideIcons.RotateCcw,
                label = stringResource(R.string.reset_to_default),
                onClick = { showResetDialog = true }
            )
            SaltIconButton(
                icon = LucideIcons.Plus,
                label = stringResource(R.string.custom_tag_add_key),
                onClick = {
                    viewModel.clearInputError()
                    showAddDialog = true
                }
            )
        },
        miuixTopBar = {
            BlurredTopBar(backdrop = topBarBackdrop) {
                SmallTopAppBar(
                    title = stringResource(R.string.edit_field_settings_title),
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        IconButton(onClick = { navigator.popBackStack() }) {
                            Icon(LucideIcons.ArrowLeft, stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showResetDialog = true }) {
                            Icon(LucideIcons.RotateCcw, stringResource(R.string.reset_to_default))
                        }
                        IconButton(onClick = {
                            viewModel.clearInputError()
                            showAddDialog = true
                        }) {
                            Icon(
                                LucideIcons.Plus,
                                contentDescription = stringResource(R.string.custom_tag_add_key),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = (if (salt) Modifier else Modifier
                .blurSource(topBarBackdrop)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection))
                .fillMaxHeight(),
            contentPadding = scaffoldContentPadding(
                paddingValues = paddingValues,
                bottomExtra = 12.dp,
            ),
            overscrollEffect = if (salt) rememberOverscrollEffect() else null,
        ) {
            item(key = "hint") {
                val hintText = stringResource(R.string.edit_field_settings_hint)
                val countText = stringResource(
                    R.string.edit_field_settings_enabled_count,
                    uiState.enabledCount,
                    uiState.items.size,
                )
                if (salt) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .outerPadding(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        com.moriafly.salt.ui.Text(
                            text = hintText,
                            color = SaltTheme.colors.subText,
                            style = SaltTheme.textStyles.sub,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        com.moriafly.salt.ui.Text(
                            text = countText,
                            color = SaltTheme.colors.subText,
                            style = SaltTheme.textStyles.sub,
                            maxLines = 1,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = hintText,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = countText,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            maxLines = 1,
                        )
                    }
                }
            }

            items(
                items = displayCodes,
                key = { code -> code },
            ) { code ->
                val block = blocks.firstOrNull { it.key == code }
                val item = uiState.items.firstOrNull { it.field.code == block?.fields?.firstOrNull()?.code }

                ReorderableItem(state = reorderableState, key = code) {
                    if (item != null) {
                        EditFieldRow(
                            item = item,
                            iconTone = PreferenceIconTone.at(displayCodes.indexOf(code)),
                            component = block?.takeIf { it.isComposite },
                            checked = if (block?.isComposite == true) uiState.componentOverrides[block.key] ?: true else item.enabled,
                            onManageComponent = {
                                componentKey = block?.key
                                showComponentSheet = true
                            },
                            onCheckedChange = { checked ->
                                if (block?.isComposite == true) viewModel.setComponentEnabled(block.key, checked)
                                else viewModel.setEnabled(item.field.code, checked)
                            },
                            onDelete = {
                                EditFieldRegistry.customTagKeyOf(item.field.code)
                                    ?.let { pendingDeleteKey = it }
                            },
                            onOpenSongs = {
                                navigator.navigate(FieldSongsDestination(
                                    fieldCode = item.field.code,
                                    component = block?.isComposite == true,
                                ))
                            },
                            dragHandleModifier = Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    viewModel.setBlockOrder(orderState.order)
                                },
                            ),
                        )
                    }
                }
            }


        }
    }

    AddCustomTagDialog(
        show = showAddDialog,
        availableKeys = uiState.availableKeys,
        error = uiState.inputError,
        onAddAvailable = viewModel::addAvailableKey,
        onDismiss = { showAddDialog = false },
        onDismissFinished = viewModel::clearInputError,
        onConfirm = { typed ->
            scope.launch {
                if (viewModel.addCustomTag(typed)) showAddDialog = false
            }
        },
    )

    if (salt) {
        if (pendingDeleteKey != null) SaltYesNoDialog(
            title = stringResource(R.string.custom_tag_delete_title),
            content = stringResource(R.string.custom_tag_delete_message, pendingDeleteKey.orEmpty()),
            onDismissRequest = { pendingDeleteKey = null },
            onConfirm = {
                pendingDeleteKey?.let(viewModel::removeCustomTag)
                pendingDeleteKey = null
            },
            cancelText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.common_delete),
        )
        if (showResetDialog) SaltYesNoDialog(
            title = stringResource(R.string.reset_to_default),
            content = stringResource(R.string.edit_field_settings_reset_message),
            onDismissRequest = { showResetDialog = false },
            onConfirm = {
                viewModel.resetAll()
                orderState.resetPending()
                showResetDialog = false
            },
            cancelText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.confirm),
        )
    } else {
        YesNoDialog(
            show = pendingDeleteKey != null,
            title = stringResource(R.string.custom_tag_delete_title),
            summary = stringResource(R.string.custom_tag_delete_message, pendingDeleteKey.orEmpty()),
            confirmText = stringResource(R.string.common_delete),
            onDismissRequest = { pendingDeleteKey = null },
            onConfirm = {
                pendingDeleteKey?.let(viewModel::removeCustomTag)
                pendingDeleteKey = null
            },
        )

        YesNoDialog(
            show = showResetDialog,
            title = stringResource(R.string.reset_to_default),
            summary = stringResource(R.string.edit_field_settings_reset_message),
            confirmText = stringResource(R.string.confirm),
            onDismissRequest = { showResetDialog = false },
            onConfirm = {
                viewModel.resetAll()
                orderState.resetPending()
                showResetDialog = false
            },
        )
    }

    ComponentFieldsBottomSheet(
        show = showComponentSheet,
        component = selectedComponent,
        items = uiState.items,
        onEnabledChange = viewModel::setEnabled,
        onOrderChange = { codes -> componentKey?.let { viewModel.setComponentOrder(it, codes) } },
        onOpenSongs = { field ->
            pendingFieldSongs = field
            showComponentSheet = false
        },
        onDismissRequest = { showComponentSheet = false },
        onDismissFinished = {
            if (!showComponentSheet) {
                componentKey = null
                pendingFieldSongs?.let { field ->
                    navigator.navigate(FieldSongsDestination(fieldCode = field.code))
                }
                pendingFieldSongs = null
            }
        },
    )
}

/** 自定义标签的键名；标准字段没有键名，返回空串。 */
private fun EditFieldDefinition?.customTagKey(): String =
    this?.let { EditFieldRegistry.customTagKeyOf(it.code) }.orEmpty()

/** 歌曲列表的标题：自定义标签用键名，标准字段用字段名。 */
@Composable
private fun EditFieldDefinition?.displayTitle(): String = when {
    this == null -> ""
    custom -> customTagKey()
    else -> stringResource(titleRes)
}

@Composable
private fun EditFieldRow(
    item: EditFieldListItem,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onOpenSongs: () -> Unit,
    dragHandleModifier: Modifier,
    component: EditFieldBlock? = null,
    checked: Boolean = item.enabled,
    onManageComponent: () -> Unit = {},
    inSheet: Boolean = false,
    iconTone: PreferenceIconTone = PreferenceIconTone.Tools,
) {
    var showMenu by remember { mutableStateOf(false) }
    val menuItems = buildList<Triple<String, ImageVector, () -> Unit>> {
        if (component != null) add(Triple(stringResource(R.string.edit_field_manage_members), LucideIcons.Settings, onManageComponent))
        add(Triple(stringResource(R.string.edit_field_view_songs), LucideIcons.ListMusic, onOpenSongs))
        if (item.field.custom) add(Triple(stringResource(R.string.common_delete), LucideIcons.Trash, onDelete))
    }
    if (LocalUiEngine.current == UiEngine.SALT) {
        // Salt 行：拖拽把手不变，操作菜单换成原生弹出菜单，密度按 Salt 的 item 规格。
        val saltContent: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth().then(dragHandleModifier).innerPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                com.moriafly.salt.ui.Icon(
                    painter = rememberVectorPainter(
                        if (component?.isComposite == true) LucideIcons.Volume2 else item.field.settingsIcon()
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(SaltTheme.dimens.itemIcon),
                    tint = iconTone.color(),
                )
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    com.moriafly.salt.ui.Text(
                        text = component?.title() ?: item.field.displayTitle(),
                        modifier = Modifier.weight(1f, fill = false),
                        style = SaltTheme.textStyles.main,
                        color = SaltTheme.colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (component != null || item.field.custom) {
                        FieldTagChip(stringResource(
                            if (component != null) R.string.edit_field_component_short_tag
                            else R.string.edit_field_custom_short_tag
                        ))
                    }
                }
                Box {
                    SaltIconButton(
                        icon = LucideIcons.EllipsisVertical,
                        label = stringResource(R.string.cd_more_actions),
                        onClick = { showMenu = true },
                        size = 36.dp,
                        iconSize = 20.dp,
                    )
                    PopupMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        menuItems.forEachIndexed { index, (label, icon, action) ->
                            PopupMenuItem(
                                text = label,
                                iconPainter = rememberVectorPainter(icon),
                                iconColor = if (icon == LucideIcons.Trash) SaltTheme.colors.error
                                else PreferenceIconTone.at(index).color(),
                                onClick = {
                                    showMenu = false
                                    action()
                                },
                            )
                        }
                    }
                }
                Switcher(state = checked, onChange = onCheckedChange)
            }
        }
        if (inSheet) saltContent() else com.moriafly.salt.ui.Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
        ) { saltContent() }
        return
    }
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().then(dragHandleModifier)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreferenceIcon(
                imageVector = if (component?.isComposite == true) LucideIcons.Volume2 else item.field.settingsIcon(),
                tone = iconTone,
                modifier = Modifier,
            )
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = component?.title() ?: item.field.displayTitle(),
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (component != null || item.field.custom) {
                    FieldTagChip(stringResource(
                        if (component != null) R.string.edit_field_component_short_tag
                        else R.string.edit_field_custom_short_tag
                    ))
                }
            }
            Box {
                PillButton(
                    text = stringResource(R.string.cd_more_actions),
                    showText = false,
                    style = PillButtonDefaults.style(PillButtonSize.Small),
                    onClick = { showMenu = true },
                    leading = { Icon(LucideIcons.EllipsisVertical, stringResource(R.string.cd_more_actions), Modifier.size(20.dp)) },
                )
                WindowListPopup(
                    show = showMenu,
                    popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                    onDismissRequest = { showMenu = false },
                    alignment = PopupPositionProvider.Align.End,
                ) {
                    ListPopupColumn {
                        menuItems.forEachIndexed { index, (label, icon, action) ->
                            DropdownImpl(
                                item = top.yukonga.miuix.kmp.basic.DropdownItem(
                                    text = label,
                                    icon = { modifier ->
                                        PreferenceIcon(icon,
                                            if (icon == LucideIcons.Trash) PreferenceIconTone.Danger else PreferenceIconTone.at(index),
                                            modifier = modifier)
                                    },
                                ),
                                isSelected = false,
                                optionSize = menuItems.size,
                                index = index,
                                onSelectedIndexChange = {
                                    showMenu = false
                                    action()
                                },
                            )
                        }
                    }
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
    if (inSheet) content() else Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        insideMargin = PaddingValues(0.dp),
    ) { content() }
}

@Composable
private fun EditFieldBlock.title(): String = stringResource(
    if (isComposite) R.string.group_replay_gain else fields.first().titleRes
)

private fun EditFieldDefinition.settingsIcon(): ImageVector = when (code) {
    "picture" -> LucideIcons.Image
    "rating" -> LucideIcons.Star
    "title" -> LucideIcons.Music
    "artist", "album_artist" -> LucideIcons.Users
    "album", "disc_number" -> LucideIcons.Disc
    "date" -> LucideIcons.Calendar
    "language" -> LucideIcons.Languages
    "genre" -> LucideIcons.Tags
    "track_number" -> LucideIcons.ListMusic
    "lyricist" -> LucideIcons.NotebookPen
    "composer" -> LucideIcons.Music
    "copyright" -> LucideIcons.Copyright
    "comment" -> LucideIcons.MessageSquare
    "track_gain", "album_gain", "reference_loudness" -> LucideIcons.Volume2
    "track_peak", "album_peak" -> LucideIcons.Activity
    "lyrics" -> LucideIcons.Captions
    "lyrics_offset" -> LucideIcons.ArrowDownUp
    else -> LucideIcons.Tags
}

@Composable
private fun ComponentFieldsBottomSheet(
    show: Boolean,
    component: EditFieldBlock?,
    items: List<EditFieldListItem>,
    onEnabledChange: (String, Boolean) -> Unit,
    onOrderChange: (List<String>) -> Unit,
    onOpenSongs: (EditFieldDefinition) -> Unit,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltBottomSheet(
            show = show,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            header = { ItemOuterTitle(text = component?.title().orEmpty()) },
        ) {
            ComponentFieldsSheetContent(
                component = component,
                items = items,
                onEnabledChange = onEnabledChange,
                onOrderChange = onOrderChange,
                onOpenSongs = onOpenSongs,
                salt = true,
            )
        }
        return
    }
    WindowBottomSheet(
        show = show,
        title = component?.title().orEmpty(),
        enableNestedScroll = false,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
    ) {
        ComponentFieldsSheetContent(
            component = component,
            items = items,
            onEnabledChange = onEnabledChange,
            onOrderChange = onOrderChange,
            onOpenSongs = onOpenSongs,
            salt = false,
        )
    }
}

@Composable
private fun ComponentFieldsSheetContent(
    component: EditFieldBlock?,
    items: List<EditFieldListItem>,
    onEnabledChange: (String, Boolean) -> Unit,
    onOrderChange: (List<String>) -> Unit,
    onOpenSongs: (EditFieldDefinition) -> Unit,
    salt: Boolean,
) {
    val savedCodes = component?.fields?.map { it.code }.orEmpty()
    val orderState = remember(component?.key) { ItemOrderState(savedCodes) }
    val displayCodes = orderState.order
    val latestOnOrderChange by rememberUpdatedState(onOrderChange)
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (orderState.move(from.key, to.key)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(savedCodes) { orderState.updateSaved(savedCodes) }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (salt) Modifier else Modifier.padding(bottom = 32.dp))
    ) {
        if (salt) com.moriafly.salt.ui.Text(
            text = stringResource(R.string.edit_field_component_hint),
            style = SaltTheme.textStyles.sub,
            color = SaltTheme.colors.subText,
            modifier = Modifier.innerPadding(vertical = false),
        )
        else Text(
            stringResource(R.string.edit_field_component_hint),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(8.dp))
        val listContent: @Composable () -> Unit = {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                overscrollEffect = if (salt) rememberOverscrollEffect() else null,
            ) {
                items(displayCodes, key = { it }) { code ->
                    val item = items.firstOrNull { it.field.code == code }
                    ReorderableItem(reorderState, key = code) {
                        if (item != null) EditFieldRow(
                            item = item,
                            iconTone = PreferenceIconTone.at(displayCodes.indexOf(code)),
                            inSheet = true,
                            onCheckedChange = { onEnabledChange(code, it) },
                            onDelete = {},
                            onOpenSongs = { onOpenSongs(item.field) },
                            dragHandleModifier = Modifier.longPressDraggableHandle(
                                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDragStopped = { latestOnOrderChange(orderState.order) },
                            ),
                        )
                    }
                }
            }
        }
        if (salt) RoundedColumn { listContent() }
        else Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
        ) { listContent() }
    }
}

/** 字段行上的小标记，用来把自定义标签和内置字段区分开。 */
@Composable
private fun FieldTagChip(text: String) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        com.moriafly.salt.ui.Text(
            text = text,
            style = SaltTheme.textStyles.sub,
            color = SaltTheme.colors.highlight,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SaltTheme.colors.highlight.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        return
    }
    Text(
        text = text,
        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCustomTagDialog(
    show: Boolean,
    availableKeys: List<String>,
    error: CustomTagKeyError?,
    onAddAvailable: (String) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var typed by remember(show) { mutableStateOf("") }

    if (LocalUiEngine.current == UiEngine.SALT) {
        // Salt 弹窗同步销毁，用“曾显示”标记在关闭后补发一次完成回调。
        var wasShown by remember { mutableStateOf(false) }
        LaunchedEffect(show) {
            if (show) {
                wasShown = true
            } else if (wasShown) {
                wasShown = false
                onDismissFinished()
            }
        }
        val errorText = when (error) {
            null -> stringResource(R.string.custom_tag_key_hint)
            CustomTagKeyError.EMPTY -> stringResource(R.string.invalid_empty_input)
            CustomTagKeyError.INVALID -> stringResource(R.string.custom_tag_key_invalid)
            CustomTagKeyError.DUPLICATE -> stringResource(R.string.duplicate_item)
        }
        com.lonx.lyrico.ui.components.salt.SaltConfirmDialog(
            show = show, title = stringResource(R.string.custom_tag_add_key),
            onDismissRequest = onDismiss, onConfirm = { onConfirm(typed) },
            cancelText = stringResource(R.string.cancel), confirmText = stringResource(R.string.confirm),
            dismissOnConfirm = false,
        ) {
            com.moriafly.salt.ui.Text(
                text = stringResource(
                    if (availableKeys.isEmpty()) {
                        R.string.custom_tag_library_empty
                    } else {
                        R.string.custom_tag_library_section
                    }
                ),
                color = SaltTheme.colors.subText,
                style = SaltTheme.textStyles.sub,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (availableKeys.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableKeys.forEach { key ->
                        ManagedChip(text = key, onClick = { onAddAvailable(key) })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                SaltTextInput(
                    text = typed,
                    onChange = { typed = it },
                    hint = stringResource(R.string.custom_tag_key),
                )
            }
            Spacer(Modifier.height(6.dp))
            com.moriafly.salt.ui.Text(
                text = errorText,
                color = if (error == null) SaltTheme.colors.subText else SaltTheme.colors.error,
                style = SaltTheme.textStyles.sub,
            )
        }
        return
    }

    WindowDialog(
        show = show,
        title = stringResource(R.string.custom_tag_add_key),
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(
                    if (availableKeys.isEmpty()) {
                        R.string.custom_tag_library_empty
                    } else {
                        R.string.custom_tag_library_section
                    }
                ),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            )

            if (availableKeys.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableKeys.forEach { key ->
                        ManagedChip(text = key, onClick = { onAddAvailable(key) })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TextField(
                value = typed,
                onValueChange = { typed = it },
                label = stringResource(R.string.custom_tag_key),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (error) {
                    null -> stringResource(R.string.custom_tag_key_hint)
                    CustomTagKeyError.EMPTY -> stringResource(R.string.invalid_empty_input)
                    CustomTagKeyError.INVALID -> stringResource(R.string.custom_tag_key_invalid)
                    CustomTagKeyError.DUPLICATE -> stringResource(R.string.duplicate_item)
                },
                color = if (error == null) {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                } else {
                    MiuixTheme.colorScheme.error
                },
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = { onConfirm(typed) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
