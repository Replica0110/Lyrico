@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens.diagnostics

import android.widget.Toast
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.screens.settings.SettingsPage
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.ProblemReproductionViewModel
import com.moriafly.salt.ui.SaltTheme
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import java.util.Locale
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.platform.LocalLocale

private enum class RecordingConfirmation { RESTART, CLEAR }

@Composable
@Destination<RootGraph>(route = "problem_reproduction")
fun ProblemReproductionScreen(navigator: DestinationsNavigator) {
    val viewModel: ProblemReproductionViewModel = koinViewModel()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingExportSession by rememberSaveable { mutableLongStateOf(0L) }
    var confirmation by remember { mutableStateOf<RecordingConfirmation?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { viewModel.export(it, pendingExportSession) }
        pendingExportSession = 0L
    }

    LaunchedEffect(recording.active, recording.startedAt) {
        while (recording.active) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            message.asString(context)?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
    }
    LaunchedEffect(viewModel, context) {
        viewModel.shareIntents.collect { intent ->
            try {
                context.startActivity(Intent.createChooser(intent, null))
            } catch (error: Exception) {
                Toast.makeText(context, context.getString(R.string.reproduction_report_failed, error.message.orEmpty()), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val elapsedSeconds = ((recording.finishedAt ?: now) - recording.startedAt).coerceAtLeast(0) / 1000
    val elapsed = String.format(LocalLocale.current.platformLocale, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
    val hasRecording = recording.startedAt != 0L
    val canUseReport = hasRecording && !ui.preparingReport
    val canShareReport = canUseReport && recording.count > 0
    val canClear = canUseReport && !recording.active

    SettingsPage(
        engine = LocalUiEngine.current,
        initialized = true,
        title = stringResource(R.string.reproduction_title),
        onBack = { navigator.popBackStack() },
    ) {
        item("recording") {
            SectionTitle(stringResource(R.string.reproduction_section_recording))
            Group {
                Action(
                    title = stringResource(if (recording.active) R.string.log_recording_stop else R.string.log_recording_start),
                    summary = when {
                        recording.active -> stringResource(R.string.reproduction_elapsed, elapsed)
                        recording.limitReached -> stringResource(R.string.log_recording_limit, recording.count)
                        recording.startedAt != 0L -> stringResource(R.string.reproduction_stopped, elapsed)
                        else -> stringResource(R.string.reproduction_start_hint)
                    },
                    icon = LucideIcons.Activity,
                    enabled = !ui.preparingReport,
                    onClick = {
                        if (!ui.preparingReport) when {
                            recording.active -> viewModel.stop()
                            recording.startedAt != 0L -> confirmation = RecordingConfirmation.RESTART
                            else -> viewModel.start()
                        }
                    },
                )
            }
        }
        item("report") {
            SectionTitle(stringResource(R.string.reproduction_section_report))
            Group {
                Action(
                    title = stringResource(R.string.reproduction_preview),
                    summary = when {
                        !hasRecording -> stringResource(R.string.reproduction_empty)
                        ui.preparingReport -> stringResource(R.string.reproduction_preparing)
                        else -> stringResource(
                            R.string.reproduction_counts, recording.count, recording.warningCount, recording.errorCount, recording.omittedCount,
                        )
                    },
                    icon = LucideIcons.ClipboardList,
                    enabled = canUseReport,
                    onClick = viewModel::preview,
                )
                Action(
                    title = stringResource(R.string.action_share),
                    icon = LucideIcons.Share2,
                    enabled = canShareReport,
                    onClick = viewModel::share,
                )
                Action(
                    title = stringResource(R.string.log_recording_export),
                    icon = LucideIcons.Download,
                    enabled = canShareReport,
                    onClick = {
                        if (canShareReport) {
                            viewModel.stop()
                            pendingExportSession = recording.startedAt
                            exportLauncher.launch("lyrico_recording_${pendingExportSession}.log")
                        }
                    },
                )
                Action(
                    title = stringResource(R.string.reproduction_clear),
                    icon = LucideIcons.Trash,
                    tone = PreferenceIconTone.Danger,
                    enabled = canClear,
                    onClick = { if (canClear) confirmation = RecordingConfirmation.CLEAR },
                )
            }
        }
    }

    YesNoDialog(
        show = confirmation != null,
        title = stringResource(if (confirmation == RecordingConfirmation.RESTART) R.string.reproduction_restart else R.string.reproduction_clear),
        summary = stringResource(R.string.reproduction_replace_warning),
        onDismissRequest = { confirmation = null },
        onConfirm = {
            val action = confirmation
            confirmation = null
            when (action) {
                RecordingConfirmation.RESTART -> viewModel.start()
                RecordingConfirmation.CLEAR -> viewModel.clear()
                null -> Unit
            }
        },
    )
    ActionBottomSheet(
        show = ui.preview != null,
        title = stringResource(R.string.reproduction_preview),
        onDismissRequest = viewModel::dismissPreview,
        content = {
            if (ui.previewTruncated) ReproductionText(
                stringResource(R.string.reproduction_preview_truncated), Modifier.padding(16.dp),
            )
            SelectionContainer {
                ReproductionText(ui.preview.orEmpty(), Modifier.padding(16.dp), monospace = true)
            }
        },
    )
}

@Composable
private fun ReproductionText(text: String, modifier: Modifier, monospace: Boolean = false) {
    val family = if (monospace) FontFamily.Monospace else FontFamily.Default
    if (LocalUiEngine.current == UiEngine.SALT) {
        com.moriafly.salt.ui.Text(
            text = text,
            modifier = modifier,
            fontFamily = family,
            style = SaltTheme.textStyles.sub,
            color = SaltTheme.colors.subText,
        )
    } else {
        top.yukonga.miuix.kmp.basic.Text(
            text = text,
            modifier = modifier,
            fontFamily = family,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
