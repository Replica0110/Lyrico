@file:OptIn(
    com.moriafly.salt.ui.UnstableSaltUiApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.lonx.lyrico.screens

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.base.PillButtonSize
import com.lonx.lyrico.ui.components.salt.SaltIconButton
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.model.entity.capabilities
import com.lonx.lyrico.data.model.entity.displaySourceTypes
import com.lonx.lyrico.data.model.entity.displayName
import com.lonx.lyrico.data.model.entity.isEnabledFor
import com.lonx.lyrico.data.model.entity.sortOrderFor
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import com.lonx.lyrico.data.model.plugin.supportsSourceType
import com.lonx.lyrico.data.model.plugin.displaySourceTypes
import com.lonx.lyrico.plugin.source.PluginInstallCandidate
import com.lonx.lyrico.plugin.source.PluginInstallFailed
import com.lonx.lyrico.plugin.source.PluginVersionConflict
import com.lonx.lyrico.ui.components.base.YesNoBottomSheet
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.lonx.lyrico.ui.components.library.LibraryDetailTabs
import com.lonx.lyrico.ui.components.plugin.*
import com.lonx.lyrico.plugin.source.PluginUpdateState
import com.lonx.lyrico.ui.components.ItemOrderState
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.theme.isDarkTheme
import com.lonx.lyrico.viewmodel.PluginViewModel
import com.lonx.lyrico.viewmodel.PluginMessage
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.PluginConfigDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.icons.Check
import com.moriafly.salt.ui.icons.Uncheck
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Switcher
import com.moriafly.salt.ui.innerPadding
import com.lonx.lyrico.ui.components.salt.SaltConfirmDialog
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File

@Composable
private fun PluginMessage.localizedText(): String = when (this) {
    is PluginMessage.UpdateResult -> if (error == null)
        stringResource(R.string.plugin_update_succeeded, name, version)
    else stringResource(R.string.plugin_update_failed, name, error)
    is PluginMessage.UpdateCheck -> when {
        failures > 0 -> stringResource(R.string.plugin_check_failed, failures)
        count > 0 -> stringResource(R.string.plugin_check_available, count)
        else -> stringResource(R.string.plugin_check_latest)
    }
    is PluginMessage.Failure -> stringResource(R.string.plugin_action_failed, reason)
    is PluginMessage.Status -> stringResource(when (this) {
        PluginMessage.Status.PACKAGE_SCANNED -> R.string.plugin_action_scanned
        PluginMessage.Status.INSTALLED -> R.string.plugin_action_imported
        PluginMessage.Status.DELETED -> R.string.plugin_action_deleted
        PluginMessage.Status.NAME_SAVED -> R.string.plugin_action_name_saved
        PluginMessage.Status.NO_PENDING_IMPORT -> R.string.plugin_action_no_pending
        PluginMessage.Status.NO_SELECTION -> R.string.plugin_action_no_selection
        PluginMessage.Status.BUSY -> R.string.plugin_action_busy
        PluginMessage.Status.WORKING -> R.string.plugin_action_working
        PluginMessage.Status.TIMED_OUT -> R.string.plugin_action_timeout
        PluginMessage.Status.CANNOT_OPEN_FILE -> R.string.plugin_action_cannot_open
        PluginMessage.Status.NO_INSTALLABLE_PLUGIN -> R.string.plugin_action_none_installable
        PluginMessage.Status.CATALOG_CHANGED -> R.string.plugin_catalog_changed
    })
}

private enum class PluginTypeTab(
    val sourceType: PluginSourceType,
    val labelRes: Int
) {
    METADATA(PluginSourceType.METADATA, R.string.plugin_type_metadata),
    LYRICS(PluginSourceType.LYRICS, R.string.plugin_type_lyrics),
    COVER(PluginSourceType.COVER, R.string.plugin_type_cover)
}

@Composable
@Destination<RootGraph>(route = "plugin_manager")
fun PluginManagerScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: PluginViewModel = koinViewModel()
    val plugins by viewModel.plugins.collectAsState()
    val localeConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    LaunchedEffect(localeConfiguration) {
        com.lonx.lyrico.plugin.i18n.PluginLocales.update(localeConfiguration)
    }
    val uiState by viewModel.uiState.collectAsState()
    val updates by viewModel.online.updates.collectAsState()
    var showOnline by rememberSaveable { mutableStateOf(false) }
    var updateCandidate by remember { mutableStateOf<PluginUpdateState?>(null) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.online.checkUpdates() }
    val pendingImport = uiState.pendingImport
    val context: Context = LocalContext.current
    var selectedTypeTab by rememberSaveable { mutableStateOf(PluginTypeTab.METADATA) }
    var compactMode by rememberSaveable {
        mutableStateOf(
            context.getSharedPreferences(
                PLUGIN_MANAGER_PREFERENCES,
                Context.MODE_PRIVATE
            ).getBoolean(KEY_COMPACT_MODE, false)
        )
    }
    val savedPlugins = remember(plugins, selectedTypeTab) {
        plugins
            .filter {
                it.capabilities.supportsSourceType(selectedTypeTab.sourceType)
            }
            .sortedWith(
                compareBy<SourcePluginEntity> {
                    it.sortOrderFor(selectedTypeTab.sourceType)
                }.thenBy { it.displayName }
            )
    }
    val savedOrder = savedPlugins.map { it.id }
    val orderState = remember(selectedTypeTab) { ItemOrderState(savedOrder) }
    val pluginsById = savedPlugins.associateBy { it.id }
    val currentList = orderState.order.mapNotNull { pluginsById[it] }
    LaunchedEffect(savedOrder) { orderState.updateSaved(savedOrder) }
    // Long-lived pointer input callbacks must read the current state and category.
    val saveOrder by rememberUpdatedState {
        viewModel.setPluginOrder(
            orderState.order.mapNotNull { pluginsById[it] },
            selectedTypeTab.sourceType
        )
    }
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderableLazyColumnState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (orderState.move(from.key, to.key)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importPlugin(context, it) }
    }
    val operationMessage = uiState.message?.localizedText()
    LaunchedEffect(uiState.messageVersion) {
        operationMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    var showUninstallDialog by rememberSaveable { mutableStateOf(false) }
    var pendingUninstallPluginId by rememberSaveable { mutableStateOf<String?>(null) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var pendingRenamePluginId by rememberSaveable { mutableStateOf<String?>(null) }
    val nameState = rememberTextFieldState()
    val customNameInput = nameState.text.toString()
    var showImportPreviewSheet by rememberSaveable { mutableStateOf(false) }
    var retainedImportState by remember { mutableStateOf(uiState) }

    LaunchedEffect(uiState) {
        if (pendingImport != null) retainedImportState = uiState
    }

    LaunchedEffect(pendingImport) {
        showImportPreviewSheet = pendingImport != null
    }

    val salt = LocalUiEngine.current == UiEngine.SALT
    val toggleCompactMode: () -> Unit = {
        compactMode = !compactMode
        context.getSharedPreferences(
            PLUGIN_MANAGER_PREFERENCES,
            Context.MODE_PRIVATE
        ).edit().putBoolean(KEY_COMPACT_MODE, compactMode).apply()
    }

    LibraryDetailPage(
        title = stringResource(id = R.string.plugin_manager_title),
        onBack = { navigator.navigateUp() },
        actions = {
            SaltIconButton(
                icon = if (compactMode) LucideIcons.LayoutGrid else LucideIcons.List,
                label = stringResource(
                    if (compactMode) R.string.plugin_switch_to_detailed
                    else R.string.plugin_switch_to_compact
                ),
                onClick = toggleCompactMode
            )
            PluginImportMenu(
                enabled = !uiState.isBusy,
                onLocal = { importLauncher.launch(arrayOf("*/*")) },
                onOnline = { showOnline = true },
                onCheckUpdates = viewModel::checkPluginUpdates
            )
        },
        miuixTopBar = {
            SmallTopAppBar(
                title = stringResource(id = R.string.plugin_manager_title),
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = toggleCompactMode
                    ) {
                        Icon(
                            imageVector = if (compactMode) {
                                LucideIcons.LayoutGrid
                            } else {
                                LucideIcons.List
                            },
                            contentDescription = stringResource(
                                if (compactMode) {
                                    R.string.plugin_switch_to_detailed
                                } else {
                                    R.string.plugin_switch_to_compact
                                }
                            )
                        )
                    }

                    PluginImportMenu(
                        enabled = !uiState.isBusy,
                        onLocal = { importLauncher.launch(arrayOf("*/*")) },
                        onOnline = { showOnline = true },
                        onCheckUpdates = viewModel::checkPluginUpdates
                    )
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (salt) com.lonx.lyrico.ui.components.scaffoldContentPadding(paddingValues)
                    else scaffoldTopHorizontalPadding(paddingValues))
        ) {
            val typeTabs = PluginTypeTab.entries.map { stringResource(it.labelRes) }
            val onTypeTabSelected: (Int) -> Unit = { index ->
                selectedTypeTab = PluginTypeTab.entries[index]
            }

            if (salt) {
                LibraryDetailTabs(
                    tabs = typeTabs,
                    selectedIndex = selectedTypeTab.ordinal,
                    onSelected = onTypeTabSelected
                )
                ItemTip(text = stringResource(R.string.search_source_priority_tip))
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 8.dp)
                ) {
                    TabRowWithContour(
                        tabs = typeTabs,
                        selectedTabIndex = selectedTypeTab.ordinal,
                        onTabSelected = onTypeTabSelected
                    )
                }

                Text(
                    text = stringResource(R.string.search_source_priority_tip),
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.padding(12.dp)
                )
            }

            LazyColumn(
                state = lazyListState,
                modifier = (if (salt) Modifier else Modifier
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection))
                    .fillMaxHeight(),
                contentPadding = PaddingValues(bottom = 12.dp),
                overscrollEffect = if (salt) rememberOverscrollEffect() else null,
            ) {
                if (currentList.isEmpty()) {
                    item("empty") {
                        IllustratedEmptyState(
                            kind = EmptyStateKind.Plugins,
                            title = if (plugins.isEmpty()) {
                                stringResource(R.string.plugin_empty)
                            } else {
                                stringResource(
                                    R.string.plugin_type_empty,
                                    stringResource(selectedTypeTab.labelRes)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            icon = LucideIcons.FileDown,
                            actionText = stringResource(R.string.plugin_import_archive),
                            onAction = {
                                if (!uiState.isBusy) importLauncher.launch(arrayOf("*/*"))
                            },
                        )
                    }
                }

                itemsIndexed(
                    items = currentList,
                    key = { _, plugin -> plugin.id }
                ) { index, plugin ->
                    ReorderableItem(
                        state = reorderableLazyColumnState,
                        key = plugin.id
                    ) { _ ->
                        val interactionSource = remember { MutableInteractionSource() }

                        PluginItem(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = if (compactMode) 6.dp else 12.dp)
                                .clip(
                                    if (salt) com.moriafly.salt.ui.CardDefaults.shape
                                    else RoundedCornerShape(CardDefaults.CornerRadius)
                                )
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        saveOrder()
                                    },
                                    interactionSource = interactionSource
                                ),
                            plugin = plugin,
                            compact = compactMode,
                            enabled = plugin.isEnabledFor(selectedTypeTab.sourceType),
                            update = updates[plugin.id],
                            onUpdate = {
                                updateCandidate = updates[plugin.id]
                                showUpdateSheet = updateCandidate != null
                            },
                            onCancelUpdate = { viewModel.online.cancel(plugin.id) },
                            onUninstall = {
                                pendingUninstallPluginId = plugin.id
                                showUninstallDialog = true
                            },
                            onRename = {
                                pendingRenamePluginId = plugin.id
                                nameState.setTextAndPlaceCursorAtEnd(plugin.customName ?: plugin.name)
                                showRenameDialog = true
                            },
                            onCheckChanged = { enabled ->
                                viewModel.setEnabled(
                                    plugin.id,
                                    selectedTypeTab.sourceType,
                                    enabled
                                )
                            },
                            onConfig = {
                                navigator.navigate(PluginConfigDestination(plugin.id))
                            }
                        )
                    }
                }
            }
        }
    }

    val uninstallMessage = pendingUninstallPluginId?.let { id ->
        plugins.find { it.id == id }?.displayName?.let { name ->
            stringResource(R.string.plugin_uninstall_confirm_message, name)
        }
    }

    if (salt) {
        if (showUninstallDialog) SaltConfirmDialog(
            title = stringResource(R.string.plugin_uninstall),
            summary = uninstallMessage.orEmpty(),
            onDismissRequest = {
                showUninstallDialog = false
                pendingUninstallPluginId = null
            },
            onConfirm = {
                pendingUninstallPluginId?.let { viewModel.uninstallPlugin(it) }
                showUninstallDialog = false
                pendingUninstallPluginId = null
            },
            cancelText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.confirm),
        )
    } else YesNoDialog(
        show = showUninstallDialog,
        title = stringResource(R.string.plugin_uninstall),
        summary = uninstallMessage,
        onDismissRequest = {
            showUninstallDialog = false
        },
        onDismissFinished = {
            showUninstallDialog = false
            pendingUninstallPluginId = null
        },
        onConfirm = {
            pendingUninstallPluginId?.let { viewModel.uninstallPlugin(it) }
            showUninstallDialog = false
            pendingUninstallPluginId = null
        }
    )

    val pendingRenamePlugin = pendingRenamePluginId?.let { id ->
        plugins.find { it.id == id }
    }

    if (salt) {
        if (showRenameDialog) {
            com.lonx.lyrico.ui.components.salt.SaltInputDialog(
                title = stringResource(R.string.plugin_custom_name),
                state = nameState,
                hint = stringResource(
                    R.string.plugin_custom_name_hint,
                    pendingRenamePlugin?.name.orEmpty()
                ),
                onDismissRequest = {
                    showRenameDialog = false
                    pendingRenamePluginId = null
                },
                onConfirm = {
                    pendingRenamePluginId?.let { id ->
                        viewModel.setCustomName(id, nameState.text.toString())
                    }
                    showRenameDialog = false
                    pendingRenamePluginId = null
                },
                cancelText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.confirm),
            )
        }
    } else WindowDialog(
        title = stringResource(R.string.plugin_custom_name),
        show = showRenameDialog,
        onDismissRequest = {
            showRenameDialog = false
        },
        onDismissFinished = {
            pendingRenamePluginId = null
        }
    ) {
        Column {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                state = nameState,
                label = stringResource(
                    R.string.plugin_custom_name_hint,
                    pendingRenamePlugin?.name.orEmpty()
                ),
                useLabelAsPlaceholder = true,
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = {
                        showRenameDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        showRenameDialog = false
                        pendingRenamePluginId?.let { id ->
                            viewModel.setCustomName(id, customNameInput)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }

    PluginSubscriptionsSheet(showOnline, viewModel) { showOnline = false }
    PluginUpdateSheet(show = showUpdateSheet, candidate = updateCandidate,
        installed = plugins.firstOrNull { it.id == updateCandidate?.release?.id },
        error = updateCandidate?.error,
        onDismiss = { showUpdateSheet = false },
        onDismissFinished = { if (!showUpdateSheet) updateCandidate = null }, onConfirm = { selected ->
        viewModel.updatePlugin(selected)
        showUpdateSheet = false
    })

    YesNoBottomSheet(
        show = showImportPreviewSheet,
        scrollContent = true,
        allowDismiss = !uiState.isBusy,
        cancelEnabled = !uiState.isBusy,
        title = stringResource(R.string.plugin_import_found_title),
        onDismissRequest = {
            showImportPreviewSheet = false
        },
        enableNestedScroll = false,
        onDismissFinished = {
            if (!showImportPreviewSheet) {
                viewModel.dismissPendingImport()
                retainedImportState = uiState.copy(pendingImport = null)
            }
        },
        onCancel = {
            showImportPreviewSheet = false
        },
        confirmText = stringResource(when {
            uiState.importComplete -> R.string.plugin_import_done
            uiState.importResult?.failed?.isNotEmpty() == true -> R.string.plugin_online_retry
            else -> R.string.plugin_import_install
        }),
        confirmEnabled = !uiState.isBusy && (uiState.importComplete || uiState.selectedImportRoots.isNotEmpty()),
        onConfirm = {
            if (uiState.importComplete) showImportPreviewSheet = false else viewModel.installPendingImport()
        },
        content = {
            val previewState = if (pendingImport != null) uiState else retainedImportState
            previewState.pendingImport?.let { session ->
                PluginImportResult(previewState.importResult, previewState.importError)
                val completedIds = previewState.importResult?.installed.orEmpty().mapTo(mutableSetOf()) { it.id }
                val remaining = session.candidates.filter { it.manifest.id !in completedIds }
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    if (remaining.isNotEmpty()) {
                        if (salt) ItemOuterTitle(
                            text = stringResource(
                                R.string.plugin_import_installable_title,
                                remaining.size
                            ),
                        )
                        else SmallTitle(
                            text = stringResource(
                                R.string.plugin_import_installable_title,
                                remaining.size
                            ),
                            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }

                    remaining.forEach { candidate ->
                        val selected =
                            candidate.relativeRootInArchive in previewState.selectedImportRoots

                        PluginImportCandidateItem(
                            candidate = candidate,
                            selected = selected,
                            enabled = !previewState.isBusy,
                            onCheckedChange = { checked ->
                                viewModel.setImportCandidateSelected(
                                    candidate.relativeRootInArchive,
                                    checked
                                )
                            }
                        )
                        if (!salt) Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (previewState.importResult == null && session.failed.isNotEmpty()) {
                    if (salt) ItemOuterTitle(
                        text = stringResource(
                            R.string.plugin_import_failed_title,
                            session.failed.size
                        ),
                    )
                    else SmallTitle(
                        text = stringResource(
                            R.string.plugin_import_failed_title,
                            session.failed.size
                        ),
                        textColor = colorScheme.error
                    )

                    session.failed.forEach { failed ->
                        PluginImportFailedItem(failed = failed)
                        if (!salt) Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (salt) Spacer(modifier = Modifier.height(12.dp))
                else Spacer(modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    )
}

@Composable
fun PluginItem(
    plugin: SourcePluginEntity,
    compact: Boolean,
    enabled: Boolean,
    update: PluginUpdateState?,
    onUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    onUninstall: () -> Unit,
    onRename: () -> Unit,
    onCheckChanged: (Boolean) -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (compact) {
        CompactPluginItem(
            update = update,
            onUpdate = onUpdate,
            onCancelUpdate = onCancelUpdate,
            plugin = plugin,
            enabled = enabled,
            onUninstall = onUninstall,
            onRename = onRename,
            onCheckChanged = onCheckChanged,
            onConfig = onConfig,
            modifier = modifier
        )
        return
    }

    val salt = LocalUiEngine.current == UiEngine.SALT
    val actionStyle = if (salt) PillButtonDefaults.style(PillButtonSize.Medium)
        else PillButtonDefaults.style(PillButtonSize.Medium).copy(
            height = 35.dp,
            contentPadding = PaddingValues(horizontal = 10.dp),
            shape = CircleShape,
            textStyle = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
        )
    val actionColors = PillButtonDefaults.colors(
        contentColor = if (salt) SaltTheme.colors.text
            else colorScheme.onSurface.copy(alpha = if (isDarkTheme) 0.7f else 0.9f),
        containerColor = if (salt) SaltTheme.colors.text.copy(alpha = 0.08f)
            else colorScheme.secondaryContainer.copy(alpha = 0.8f)
    )
    val dangerColors = PillButtonDefaults.colors(
        contentColor = if (salt) SaltTheme.colors.error else colorScheme.error,
        containerColor = if (salt) SaltTheme.colors.error.copy(alpha = 0.12f)
            else colorScheme.errorContainer.copy(alpha = 0.45f)
    )

    val pluginId = plugin.id
    val pluginName = plugin.displayName
    val pluginAuthor = plugin.author
    val pluginVersion = plugin.versionName
    val pluginDescription = plugin.description
    val pluginEnabled = enabled
    val pluginTypeText = plugin.displaySourceTypes
        .map { stringResource(it.labelRes()) }
        .joinToString(" / ")

    val hasDescription = pluginDescription.isNotBlank()

    var expanded by rememberSaveable(pluginId) {
        mutableStateOf(false)
    }

    if (salt) {
        com.moriafly.salt.ui.Card(modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (hasDescription) Modifier.clickable { expanded = !expanded } else Modifier)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().innerPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PluginIcon(
                        iconPath = plugin.iconPath,
                        contentDescription = pluginName,
                        size = 40.dp
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            com.moriafly.salt.ui.Text(
                                text = pluginName,
                                style = SaltTheme.textStyles.main,
                                fontWeight = FontWeight.Medium,
                                color = SaltTheme.colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                        }

                        SaltPluginSubText(pluginTypeText)
                        SaltPluginSubText(
                            text = stringResource(R.string.plugin_version_with_value, pluginVersion),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        SaltPluginSubText(
                            text = stringResource(
                                R.string.plugin_api_versions_with_value,
                                plugin.apiVersion,
                                plugin.minHostApiVersion
                            )
                        )
                        SaltPluginSubText(
                            text = stringResource(R.string.plugin_author_with_value, pluginAuthor)
                        )
                    }

                    Switcher(
                        state = pluginEnabled,
                        onChange = { checked ->
                            if (checked != pluginEnabled) onCheckChanged(checked)
                        }
                    )
                }

                if (hasDescription) {
                    Box(
                        modifier = Modifier
                            .innerPadding(vertical = false)
                            .animateContentSize(
                                animationSpec = tween(
                                    durationMillis = 250,
                                    easing = FastOutSlowInEasing
                                )
                            )
                    ) {
                        com.moriafly.salt.ui.Text(
                            text = pluginDescription,
                            style = SaltTheme.textStyles.sub,
                            color = SaltTheme.colors.subText,
                            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                            maxLines = if (expanded) Int.MAX_VALUE else 3
                        )
                    }
                }

                // 分割线和上面的描述拉开距离，否则贴着文字。
                ItemDivider(modifier = Modifier.padding(top = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().innerPadding(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PillButton(
                            text = stringResource(R.string.plugin_config),
                            onClick = onConfig,
                            style = actionStyle,
                            colors = actionColors,
                            leading = {
                                Icon(
                                    imageVector = LucideIcons.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = actionColors.contentColor(selected = false, enabled = true)
                                )
                            }
                        )
                        PillButton(
                            text = stringResource(R.string.plugin_custom_name),
                            onClick = onRename,
                            style = actionStyle,
                            colors = actionColors,
                            leading = {
                                Icon(
                                    imageVector = LucideIcons.Pencil,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = actionColors.contentColor(selected = false, enabled = true)
                                )
                            }
                        )
                        if (update != null) PillButton(
                            text = pluginUpdateActionText(update),
                            onClick = if (update.isDownloading) onCancelUpdate else onUpdate,
                            enabled = !update.isInstalling,
                            style = actionStyle,
                            colors = actionColors,
                            leading = {
                                Icon(
                                    imageVector = if (update.isDownloading) LucideIcons.Close else LucideIcons.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = actionColors.contentColor(selected = false, enabled = !update.isInstalling)
                                )
                            }
                        )
                    }
                    PillButton(
                        text = stringResource(R.string.plugin_uninstall),
                        onClick = onUninstall,
                        style = actionStyle,
                        colors = dangerColors,
                        leading = {
                            Icon(
                                imageVector = LucideIcons.Trash,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = dangerColors.contentColor(selected = false, enabled = true)
                            )
                        }
                    )
                }
            }
        }
        return
    }

    Card(
        modifier = modifier,
        insideMargin = PaddingValues(16.dp),
        onClick = {
            if (hasDescription) expanded = !expanded
        }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PluginIcon(
                iconPath = plugin.iconPath,
                contentDescription = pluginName,
                size = 40.dp
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = pluginName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight(550),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                }

                Text(
                    text = pluginTypeText,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(R.string.plugin_version_with_value, pluginVersion),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(
                        R.string.plugin_api_versions_with_value,
                        plugin.apiVersion,
                        plugin.minHostApiVersion
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(R.string.plugin_author_with_value, pluginAuthor),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 1.dp),
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = pluginEnabled,
                onCheckedChange = { checked ->
                    if (checked != pluginEnabled) {
                        onCheckChanged(checked)
                    }
                }
            )
        }

        if (hasDescription) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = 250,
                            easing = FastOutSlowInEasing
                        )
                    )
            ) {
                Text(
                    text = pluginDescription,
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    maxLines = if (expanded) Int.MAX_VALUE else 3
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 0.5.dp,
            color = colorScheme.outline.copy(alpha = 0.5f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PillButton(
                    text = stringResource(R.string.plugin_config),
                    onClick = onConfig,
                    style = actionStyle,
                    colors = actionColors,
                    leading = {
                        Icon(
                            imageVector = LucideIcons.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = actionColors.contentColor(selected = false, enabled = true)
                        )
                    }
                )
                PillButton(
                    text = stringResource(R.string.plugin_custom_name),
                    onClick = onRename,
                    style = actionStyle,
                    colors = actionColors,
                    leading = {
                        Icon(
                            imageVector = LucideIcons.Pencil,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = actionColors.contentColor(selected = false, enabled = true)
                        )
                    }
                )
                if (update != null) PillButton(
                    text = pluginUpdateActionText(update),
                    onClick = if (update.isDownloading) onCancelUpdate else onUpdate,
                    enabled = !update.isInstalling,
                    style = actionStyle,
                    colors = actionColors,
                    leading = {
                        Icon(
                            imageVector = if (update.isDownloading) LucideIcons.Close else LucideIcons.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = actionColors.contentColor(selected = false, enabled = !update.isInstalling)
                        )
                    }
                )
            }
            PillButton(
                text = stringResource(R.string.plugin_uninstall),
                onClick = onUninstall,
                style = actionStyle,
                colors = dangerColors,
                leading = {
                    Icon(
                        imageVector = LucideIcons.Trash,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = dangerColors.contentColor(selected = false, enabled = true)
                    )
                }
            )
        }
    }
}

@Composable
private fun CompactPluginItem(
    update: PluginUpdateState?,
    onUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    plugin: SourcePluginEntity,
    enabled: Boolean,
    onUninstall: () -> Unit,
    onRename: () -> Unit,
    onCheckChanged: (Boolean) -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pluginName = plugin.displayName
    val versionText = if (update?.isDownloading == true) {
        "${plugin.versionName} · ${update.percent}%"
    } else plugin.versionName
    val pluginTypeText = plugin.displaySourceTypes
        .map { stringResource(it.labelRes()) }
        .joinToString(" / ")
    val salt = LocalUiEngine.current == UiEngine.SALT
    val actionStyle = PillButtonDefaults.style(PillButtonSize.Small).copy(
        height = 30.dp, iconOnlyPadding = PaddingValues(6.dp), shape = CircleShape
    )
    val actionColors = PillButtonDefaults.colors(
        contentColor = if (salt) SaltTheme.colors.text
            else colorScheme.onSurface.copy(alpha = if (isDarkTheme) 0.7f else 0.9f),
        containerColor = if (salt) SaltTheme.colors.text.copy(alpha = 0.08f)
            else colorScheme.secondaryContainer.copy(alpha = 0.8f)
    )
    val dangerColors = PillButtonDefaults.colors(
        contentColor = if (salt) SaltTheme.colors.error else colorScheme.error,
        containerColor = if (salt) SaltTheme.colors.error.copy(alpha = 0.12f)
            else colorScheme.errorContainer.copy(alpha = 0.45f)
    )

    if (salt) {
        com.moriafly.salt.ui.Card(modifier = modifier) {
            Row(
                modifier = Modifier.fillMaxWidth().innerPadding(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PluginIcon(
                    iconPath = plugin.iconPath,
                    contentDescription = pluginName,
                    size = 36.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    com.moriafly.salt.ui.Text(
                        text = pluginName,
                        style = SaltTheme.textStyles.main,
                        fontWeight = FontWeight.Medium,
                        color = SaltTheme.colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    SaltPluginSubText(pluginTypeText)
                    SaltPluginSubText(stringResource(R.string.plugin_version_with_value, versionText))
                    SaltPluginSubText(
                        text = stringResource(
                            R.string.plugin_api_versions_with_value,
                            plugin.apiVersion,
                            plugin.minHostApiVersion
                        )
                    )
                }

                Switcher(
                    state = enabled,
                    onChange = { checked ->
                        if (checked != enabled) onCheckChanged(checked)
                    }
                )
            }

            Row(
                // 底部留出与卡片边缘的距离，操作按钮保持紧凑。
                modifier = Modifier.fillMaxWidth().innerPadding(vertical = false)
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                if (update != null) PillButton(
                    text = pluginUpdateActionText(update),
                    onClick = if (update.isDownloading) onCancelUpdate else onUpdate,
                    enabled = !update.isInstalling,
                    showText = false,
                    style = actionStyle,
                    colors = actionColors,
                    leading = {
                        Icon(
                            imageVector = if (update.isDownloading) LucideIcons.Close else LucideIcons.Download,
                            contentDescription = pluginUpdateActionText(update),
                            modifier = Modifier.size(18.dp),
                            tint = actionColors.contentColor(selected = false, enabled = !update.isInstalling)
                        )
                    }
                )
                PillButton(
                    text = stringResource(R.string.plugin_config),
                    onClick = onConfig,
                    showText = false,
                    style = actionStyle,
                    colors = actionColors,
                    leading = {
                        Icon(
                            imageVector = LucideIcons.Settings,
                            contentDescription = stringResource(R.string.plugin_config),
                            modifier = Modifier.size(18.dp),
                            tint = actionColors.contentColor(selected = false, enabled = true)
                        )
                    }
                )
                PillButton(
                    text = stringResource(R.string.plugin_custom_name),
                    onClick = onRename,
                    showText = false,
                    style = actionStyle,
                    colors = actionColors,
                    leading = {
                        Icon(
                            imageVector = LucideIcons.Pencil,
                            contentDescription = stringResource(R.string.plugin_custom_name),
                            modifier = Modifier.size(18.dp),
                            tint = actionColors.contentColor(selected = false, enabled = true)
                        )
                    }
                )
                PillButton(
                    text = stringResource(R.string.plugin_uninstall),
                    onClick = onUninstall,
                    showText = false,
                    style = actionStyle,
                    colors = dangerColors,
                    leading = {
                        Icon(
                            imageVector = LucideIcons.Trash,
                            contentDescription = stringResource(R.string.plugin_uninstall),
                            modifier = Modifier.size(18.dp),
                            tint = dangerColors.contentColor(selected = false, enabled = true)
                        )
                    }
                )
            }
        }
        return
    }

    Card(
        modifier = modifier,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PluginIcon(
                iconPath = plugin.iconPath,
                contentDescription = pluginName,
                size = 36.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = pluginName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight(550),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                }

                Text(
                    text = pluginTypeText,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(R.string.plugin_version_with_value, versionText),
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(
                        R.string.plugin_api_versions_with_value,
                        plugin.apiVersion,
                        plugin.minHostApiVersion
                    ),
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    if (checked != enabled) onCheckChanged(checked)
                }
            )
        }

        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            if (update != null) PillButton(
                text = pluginUpdateActionText(update),
                onClick = if (update.isDownloading) onCancelUpdate else onUpdate,
                enabled = !update.isInstalling,
                showText = false,
                style = actionStyle,
                colors = actionColors,
                leading = {
                    Icon(
                        imageVector = if (update.isDownloading) LucideIcons.Close else LucideIcons.Download,
                        contentDescription = pluginUpdateActionText(update),
                        modifier = Modifier.size(18.dp),
                        tint = actionColors.contentColor(selected = false, enabled = !update.isInstalling)
                    )
                }
            )
            PillButton(
                text = stringResource(R.string.plugin_config),
                onClick = onConfig,
                showText = false,
                style = actionStyle,
                colors = actionColors,
                leading = {
                    Icon(
                        imageVector = LucideIcons.Settings,
                        contentDescription = stringResource(R.string.plugin_config),
                        modifier = Modifier.size(18.dp),
                        tint = actionColors.contentColor(selected = false, enabled = true)
                    )
                }
            )
            PillButton(
                text = stringResource(R.string.plugin_custom_name),
                onClick = onRename,
                showText = false,
                style = actionStyle,
                colors = actionColors,
                leading = {
                    Icon(
                        imageVector = LucideIcons.Pencil,
                        contentDescription = stringResource(R.string.plugin_custom_name),
                        modifier = Modifier.size(18.dp),
                        tint = actionColors.contentColor(selected = false, enabled = true)
                    )
                }
            )
            PillButton(
                text = stringResource(R.string.plugin_uninstall),
                onClick = onUninstall,
                showText = false,
                style = actionStyle,
                colors = dangerColors,
                leading = {
                    Icon(
                        imageVector = LucideIcons.Trash,
                        contentDescription = stringResource(R.string.plugin_uninstall),
                        modifier = Modifier.size(18.dp),
                        tint = dangerColors.contentColor(selected = false, enabled = true)
                    )
                }
            )
        }
    }
}

private const val PLUGIN_MANAGER_PREFERENCES = "plugin_manager_preferences"
private const val KEY_COMPACT_MODE = "compact_mode"

/** Salt 的副文案行：类型、版本、API 与作者共用同一语义样式。 */
@Composable
private fun SaltPluginSubText(text: String, modifier: Modifier = Modifier) {
    com.moriafly.salt.ui.Text(
        text = text,
        style = SaltTheme.textStyles.sub,
        color = SaltTheme.colors.subText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun pluginUpdateActionText(update: PluginUpdateState?): String = when (update?.phase) {
    PluginUpdateState.Phase.DOWNLOADING -> "${update.percent}%"
    PluginUpdateState.Phase.INSTALLING -> stringResource(R.string.plugin_online_installing)
    PluginUpdateState.Phase.FAILED -> stringResource(R.string.plugin_online_retry)
    else -> stringResource(R.string.plugin_update)
}
