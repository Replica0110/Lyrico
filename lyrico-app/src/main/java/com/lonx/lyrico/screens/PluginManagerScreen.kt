@file:OptIn(
    com.moriafly.salt.ui.UnstableSaltUiApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.lonx.lyrico.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
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
import com.lonx.lyrico.ui.components.plugin.PluginIcon
import com.lonx.lyrico.ui.components.ItemOrderState
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.theme.isDarkTheme
import com.lonx.lyrico.viewmodel.PluginViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.PluginConfigDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.ButtonIntent
import com.moriafly.salt.ui.ControlSize
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.ItemCheck
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Switcher
import com.moriafly.salt.ui.innerPadding
import com.moriafly.salt.ui.screen.ScreenCard
import com.moriafly.salt.ui.dialog.YesNoDialog as SaltYesNoDialog
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
import top.yukonga.miuix.kmp.basic.Scaffold
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
    LaunchedEffect(uiState.messageVersion) {
        if (uiState.message.isNotBlank()) {
            Toast.makeText(context, uiState.message, Toast.LENGTH_SHORT).show()
        }
    }
    var showUninstallDialog by rememberSaveable { mutableStateOf(false) }
    var pendingUninstallPluginId by rememberSaveable { mutableStateOf<String?>(null) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var pendingRenamePluginId by rememberSaveable { mutableStateOf<String?>(null) }
    var customNameInput by rememberSaveable { mutableStateOf("") }
    var showImportPreviewSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(pendingImport) {
        if (pendingImport != null) {
            showImportPreviewSheet = true
        }
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
            // Salt 原生顶栏槽位：紧凑模式切换与导入。
            SaltIconButton(
                icon = if (compactMode) LucideIcons.LayoutGrid else LucideIcons.List,
                label = stringResource(
                    if (compactMode) R.string.plugin_switch_to_detailed
                    else R.string.plugin_switch_to_compact
                ),
                onClick = toggleCompactMode
            )
            SaltIconButton(
                icon = LucideIcons.Plus,
                label = stringResource(R.string.plugin_import_archive),
                enabled = !uiState.isBusy,
                onClick = { importLauncher.launch(arrayOf("*/*")) }
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

                    IconButton(
                        onClick = {
                            if (!uiState.isBusy) importLauncher.launch(arrayOf("*/*"))
                        }
                    ) {
                        Icon(
                            imageVector = LucideIcons.Plus,
                            contentDescription = null
                        )
                    }
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
                            updateUrl = "",
                            onUninstall = {
                                pendingUninstallPluginId = plugin.id
                                showUninstallDialog = true
                            },
                            onRename = {
                                pendingRenamePluginId = plugin.id
                                customNameInput = plugin.customName ?: plugin.name
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
        if (showUninstallDialog) SaltYesNoDialog(
            title = stringResource(R.string.plugin_uninstall),
            content = uninstallMessage.orEmpty(),
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
            val nameState = rememberTextFieldState(initialText = customNameInput)
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
                value = customNameInput,
                label = stringResource(R.string.plugin_custom_name),
                singleLine = true,
                onValueChange = { customNameInput = it }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.plugin_custom_name_hint,
                    pendingRenamePlugin?.name.orEmpty()
                ),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary
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

    YesNoBottomSheet(
        show = showImportPreviewSheet,
        saltScrollContent = true,
        title = stringResource(R.string.plugin_import_found_title),
        onDismissRequest = {
            viewModel.discardPendingImportFiles()
            showImportPreviewSheet = false
        },
        enableNestedScroll = false,
        onDismissFinished = {
            if (!showImportPreviewSheet) {
                viewModel.clearPendingImport()
            }
        },
        onCancel = {
            viewModel.discardPendingImportFiles()
            showImportPreviewSheet = false
        },
        confirmText = stringResource(R.string.plugin_import_install),
        onConfirm = {
            viewModel.installPendingImport()
            showImportPreviewSheet = false
        },
        content = {
            pendingImport?.let { session ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (salt) Modifier else Modifier.verticalScroll(rememberScrollState())),
                ) {
                    if (session.candidates.isNotEmpty()) {
                        if (salt) com.moriafly.salt.ui.Text(
                            text = stringResource(
                                R.string.plugin_import_installable_title,
                                session.candidates.size
                            ),
                            style = SaltTheme.textStyles.sub,
                            color = SaltTheme.colors.subText,
                            modifier = Modifier.innerPadding(vertical = false)
                        )
                        else SmallTitle(
                            text = stringResource(
                                R.string.plugin_import_installable_title,
                                session.candidates.size
                            ),
                            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }

                    session.candidates.forEach { candidate ->
                        val selected =
                            candidate.relativeRootInArchive in uiState.selectedImportRoots

                        PluginImportCandidateItem(
                            candidate = candidate,
                            selected = selected,
                            onCheckedChange = { checked ->
                                viewModel.setImportCandidateSelected(
                                    candidate.relativeRootInArchive,
                                    checked
                                )
                            }
                        )
                        if (salt) ItemDivider() else Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (session.failed.isNotEmpty()) {
                    if (salt) com.moriafly.salt.ui.Text(
                        text = stringResource(
                            R.string.plugin_import_failed_title,
                            session.failed.size
                        ),
                        style = SaltTheme.textStyles.sub,
                        color = SaltTheme.colors.error,
                        modifier = Modifier.innerPadding(vertical = false)
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
                        if (salt) ItemDivider() else Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (salt) Spacer(modifier = Modifier.height(12.dp))
                else Spacer(modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    )
}


@Composable
private fun PluginImportCandidateItem(
    candidate: PluginInstallCandidate,
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val localeTags = androidx.compose.ui.platform.LocalConfiguration.current.locales.toLanguageTags()
    val manifest by androidx.compose.runtime.produceState(candidate.manifest, candidate, localeTags) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                com.lonx.lyrico.plugin.i18n.PluginStrings.load(candidate.pluginRoot, candidate.manifest)
                    .snapshot(localeTags.split(',')).localize(candidate.manifest)
            }.getOrDefault(candidate.manifest)
        }
    }
    val conflictText = candidate.versionConflict.toImportConflictText()
    val conflictColor = candidate.versionConflict.toImportConflictColor()
    val iconPath = manifest.icon?.let { File(candidate.pluginRoot, it).absolutePath }
    val versionText = if (candidate.existingPlugin != null) {
        "${candidate.existingPlugin.versionName} -> ${manifest.versionName}"
    } else {
        manifest.versionName
    }
    val locationText = candidate.relativeRootInArchive.ifBlank { "/" }
    val pluginTypeText = candidate.manifest.capabilities
        .displaySourceTypes()
        .map { stringResource(it.labelRes()) }
        .joinToString(" / ")

    if (LocalUiEngine.current == UiEngine.SALT) {
        // Salt 的复选行只有文本槽位，图标改由元数据行承载，冲突状态保留在首行。
        val summary = buildString {
            append(conflictText)
            append(" · ")
            append(stringResource(R.string.plugin_import_version, versionText))
            append("\n")
            append(manifest.id)
            append("\n")
            append(stringResource(R.string.plugin_type_with_value, pluginTypeText))
            append("\n")
            append(stringResource(R.string.plugin_import_path, locationText))
            if (manifest.description.isNotBlank()) {
                append("\n")
                append(stringResource(R.string.plugin_import_description, manifest.description))
            }
        }
        ItemCheck(
            state = selected,
            onChange = { onCheckedChange(it) },
            text = manifest.name,
            sub = summary
        )
        return
    }

    Card(
        colors = CardDefaults.defaultColors(
            color = colorScheme.secondaryContainer,
        )
    ) {
        BasicComponent(
            insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            onClick = {
                onCheckedChange(!selected)
            },
            startAction = {
                PluginIcon(
                    iconPath = iconPath,
                    contentDescription = manifest.name,
                    size = 34.dp
                )
            },
            endActions = {
                Checkbox(
                    state = if (selected) ToggleableState.On else ToggleableState.Off,
                    onClick = {
                        onCheckedChange(!selected)
                    }
                )
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = manifest.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                ImportStatusBadge(
                    text = conflictText,
                    color = conflictColor
                )
            }

            Text(
                text = manifest.id,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = stringResource(R.string.plugin_import_version, versionText),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.plugin_type_with_value, pluginTypeText),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.plugin_import_path,locationText),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (manifest.description.isNotBlank()) {
                Text(
                    text = stringResource(R.string.plugin_import_description,manifest.description),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

    }
}

@Composable
private fun PluginImportFailedItem(
    failed: PluginInstallFailed
) {
    var expanded by rememberSaveable(
        failed.rootPath,
        failed.reason,
        failed.pluginId,
        failed.versionName
    ) {
        mutableStateOf(false)
    }

    val title = failed.displayName

    val subtitle = buildString {
        failed.pluginId
            ?.takeIf { it.isNotBlank() && it != title }
            ?.let { append(it) }

        failed.versionName
            ?.takeIf { it.isNotBlank() }
            ?.let {
                if (isNotEmpty()) append("\n")
                append(stringResource(R.string.plugin_import_version))
                append(" ")
                append(it)
            }

        if (failed.rootPath.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append(failed.rootPath)
        }
    }

    val versionChange = buildString {
        val oldVersion = failed.existingVersionName
        val newVersion = failed.versionName
        if (!oldVersion.isNullOrBlank() && !newVersion.isNullOrBlank()) {
            append(oldVersion)
            append(" -> ")
            append(newVersion)
        }
    }

    if (LocalUiEngine.current == UiEngine.SALT) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Item(
                onClick = { expanded = !expanded },
                text = title,
                sub = subtitle.ifBlank { null },
                textColor = SaltTheme.colors.error,
                subColor = SaltTheme.colors.error.copy(alpha = 0.82f),
                tag = if (expanded) stringResource(R.string.plugin_import_collapse)
                else stringResource(R.string.plugin_import_expand),
                arrowType = ItemArrowType.None,
            )
            if (versionChange.isNotBlank()) com.moriafly.salt.ui.Text(
                text = versionChange,
                style = SaltTheme.textStyles.sub,
                color = SaltTheme.colors.error.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.innerPadding(vertical = false)
            )
            com.moriafly.salt.ui.Text(
                text = failed.reason,
                style = SaltTheme.textStyles.sub,
                color = SaltTheme.colors.error,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = Modifier.innerPadding(vertical = false)
            )
        }
        return
    }

    Card(
        colors = CardDefaults.defaultColors(
            color = colorScheme.errorContainer
        )
    ) {
        BasicComponent(
            onClick = {
                expanded = !expanded
            },
            modifier = Modifier.animateContentSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onErrorContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                failed.conflict?.let { conflict ->
                    ImportStatusBadge(
                        text = conflict.toImportConflictText(),
                        color = colorScheme.error
                    )
                }

                Text(
                    text = if (expanded) {
                        stringResource(R.string.plugin_import_collapse)
                    } else {
                        stringResource(R.string.plugin_import_expand)
                    },
                    fontSize = 12.sp,
                    color = colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colorScheme.onErrorContainer.copy(alpha = 0.82f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (versionChange.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = versionChange,
                    fontSize = 12.sp,
                    color = colorScheme.onErrorContainer.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = failed.reason,
                fontSize = 12.sp,
                color = colorScheme.onErrorContainer,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
private fun ImportStatusBadge(
    text: String,
    color: Color
) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun PluginVersionConflict.toImportConflictColor(): Color {
    return when (this) {
        PluginVersionConflict.NONE -> colorScheme.primary
        PluginVersionConflict.UPDATE -> colorScheme.primary
        PluginVersionConflict.OVERWRITE -> colorScheme.onTertiaryContainer
        PluginVersionConflict.DOWNGRADE -> colorScheme.error
    }
}

@Composable
private fun PluginVersionConflict.toImportConflictText(): String {
    return when (this) {
        PluginVersionConflict.NONE ->
            stringResource(R.string.plugin_import_conflict_new)

        PluginVersionConflict.UPDATE ->
            stringResource(R.string.plugin_import_conflict_update)

        PluginVersionConflict.OVERWRITE ->
            stringResource(R.string.plugin_import_conflict_overwrite)

        PluginVersionConflict.DOWNGRADE ->
            stringResource(R.string.plugin_import_conflict_downgrade)
    }
}

@Composable
fun PluginItem(
    plugin: SourcePluginEntity,
    compact: Boolean,
    enabled: Boolean,
    updateUrl: String,
    onUninstall: () -> Unit,
    onRename: () -> Unit,
    onCheckChanged: (Boolean) -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (compact) {
        CompactPluginItem(
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

    val secondaryContainer = colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val actionIconTint = colorScheme.onSurface.copy(alpha = if (isDarkTheme) 0.7f else 0.9f)

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
    val hasUpdateSource = updateUrl.isNotBlank()

    var expanded by rememberSaveable(pluginId) {
        mutableStateOf(false)
    }

    if (LocalUiEngine.current == UiEngine.SALT) {
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

                            if (hasUpdateSource) {
                                SaltPluginBadge(
                                    text = stringResource(R.string.plugin_update_source_configured)
                                )
                            }
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左侧操作可换行，卸载始终靠右。
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SaltPluginActionButton(
                            text = stringResource(R.string.plugin_config),
                            icon = LucideIcons.Settings,
                            onClick = onConfig
                        )
                        SaltPluginActionButton(
                            text = stringResource(R.string.plugin_custom_name),
                            icon = LucideIcons.Pencil,
                            onClick = onRename
                        )
                    }
                    SaltPluginActionButton(
                        text = stringResource(R.string.plugin_uninstall),
                        icon = LucideIcons.Trash,
                        danger = true,
                        onClick = onUninstall
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

                    if (hasUpdateSource) {
                        PluginBadge(
                            text = stringResource(R.string.plugin_update_source_configured)
                        )
                    }

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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PluginActionChip(
                text = stringResource(R.string.plugin_config),
                icon = LucideIcons.Settings,
                tint = actionIconTint,
                background = secondaryContainer,
                onClick = onConfig
            )
            PluginActionChip(
                text = stringResource(R.string.plugin_custom_name),
                icon = LucideIcons.Pencil,
                tint = actionIconTint,
                background = secondaryContainer,
                onClick = onRename
            )

            Spacer(modifier = Modifier.weight(1f))

            PluginActionChip(
                text = stringResource(R.string.plugin_uninstall),
                icon = LucideIcons.Trash,
                tint = colorScheme.error,
                background = colorScheme.errorContainer.copy(alpha = 0.45f),
                onClick = onUninstall
            )
        }
    }
}

@Composable
private fun CompactPluginItem(
    plugin: SourcePluginEntity,
    enabled: Boolean,
    onUninstall: () -> Unit,
    onRename: () -> Unit,
    onCheckChanged: (Boolean) -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pluginName = plugin.displayName
    val pluginTypeText = plugin.displaySourceTypes
        .map { stringResource(it.labelRes()) }
        .joinToString(" / ")
    val neutralActionBackground = colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val neutralActionTint = colorScheme.onSurface.copy(alpha = if (isDarkTheme) 0.7f else 0.9f)

    if (LocalUiEngine.current == UiEngine.SALT) {
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
                    SaltPluginSubText(stringResource(R.string.plugin_version_with_value, plugin.versionName))
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
                SaltIconButton(
                    icon = LucideIcons.Settings,
                    label = stringResource(R.string.plugin_config),
                    onClick = onConfig,
                    size = 30.dp,
                    iconSize = 18.dp,
                    backgroundColor = SaltTheme.colors.text.copy(alpha = 0.08f)
                )
                SaltIconButton(
                    icon = LucideIcons.Pencil,
                    label = stringResource(R.string.plugin_custom_name),
                    onClick = onRename,
                    size = 30.dp,
                    iconSize = 18.dp,
                    backgroundColor = SaltTheme.colors.text.copy(alpha = 0.08f)
                )
                SaltIconButton(
                    icon = LucideIcons.Trash,
                    label = stringResource(R.string.plugin_uninstall),
                    onClick = onUninstall,
                    tint = SaltTheme.colors.error,
                    size = 30.dp,
                    iconSize = 18.dp,
                    backgroundColor = SaltTheme.colors.error.copy(alpha = 0.12f)
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
                    text = stringResource(R.string.plugin_version_with_value, plugin.versionName),
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
            CompactPluginActionButton(
                icon = LucideIcons.Settings,
                contentDescription = stringResource(R.string.plugin_config),
                tint = neutralActionTint,
                background = neutralActionBackground,
                onClick = onConfig
            )
            CompactPluginActionButton(
                icon = LucideIcons.Pencil,
                contentDescription = stringResource(R.string.plugin_custom_name),
                tint = neutralActionTint,
                background = neutralActionBackground,
                onClick = onRename
            )
            CompactPluginActionButton(
                icon = LucideIcons.Trash,
                contentDescription = stringResource(R.string.plugin_uninstall),
                tint = colorScheme.error,
                background = colorScheme.errorContainer.copy(alpha = 0.45f),
                onClick = onUninstall
            )
        }
    }
}

@Composable
private fun CompactPluginActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = null),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun PluginSourceType.labelRes(): Int = when (this) {
    PluginSourceType.METADATA -> R.string.plugin_type_metadata
    PluginSourceType.LYRICS -> R.string.plugin_type_lyrics
    PluginSourceType.COVER -> R.string.plugin_type_cover
}

private const val PLUGIN_MANAGER_PREFERENCES = "plugin_manager_preferences"
private const val KEY_COMPACT_MODE = "compact_mode"

@Composable
private fun PluginBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colorScheme.tertiaryContainer.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        fontWeight = FontWeight(700),
        maxLines = 1,
        softWrap = false
    )
}

@Composable
private fun PluginActionChip(
    text: String,
    icon: ImageVector,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 35.dp)
            .clip(CircleShape)
            .background(background)
            .combinedClickable(
                onClick = {
                    onClick()
                },
                onLongClick = null
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )

        Text(
            text = text,
            color = tint,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 5.dp, end = 2.dp)
        )
    }
}

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

/** 小格式徽标：沿用应用约定的字号，只换 Salt 的主题色。 */
@Composable
private fun SaltPluginBadge(text: String) {
    com.moriafly.salt.ui.Text(
        text = text,
        color = SaltTheme.colors.highlight,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SaltTheme.colors.highlight.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** Salt 的胶囊操作按钮：低强调底色，危险操作走错误色。 */
@Composable
private fun SaltPluginActionButton(
    text: String,
    icon: ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (danger) SaltTheme.colors.error else SaltTheme.colors.text
    PillButton(
        text = text,
        onClick = onClick,
        style = PillButtonDefaults.style(PillButtonSize.Medium),
        colors = PillButtonDefaults.colors(
            contentColor = contentColor,
            containerColor = if (danger) SaltTheme.colors.error.copy(alpha = 0.12f)
                else SaltTheme.colors.text.copy(alpha = 0.08f),
        ),
        leading = {
            com.moriafly.salt.ui.Icon(
                painter = rememberVectorPainter(icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
        },
    )
}
