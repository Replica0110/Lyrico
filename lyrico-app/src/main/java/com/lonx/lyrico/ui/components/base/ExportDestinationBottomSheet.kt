@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.base

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.export.ExportDestination
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.ItemCheck
import com.moriafly.salt.ui.RoundedColumn
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ExportDestinationBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (ExportDestination) -> Unit
) {
    var destination by remember(show) {
        mutableStateOf(ExportDestination.SELECTED_DIRECTORY)
    }
    val salt = LocalUiEngine.current == UiEngine.SALT

    YesNoBottomSheet(
        show = show,
        title = stringResource(R.string.export_destination_title),
        onDismissRequest = onDismissRequest,
        onConfirm = { onConfirm(destination) },
        groupContent = false,
        content = {
            if (salt) {
                RoundedColumn {
                    for (option in ExportDestination.entries) {
                        ItemCheck(
                            state = destination == option,
                            onChange = { checked ->
                                if (checked) destination = option
                            },
                            text = stringResource(option.titleRes),
                            sub = stringResource(option.summaryRes),
                        )
                    }
                }
            } else Column(modifier = Modifier.fillMaxWidth()) {
                Card(
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer
                    )
                ) {
                    for (option in ExportDestination.entries) {
                        RadioButtonPreference(
                            startAction = {
                                when (option) {
                                    ExportDestination.SELECTED_DIRECTORY ->
                                        PreferenceIcon(LucideIcons.Folder, PreferenceIconTone.Appearance)
                                    ExportDestination.AUDIO_DIRECTORY ->
                                        PreferenceIcon(LucideIcons.FileMusic, PreferenceIconTone.Tools)
                                }
                            },
                            title = stringResource(option.titleRes),
                            summary = stringResource(option.summaryRes),
                            selected = destination == option,
                            onClick = { destination = option },
                        )
                    }
                }
            }
        }
    )
}

@get:StringRes
private val ExportDestination.titleRes: Int
    get() = when (this) {
        ExportDestination.SELECTED_DIRECTORY -> R.string.export_destination_selected_folder
        ExportDestination.AUDIO_DIRECTORY -> R.string.export_destination_audio_folder
    }

@get:StringRes
private val ExportDestination.summaryRes: Int
    get() = when (this) {
        ExportDestination.SELECTED_DIRECTORY -> R.string.export_destination_selected_folder_summary
        ExportDestination.AUDIO_DIRECTORY -> R.string.export_destination_audio_folder_summary
    }
