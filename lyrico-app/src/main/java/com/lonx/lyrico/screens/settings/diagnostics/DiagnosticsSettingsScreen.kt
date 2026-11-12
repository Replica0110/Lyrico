package com.lonx.lyrico.screens.settings.diagnostics

import android.widget.Toast
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
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppLogsDestination
import com.ramcosta.composedestinations.generated.destinations.ProblemReproductionDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel

@Composable
@Destination<RootGraph>(route = "settings_diagnostics")
fun DiagnosticsSettingsScreen(navigator: DestinationsNavigator) {
    val viewModel: DiagnosticsSettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<LibraryMaintenanceAction?>(null) }
    var showConfirmation by remember { mutableStateOf(false) }
    val canRun = uiState.runningAction == null

    LaunchedEffect(uiState.result) {
        uiState.result?.let { result ->
            val message = when (result.action) {
                LibraryMaintenanceAction.REBUILD_INDEXES -> if (result.succeeded) {
                    R.string.library_indexes_rebuilt
                } else {
                    R.string.library_indexes_rebuild_failed
                }
                LibraryMaintenanceAction.RESET_LIBRARY -> if (result.succeeded) {
                    R.string.library_cleared
                } else {
                    R.string.library_clear_failed
                }
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearResult()
        }
    }

    SettingsPage(
        engine = LocalUiEngine.current,
        initialized = true,
        title = stringResource(R.string.settings_category_diagnostics),
        onBack = { navigator.popBackStack() },
    ) {
        item(key = "diagnostics") {
            SectionTitle(text = stringResource(R.string.settings_section_diagnostics))
            Group {
                Action(
                    icon = LucideIcons.Logs, tone = PreferenceIconTone.Lyrics,
                    title = stringResource(R.string.app_log_title),
                    summary = stringResource(R.string.app_log_summary),
                    onClick = { navigator.navigate(AppLogsDestination()) },
                )
                Action(
                    icon = LucideIcons.Activity, tone = PreferenceIconTone.Tools,
                    title = stringResource(R.string.reproduction_title),
                    summary = stringResource(R.string.log_recording_summary),
                    onClick = { navigator.navigate(ProblemReproductionDestination()) },
                )
            }
        }
        item(key = "maintenance") {
            SectionTitle(text = stringResource(R.string.settings_section_maintenance))
            Group {
                Action(
                    icon = LucideIcons.RefreshCw, tone = PreferenceIconTone.Tools,
                    title = stringResource(
                        if (uiState.runningAction == LibraryMaintenanceAction.REBUILD_INDEXES) {
                            R.string.library_indexes_rebuilding
                        } else {
                            R.string.rebuild_library_indexes
                        }
                    ),
                    summary = stringResource(R.string.rebuild_library_indexes_summary),
                    enabled = canRun,
                    onClick = {
                        pendingAction = LibraryMaintenanceAction.REBUILD_INDEXES
                        showConfirmation = true
                    },
                )
                Action(
                    icon = LucideIcons.Database, tone = PreferenceIconTone.Danger,
                    title = stringResource(
                        if (uiState.runningAction == LibraryMaintenanceAction.RESET_LIBRARY) {
                            R.string.library_resetting
                        } else {
                            R.string.clear_songs
                        }
                    ),
                    summary = stringResource(R.string.reset_library_summary),
                    enabled = canRun,
                    onClick = {
                        pendingAction = LibraryMaintenanceAction.RESET_LIBRARY
                        showConfirmation = true
                    },
                )
            }
        }
    }

    val reset = pendingAction == LibraryMaintenanceAction.RESET_LIBRARY
    YesNoDialog(
        show = showConfirmation,
        title = stringResource(if (reset) R.string.clear_songs else R.string.rebuild_library_indexes),
        summary = stringResource(
            if (reset) R.string.reset_library_confirmation else R.string.rebuild_library_indexes_confirmation
        ),
        onDismissRequest = { showConfirmation = false },
        onDismissFinished = { if (!showConfirmation) pendingAction = null },
        confirmEnabled = canRun,
        dismissOnConfirm = false,
        onConfirm = {
            pendingAction?.let(viewModel::perform)
            showConfirmation = false
        },
    )
}
