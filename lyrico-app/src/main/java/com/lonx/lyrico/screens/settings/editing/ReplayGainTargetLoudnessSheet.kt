@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens.settings.editing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.screens.settings.*
import com.lonx.lyrico.ui.components.base.YesNoBottomSheet
import com.lonx.lyrico.ui.components.salt.SaltTextInput
import com.lonx.lyrico.ui.theme.LocalUiEngine
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun formatLoudnessNumber(value: Double): String {
    return if (value % 1.0 == 0.0) {
        "%.0f".format(java.util.Locale.US, value)
    } else {
        "%.2f".format(java.util.Locale.US, value)
    }
}

internal fun formatLoudnessValue(value: Double): String = "${formatLoudnessNumber(value)} LUFS"

@Composable
internal fun ReplayGainTargetLoudnessSheet(
    show: Boolean,
    currentLoudness: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    val presets = remember { listOf(-23.0, -18.0, -14.0) }
    val presetLabels = listOf(
        R.string.replay_gain_target_preset_ebu,
        R.string.replay_gain_target_preset_default,
        R.string.replay_gain_target_preset_streaming
    )
    var selectedPreset by remember(show, currentLoudness) {
        mutableDoubleStateOf(if (currentLoudness in presets) currentLoudness else presets.first())
    }
    var customSelected by remember(show, currentLoudness) {
        mutableStateOf(currentLoudness !in presets)
    }
    val customState = remember(show, currentLoudness) {
        TextFieldState(if (currentLoudness in presets) "" else formatLoudnessNumber(currentLoudness))
    }
    val customText = customState.text.toString()
    var invalid by remember(show) { mutableStateOf(false) }
    val clearInputError = InputTransformation { invalid = false }

    if (LocalUiEngine.current == UiEngine.SALT) {
        com.lonx.lyrico.ui.components.salt.SaltConfirmDialog(
            show = show, dismissOnConfirm = false,
            title = stringResource(R.string.settings_replay_gain_target_loudness),
            cancelText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.confirm),
            onDismissRequest = onDismiss,
            onConfirm = {
                val value = if (customSelected) customText.trim().toDoubleOrNull() else selectedPreset
                if (value != null && value in -60.0..0.0) {
                    onSave(value)
                    onDismiss()
                } else invalid = true
            },
            content = {
                com.moriafly.salt.ui.RoundedColumn(paddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    presets.forEachIndexed { index, preset ->
                        com.moriafly.salt.ui.ItemCheck(
                            text = stringResource(presetLabels[index]),
                            state = !customSelected && selectedPreset == preset,
                            onChange = { selectedPreset = preset; customSelected = false; invalid = false },
                        )
                    }
                    com.moriafly.salt.ui.ItemCheck(
                        text = stringResource(R.string.replay_gain_target_preset_custom),
                        state = customSelected,
                        onChange = { customSelected = true; invalid = false },
                    )
                    if (customSelected) SaltTextInput(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        backgroundColor = com.moriafly.salt.ui.SaltTheme.colors.background,
                        state = customState,
                        inputTransformation = clearInputError,
                        hint = stringResource(R.string.replay_gain_target_custom_hint),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    )
                    if (invalid) com.moriafly.salt.ui.ItemTip(stringResource(R.string.replay_gain_target_invalid))
                }
            },
        )
        return
    }

    YesNoBottomSheet(
        show = show,
        title = stringResource(R.string.settings_replay_gain_target_loudness),
        onDismissRequest = onDismiss,
        onConfirm = {
            if (customSelected) {
                val customValue = customText.trim().toDoubleOrNull()
                if (customValue != null && customValue in -60.0..0.0) {
                    onSave(customValue)
                    onDismiss()
                } else {
                    invalid = true
                }
            } else {
                onSave(selectedPreset)
                onDismiss()
            }
        },
        content = {
            Column {
                Card(
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer
                    )
                ) {
                    presets.forEachIndexed { index, preset ->
                        RadioButtonPreference(
                            title = stringResource(presetLabels[index]),
                            selected = !customSelected && selectedPreset == preset,
                            onClick = {
                                selectedPreset = preset
                                customSelected = false
                                invalid = false
                            }
                        )
                    }
                    RadioButtonPreference(
                        title = stringResource(R.string.replay_gain_target_preset_custom),
                        selected = customSelected,
                        onClick = {
                            customSelected = true
                            invalid = false
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                AnimatedVisibility(visible = customSelected) {
                    Card(
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        TextField(
                            state = customState,
                            inputTransformation = clearInputError,
                            label = stringResource(R.string.replay_gain_target_custom_hint),
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth(),
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                        if (invalid) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.replay_gain_target_invalid),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    )
}
