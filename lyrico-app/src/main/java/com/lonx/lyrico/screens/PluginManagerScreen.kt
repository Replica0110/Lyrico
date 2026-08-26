package com.lonx.lyrico.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.lonx.lyrico.ui.components.library.LibraryEmptyState
import com.lonx.lyrico.ui.components.plugin.PluginIcon
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.ui.theme.isDarkTheme
import com.lonx.lyrico.viewmodel.PluginViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.PluginConfigDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.icon.extended.Settings
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
    var currentList by remember(plugins, selectedTypeTab) {
        mutableStateOf(
            plugins
                .filter {
                    it.capabilities.supportsSourceType(selectedTypeTab.sourceType)
                }
                .sortedWith(
                    compareBy<SourcePluginEntity> {
                        it.sortOrderFor(selectedTypeTab.sourceType)
                    }.thenBy { it.displayName }
                )
        )
    }
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderableLazyColumnState = rememberReorderableLazyListState(lazyListState) { from, to ->
        currentList = currentList.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(id = R.string.plugin_manager_title),
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            compactMode = !compactMode
                            context.getSharedPreferences(
                                PLUGIN_MANAGER_PREFERENCES,
                                Context.MODE_PRIVATE
                            ).edit().putBoolean(KEY_COMPACT_MODE, compactMode).apply()
                        }
                    ) {
                        Icon(
                            imageVector = if (compactMode) {
                                MiuixIcons.GridView
                            } else {
                                MiuixIcons.ListView
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
                            imageVector = MiuixIcons.Add,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldTopHorizontalPadding(paddingValues))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 8.dp)
            ) {
                TabRowWithContour(
                    tabs = PluginTypeTab.entries.map { stringResource(it.labelRes) },
                    selectedTabIndex = selectedTypeTab.ordinal,
                    onTabSelected = { index ->
                        selectedTypeTab = PluginTypeTab.entries[index]
                    }
                )
            }

            Text(
                text = stringResource(R.string.search_source_priority_tip),
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = colorScheme.onSurfaceVariantActions,
                modifier = Modifier.padding(12.dp)
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .fillMaxHeight()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(bottom = 12.dp),
                overscrollEffect = null,
            ) {
                if (currentList.isEmpty()) {
                    item("empty") {
                        LibraryEmptyState(
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
                            action = {
                                TextButton(
                                    text = stringResource(R.string.plugin_import_archive),
                                    onClick = {
                                        if (!uiState.isBusy) importLauncher.launch(arrayOf("*/*"))
                                    },
                                    colors = ButtonDefaults.textButtonColorsPrimary()
                                )
                            }
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
                                .clip(RoundedCornerShape(CardDefaults.CornerRadius))
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        viewModel.setPluginOrder(
                                            currentList,
                                            selectedTypeTab.sourceType
                                        )
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

    YesNoDialog(
        show = showUninstallDialog,
        title = stringResource(R.string.plugin_uninstall),
        summary = pendingUninstallPluginId?.let { id ->
            plugins.find { it.id == id }?.displayName?.let { name ->
                stringResource(R.string.plugin_uninstall_confirm_message, name)
            }
        },
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

    WindowDialog(
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
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (session.candidates.isNotEmpty()) {
                        SmallTitle(
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
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (session.failed.isNotEmpty()) {
                    SmallTitle(
                        text = stringResource(
                            R.string.plugin_import_failed_title,
                            session.failed.size
                        ),
                        textColor = colorScheme.error
                    )

                    session.failed.forEach { failed ->
                        PluginImportFailedItem(failed = failed)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.padding(vertical = 12.dp))
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
    val manifest = candidate.manifest
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
                if (isNotEmpty()) append(" · ")
                append(stringResource(R.string.plugin_import_version))
                append(" ")
                append(it)
            }

        if (failed.rootPath.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
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
                    maxLines = 1,
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
                icon = MiuixIcons.Settings,
                tint = actionIconTint,
                background = secondaryContainer,
                onClick = onConfig
            )
            PluginActionChip(
                text = stringResource(R.string.plugin_custom_name),
                icon = MiuixIcons.Rename,
                tint = actionIconTint,
                background = secondaryContainer,
                onClick = onRename
            )

            Spacer(modifier = Modifier.weight(1f))

            PluginActionChip(
                text = stringResource(R.string.plugin_uninstall),
                icon = MiuixIcons.Delete,
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
                icon = MiuixIcons.Settings,
                contentDescription = stringResource(R.string.plugin_config),
                tint = neutralActionTint,
                background = neutralActionBackground,
                onClick = onConfig
            )
            CompactPluginActionButton(
                icon = MiuixIcons.Rename,
                contentDescription = stringResource(R.string.plugin_custom_name),
                tint = neutralActionTint,
                background = neutralActionBackground,
                onClick = onRename
            )
            CompactPluginActionButton(
                icon = MiuixIcons.Delete,
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
