@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.plugin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.plugin.source.*
import com.lonx.lyrico.ui.components.base.*
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.salt.SaltIconButton
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.PluginViewModel
import com.lonx.lyrico.viewmodel.PluginImportTask
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PluginImportMenu(enabled: Boolean, onLocal: () -> Unit, onOnline: () -> Unit, onCheckUpdates: () -> Unit) {
    val local = stringResource(R.string.plugin_import_local)
    val online = stringResource(R.string.plugin_import_online)
    val check = stringResource(R.string.plugin_check_updates)
    val label = stringResource(R.string.plugin_import_archive)
    if (LocalUiEngine.current == UiEngine.SALT) {
        val popup = com.moriafly.salt.ui.popup.rememberPopupState()
        Box {
            SaltIconButton(LucideIcons.Plus, label, enabled = enabled, onClick = popup::expend)
            com.moriafly.salt.ui.popup.PopupMenu(expanded = popup.expend, onDismissRequest = popup::dismiss) {
                com.moriafly.salt.ui.popup.PopupMenuItem(text = local, onClick = { popup.dismiss(); onLocal() })
                com.moriafly.salt.ui.popup.PopupMenuItem(text = online, onClick = { popup.dismiss(); onOnline() })
                com.moriafly.salt.ui.popup.PopupMenuItem(text = check, onClick = { popup.dismiss(); onCheckUpdates() })
            }
        }
    } else {
        OverlayIconDropdownMenu(
            entry = DropdownEntry(items = listOf(
                DropdownItem(text = local, onClick = onLocal),
                DropdownItem(text = online, onClick = onOnline),
                DropdownItem(text = check, onClick = onCheckUpdates)
            )), enabled = enabled
        ) { Icon(LucideIcons.Plus, label) }
    }
}

