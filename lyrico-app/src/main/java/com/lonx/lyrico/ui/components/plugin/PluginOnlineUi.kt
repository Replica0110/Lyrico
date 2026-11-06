@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.plugin

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.plugin.source.*
import com.lonx.lyrico.ui.components.base.*
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.salt.SaltIconButton
import com.lonx.lyrico.ui.theme.LocalUiEngine
import okhttp3.HttpUrl.Companion.toHttpUrl
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu

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
internal fun PluginUpdateSheet(
    show: Boolean,
    candidate: PluginUpdateState?,
    installed: com.lonx.lyrico.data.model.entity.SourcePluginEntity?,
    error: String?,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onConfirm: (PluginUpdateState) -> Unit
) {
    val options = candidate?.let { it.alternatives.ifEmpty { listOf(PluginUpdateOption(it.release, it.sourceUrls, it.cached)) } }.orEmpty()
    var selectedHash by remember(candidate) { mutableStateOf(candidate?.takeIf { it.alternatives.isEmpty() }?.release?.sha256) }
    val selected = options.singleOrNull { it.release.sha256 == selectedHash }
    YesNoBottomSheet(
        show = show,
        title = stringResource(R.string.plugin_update),
        enableNestedScroll = false,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        confirmText = stringResource(R.string.plugin_update),
        confirmEnabled = selected?.release?.compatible == true && candidate != null && installed?.pluginDir == candidate.installedDir,
        onConfirm = {
            if (candidate != null && selected != null) onConfirm(candidate.copy(release = selected.release,
                sourceUrls = selected.sourceUrls, alternatives = emptyList(), cached = selected.cached))
        }
    ) {
        if (options.size > 1) {
            PluginSheetError(stringResource(R.string.plugin_update_source_conflict))
            val choices: @Composable () -> Unit = {
                options.forEach { option ->
                    val title = option.sourceUrls.firstOrNull()?.let {
                        runCatching { it.toHttpUrl().host }.getOrDefault(it)
                    }.orEmpty()
                    if (LocalUiEngine.current == UiEngine.SALT) com.moriafly.salt.ui.ItemCheck(
                        text = title, sub = option.sourceUrls.joinToString("\n"), state = selectedHash == option.release.sha256,
                        onChange = { checked -> if (checked) selectedHash = option.release.sha256 })
                    else top.yukonga.miuix.kmp.preference.RadioButtonPreference(
                        title = title, summary = option.sourceUrls.joinToString("\n"), selected = selectedHash == option.release.sha256,
                        onClick = { selectedHash = option.release.sha256 })
                }
            }
            if (LocalUiEngine.current == UiEngine.SALT) com.moriafly.salt.ui.RoundedColumn { choices() }
            else Card { choices() }
            Spacer(Modifier.height(12.dp))
        }
        if (selected?.cached == true) PluginSheetError(stringResource(R.string.plugin_update_using_cache))
        (selected?.release ?: candidate?.release)?.let { release ->
            error?.let { PluginSheetError(stringResource(R.string.plugin_action_failed, it)) }
            val version = installed?.let { "${it.versionName} → ${release.versionName}" } ?: release.versionName
            PluginImportEntry(
                name = release.name,
                id = release.id,
                version = version,
                details = listOf(stringResource(R.string.plugin_download_size,
                    android.text.format.Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, release.size))) +
                    selected?.sourceUrls.orEmpty().map { stringResource(R.string.plugin_update_source_with_value, it) },
                compatible = release.compatible
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
