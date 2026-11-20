@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.settings.editing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.editfield.EditFieldDefinition
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import com.lonx.lyrico.data.editfield.toEditFieldBlocks
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.components.ItemOrderState
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.scaffold.PageScaffold
import com.lonx.lyrico.ui.components.scaffold.scaffoldContentPadding
import com.lonx.lyrico.ui.screens.settings.EditFieldSettingsViewModel
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text as SaltText
import com.moriafly.salt.ui.outerPadding
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FieldSongsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

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
    val blocksByKey = remember(blocks) { blocks.associateBy { it.key } }
    val itemsByCode = remember(uiState.items) { uiState.items.associateBy { it.field.code } }
    val selectedComponent = componentKey?.let(blocksByKey::get)

    val scrollBehavior = MiuixScrollBehavior()
    val topBarBackdrop = rememberBarBlurBackdrop()
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    val savedOrder = remember(blocks) { blocks.map { it.key } }
    val orderState = remember { ItemOrderState(savedOrder) }
    val displayCodes = orderState.order
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (orderState.move(from.key, to.key)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(savedOrder) { orderState.updateSaved(savedOrder) }

    val salt = LocalUiEngine.current == UiEngine.SALT

    PageScaffold(
        title = stringResource(R.string.edit_field_settings_title),
        onBack = { navigator.popBackStack() },
        actions = {
            SaltIconButton(
                icon = LucideIcons.RotateCcw,
                label = stringResource(R.string.reset_to_default),
                onClick = { showResetDialog = true },
            )
            SaltIconButton(
                icon = LucideIcons.Plus,
                label = stringResource(R.string.custom_tag_add_key),
                onClick = {
                    viewModel.clearInputError()
                    showAddDialog = true
                },
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
        },
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
                FieldListHint(
                    hint = stringResource(R.string.edit_field_settings_hint),
                    countText = stringResource(
                        R.string.edit_field_settings_enabled_count,
                        uiState.enabledCount,
                        uiState.items.size,
                    ),
                )
            }

            itemsIndexed(
                items = displayCodes,
                key = { _, code -> code },
            ) { index, code ->
                val block = blocksByKey[code] ?: return@itemsIndexed
                val item = block.fields.firstOrNull()?.let { itemsByCode[it.code] } ?: return@itemsIndexed
                val component = block.takeIf { it.isComposite }

                ReorderableItem(state = reorderableState, key = code) {
                    EditFieldRow(
                        item = item,
                        checked = component?.let { uiState.componentOverrides[it.key] ?: true }
                            ?: item.enabled,
                        iconTone = PreferenceIconTone.at(index),
                        onCheckedChange = { checked ->
                            if (component != null) {
                                viewModel.setComponentEnabled(component.key, checked)
                            } else {
                                viewModel.setEnabled(item.field.code, checked)
                            }
                        },
                        onOpenSongs = {
                            navigator.navigate(
                                FieldSongsDestination(
                                    fieldCode = item.field.code,
                                    component = component != null,
                                )
                            )
                        },
                        onDelete = {
                            EditFieldRegistry.customTagKeyOf(item.field.code)
                                ?.let { pendingDeleteKey = it }
                        },
                        dragHandleModifier = Modifier.longPressDraggableHandle(
                            onDragStarted = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragStopped = { viewModel.setBlockOrder(orderState.order) },
                        ),
                        component = component,
                        onManageComponent = {
                            if (component != null) {
                                componentKey = component.key
                                showComponentSheet = true
                            }
                        },
                    )
                }
            }
        }
    }

    AddCustomTagDialog(
        show = showAddDialog,
        availableKeys = uiState.availableKeys,
        error = uiState.inputError,
        onClearInputError = viewModel::clearInputError,
        onDismiss = { showAddDialog = false },
        onDismissFinished = viewModel::clearInputError,
        onConfirm = { typed ->
            scope.launch {
                if (viewModel.addCustomTag(typed)) showAddDialog = false
            }
        },
    )

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
            // 弹层完全关闭后再导航，避免成员列表在退出动画中仍然可见。
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

/** 列表顶部说明与已启用数量；两套主题的分隔与字号不同，文案一致。 */
@Composable
private fun FieldListHint(hint: String, countText: String) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .outerPadding(),
            verticalAlignment = Alignment.Top,
        ) {
            SaltText(
                text = hint,
                color = SaltTheme.colors.subText,
                style = SaltTheme.textStyles.sub,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            SaltText(
                text = countText,
                color = SaltTheme.colors.subText,
                style = SaltTheme.textStyles.sub,
                maxLines = 1,
            )
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = hint,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = countText,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            maxLines = 1,
        )
    }
}