@Composable
internal fun PluginOnlineImport(show: Boolean, viewModel: PluginViewModel, onDismiss: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val installed by viewModel.plugins.collectAsState()
    val url = rememberTextFieldState()
    val catalog = state.onlineCatalog
    var selected by rememberSaveable(catalog) { mutableStateOf<Set<String>>(emptySet()) }
    // A recreated screen with a loaded catalog has no input dialog left to dismiss.
    var inputClosed by remember { mutableStateOf(catalog != null) }
    val baseline = remember(catalog) { installed.associateBy { it.id } }
    val focus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(catalog) {
        if (catalog == null) {
            inputClosed = false
        } else {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    val dismiss = onDismiss
    val inputVisible = show && catalog == null
    val inputFinished = {
        if (!show) viewModel.dismissOnline()
        else if (catalog != null) inputClosed = true
    }
    val inputStatus: @Composable () -> Unit = {
        state.onlineError?.let { PluginSheetError(stringResource(R.string.plugin_action_failed, it)) }
        if (state.onlineBusy) AppLinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
    }
    if (LocalUiEngine.current == UiEngine.SALT) {
        com.lonx.lyrico.ui.components.salt.SaltInputDialog(
            show = inputVisible,
            onDismissFinished = inputFinished,
            title = stringResource(R.string.plugin_import_online),
            state = url,
            hint = stringResource(R.string.plugin_online_url),
            onDismissRequest = dismiss,
            onConfirm = { viewModel.fetchCatalog(url.text.toString().trim()) },
            confirmText = stringResource(R.string.plugin_online_fetch),
            cancelText = stringResource(R.string.cancel),
            confirmEnabled = !state.onlineBusy && url.text.isNotBlank(),
            readOnly = state.onlineBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            status = inputStatus
        )
    } else YesNoDialog(
        show = inputVisible,
        title = stringResource(R.string.plugin_import_online),
        onDismissRequest = dismiss,
        onDismissFinished = inputFinished,
        onConfirm = { viewModel.fetchCatalog(url.text.toString().trim()) },
        confirmText = stringResource(R.string.plugin_online_fetch),
        confirmEnabled = !state.onlineBusy && url.text.isNotBlank(),
        dismissOnConfirm = false,
        content = {
            LaunchedEffect(inputVisible) { if (inputVisible) focus.requestFocus() }
            TextField(state = url, label = stringResource(R.string.plugin_online_url),
                useLabelAsPlaceholder = true,
                lineLimits = TextFieldLineLimits.SingleLine, readOnly = state.onlineBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().focusRequester(focus))
            inputStatus()
        }
    )
    val started = state.onlineTasks.isNotEmpty()
    val retryIds = state.onlineTasks.filterValues {
        it.phase == PluginImportTask.Phase.FAILED || it.phase == PluginImportTask.Phase.CANCELLED
    }.keys
    val density = LocalDensity.current
    val windowWidth = LocalWindowInfo.current.containerSize.width
    var actionHeight by remember(catalog, density.fontScale, windowWidth) { mutableIntStateOf(0) }
    ActionBottomSheet(
        show = show && catalog != null && inputClosed,
        title = stringResource(R.string.plugin_import_install),
        enableNestedScroll = false,
        allowDismiss = !state.onlineBusy,
        onDismissRequest = dismiss,
        onDismissFinished = { if (!show) viewModel.dismissOnline() },
        footer = {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = with(density) { actionHeight.toDp() })
                    .onSizeChanged { actionHeight = maxOf(actionHeight, it.height) },
                contentAlignment = Alignment.Center
            ) {
                if (started && !state.onlineBusy && retryIds.isEmpty()) {
                    if (LocalUiEngine.current == UiEngine.SALT) com.moriafly.salt.ui.Button(
                        text = stringResource(R.string.plugin_import_done), onClick = dismiss,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp))
                    else TextButton(text = stringResource(R.string.plugin_import_done), onClick = dismiss,
                        modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
                } else SheetConfirmActions(
                    cancelText = stringResource(when {
                        state.onlineBusy -> R.string.action_abort
                        started -> R.string.action_close
                        else -> R.string.cancel
                    }),
                    confirmText = stringResource(when {
                        state.onlineBusy -> R.string.plugin_online_installing
                        retryIds.isNotEmpty() -> R.string.plugin_online_retry
                        else -> R.string.plugin_import_install
                    }),
                    onCancel = { if (state.onlineBusy) viewModel.stopOnlineImport() else dismiss() },
                    onConfirm = {
                        catalog?.let { directory -> viewModel.installOnline(directory.plugins.filter {
                            it.id in (if (started) retryIds else selected)
                        }) }
                    },
                    confirmEnabled = !state.onlineBusy && (started || selected.isNotEmpty())
                )
            }
        },
        content = {
            catalog?.let { directory ->
                if (directory.plugins.isEmpty()) OnlineText(stringResource(R.string.plugin_empty))
                for (group in listOf(directory.plugins.filter { it.compatible }, directory.plugins.filterNot { it.compatible })) {
                    if (group.isEmpty()) continue
                    PluginImportSectionTitle(stringResource(
                        if (group.first().compatible) R.string.plugin_catalog_plugins
                        else R.string.plugin_catalog_incompatible_count, group.size))
                    group.forEach { release ->
                        key(release.id) {
                            val current = baseline[release.id]
                            val task = state.onlineTasks[release.id]
                            PluginImportEntry(
                                name = release.name,
                                id = release.id,
                                version = current?.let { "${it.versionName} → ${release.versionName}" } ?: release.versionName,
                                conflict = PluginVersionConflict.between(current?.versionCode, release.versionCode),
                                details = listOf(stringResource(R.string.plugin_api_versions_with_value,
                                    release.apiVersion, release.minHostApiVersion),
                                    stringResource(R.string.plugin_download_size,
                                        android.text.format.Formatter.formatShortFileSize(
                                            androidx.compose.ui.platform.LocalContext.current, release.size))),
                                selected = release.id in selected,
                                enabled = release.compatible && !started,
                                compatible = release.compatible,
                                supportingContent = task?.error?.let { error -> { OnlineText(stringResource(R.string.plugin_action_failed, error)) } },
                                trailingContent = { PluginImportTaskProgress(task) },
                                onCheckedChange = { checked -> selected = if (checked) selected + release.id else selected - release.id }
                            )
                            if (LocalUiEngine.current != UiEngine.SALT) Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    )
}

/** The right column keeps its dimensions before, during and after downloading. */
@Composable
private fun PluginImportTaskProgress(task: PluginImportTask?) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    val label = when (task?.phase) {
        PluginImportTask.Phase.WAITING -> stringResource(R.string.plugin_task_waiting)
        PluginImportTask.Phase.DOWNLOADING -> "${task.percent.coerceIn(0, 100)}%"
        PluginImportTask.Phase.VERIFYING -> stringResource(R.string.plugin_task_verifying)
        PluginImportTask.Phase.INSTALLING -> stringResource(R.string.plugin_online_installing)
        PluginImportTask.Phase.INSTALLED -> stringResource(R.string.plugin_task_installed)
        PluginImportTask.Phase.FAILED -> stringResource(R.string.plugin_task_failed)
        PluginImportTask.Phase.CANCELLED -> stringResource(R.string.plugin_task_cancelled)
        null -> ""
    }
    val secondary = if (salt) com.moriafly.salt.ui.SaltTheme.colors.subText
        else MiuixTheme.colorScheme.onSurfaceVariantSummary
    val accent = if (salt) com.moriafly.salt.ui.SaltTheme.colors.highlight else MiuixTheme.colorScheme.primary
    val errorColor = if (salt) com.moriafly.salt.ui.SaltTheme.colors.error else MiuixTheme.colorScheme.error
    val progress by animateFloatAsState(
        targetValue = (task?.percent ?: 0).coerceIn(0, 100) / 100f,
        animationSpec = tween(150), label = "plugin-download-progress")
    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (task?.phase) {
                PluginImportTask.Phase.DOWNLOADING, PluginImportTask.Phase.VERIFYING,
                PluginImportTask.Phase.INSTALLING -> {
                    val value = if (task.phase == PluginImportTask.Phase.DOWNLOADING) progress else null
                    if (salt) {
                        if (value == null) androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = accent)
                        else androidx.compose.material3.CircularProgressIndicator(
                            progress = { value }, modifier = Modifier.size(24.dp), strokeWidth = 2.dp,
                            color = accent, trackColor = accent.copy(alpha = 0.12f))
                    } else CircularProgressIndicator(
                        progress = value, size = 24.dp, strokeWidth = 2.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = accent, backgroundColor = accent.copy(alpha = 0.12f)))
                }
                PluginImportTask.Phase.WAITING, PluginImportTask.Phase.INSTALLED,
                PluginImportTask.Phase.FAILED, PluginImportTask.Phase.CANCELLED -> {
                    val icon = when (task.phase) {
                        PluginImportTask.Phase.WAITING -> LucideIcons.Download
                        PluginImportTask.Phase.INSTALLED -> LucideIcons.Check
                        else -> LucideIcons.Close
                    }
                    val tint = if (task.phase == PluginImportTask.Phase.FAILED) errorColor else secondary
                    if (salt) com.moriafly.salt.ui.Icon(
                        painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(icon),
                        contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    else Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
                null -> Unit
            }
        }
        val textColor = if (task?.phase == PluginImportTask.Phase.FAILED) errorColor else secondary
        if (salt) com.moriafly.salt.ui.Text(label,
            style = com.moriafly.salt.ui.SaltTheme.textStyles.sub.copy(
                fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            color = textColor, maxLines = 1, minLines = 1)
        else Text(label, style = MiuixTheme.textStyles.body2.copy(
            fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            color = textColor, maxLines = 1, minLines = 1)
    }
}

@Composable
private fun OnlineText(text: String) {
    if (LocalUiEngine.current == UiEngine.SALT) com.moriafly.salt.ui.Text(text,
        style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
        color = com.moriafly.salt.ui.SaltTheme.colors.subText)
    else Text(text, style = MiuixTheme.textStyles.body2)
}

@Composable
internal fun PluginUpdateSheet(
    show: Boolean,
    candidate: PluginUpdateState?,
    installed: com.lonx.lyrico.data.model.entity.SourcePluginEntity?,
    error: String?,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onConfirm: () -> Unit
) {
    YesNoBottomSheet(
        show = show,
        title = stringResource(R.string.plugin_update),
        enableNestedScroll = false,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        confirmText = stringResource(R.string.plugin_update),
        confirmEnabled = candidate?.release?.compatible == true && installed?.pluginDir == candidate.installedDir,
        onConfirm = onConfirm
    ) {
        candidate?.release?.let { release ->
            error?.let { PluginSheetError(stringResource(R.string.plugin_action_failed, it)) }
            val version = installed?.let { "${it.versionName} → ${release.versionName}" } ?: release.versionName
            PluginImportEntry(
                name = release.name,
                id = release.id,
                version = version,
                details = listOf(stringResource(R.string.plugin_download_size,
                    android.text.format.Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, release.size))),
                compatible = release.compatible,
                supportingContent = {
                    if (release.changelog.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        dev.jeziellago.compose.markdowntext.MarkdownText(
                            markdown = release.changelog,
                            modifier = Modifier.fillMaxWidth(),
                            style = if (LocalUiEngine.current == UiEngine.SALT)
                                com.moriafly.salt.ui.SaltTheme.textStyles.main.copy(
                                    color = com.moriafly.salt.ui.SaltTheme.colors.text)
                            else MiuixTheme.textStyles.body2.copy(color = MiuixTheme.colorScheme.onSurface),
                            linkColor = if (LocalUiEngine.current == UiEngine.SALT)
                                com.moriafly.salt.ui.SaltTheme.colors.highlight
                            else MiuixTheme.colorScheme.primary
                        )
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
