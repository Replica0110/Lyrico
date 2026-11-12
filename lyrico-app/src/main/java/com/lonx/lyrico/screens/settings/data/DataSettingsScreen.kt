package com.lonx.lyrico.screens.settings.data

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.screens.settings.SettingsPage
import com.lonx.lyrico.screens.settings.settingsViewModelWithEvents
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.utils.DownloadsFileWriter
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Composable
@Destination<RootGraph>(route = "settings_data")
fun DataSettingsScreen(navigator: DestinationsNavigator) {
    val settingsViewModel = settingsViewModelWithEvents()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearCacheDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settingsViewModel.refreshCache() }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { settingsViewModel.exportSettings(context, it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { settingsViewModel.importSettings(context, it) }
    }

    val calculatingText = stringResource(R.string.calculating_cache)
    val confirmText = stringResource(R.string.clear_cache_confirm)

    val cacheContent = remember(
        settingsUiState.categorizedCacheSize,
        settingsUiState.totalCacheSize
    ) {
        if (settingsUiState.categorizedCacheSize.isEmpty()) {
            calculatingText
        } else {
            val details = settingsUiState.categorizedCacheSize
                .map { (category, size) ->
                    context.getString(
                        R.string.cache_item,
                        context.getString(category.labelRes),
                        Formatter.formatFileSize(context, size)
                    )
                }
                .joinToString(separator = "\n")

            buildString {
                append(confirmText)
                append("\n\n")
                append(details)
                append("\n\n")
                append(
                    context.getString(
                        R.string.cache_total,
                        Formatter.formatFileSize(context, settingsUiState.totalCacheSize)
                    )
                )
            }
        }
    }

    SettingsPage(
        engine = LocalUiEngine.current,
        initialized = settingsUiState.isInitialized,
        title = stringResource(R.string.settings_category_data),
        onBack = { navigator.popBackStack() },
    ) {
        item(key = "backup") {
            SectionTitle(text = stringResource(R.string.section_backup))
            Group {
                Action(
                    icon = LucideIcons.FileUp, tone = PreferenceIconTone.Appearance,
                    title = stringResource(R.string.export_config),
                    summary = stringResource(R.string.export_config_hint),
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (DownloadsFileWriter.canWrite(context)) {
                            settingsViewModel.exportSettings(context)
                        } else {
                            exportLauncher.launch("lyrico_settings_backup_${currentTime}.json")
                        }
                    }
                )
                Action(
                    icon = LucideIcons.FileDown, tone = PreferenceIconTone.Tools,
                    title = stringResource(R.string.import_config),
                    summary = stringResource(R.string.import_config_hint),
                    onClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    }
                )
            }
        }
        item(key = "cache") {
            SectionTitle(text = stringResource(R.string.settings_section_storage))
            Group {
                val cacheSummary = stringResource(
                    R.string.cache_size_label,
                    Formatter.formatFileSize(context, settingsUiState.totalCacheSize)
                )
                Action(
                    icon = LucideIcons.Eraser, tone = PreferenceIconTone.Teal,
                    title = stringResource(R.string.clear_cache),
                    summary = cacheSummary,
                    onClick = { showClearCacheDialog = true },
                    holdDownState = showClearCacheDialog
                )
            }
        }
    }
    SettingsCacheDialog(
        show = showClearCacheDialog,
        text = cacheContent,
        onDismiss = { showClearCacheDialog = false },
        onConfirm = {
            settingsViewModel.clearCache()
            showClearCacheDialog = false
        },
    )
}
