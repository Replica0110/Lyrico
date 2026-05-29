package com.lonx.lyrico.ui.components.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.BatchMatchConfigDefaults
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldTarget
import com.lonx.lyrico.data.model.plugin.PluginMetadataWriteMode
import com.lonx.lyrico.ui.components.base.YesNoBottomSheet
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchMatchConfigBottomSheet(
    show: Boolean,
    initialConfig: BatchMatchConfig,
    onDismissRequest: (BatchMatchConfig) -> Unit,
    onConfirm: (BatchMatchConfig) -> Unit
) {
    var config by remember { mutableStateOf(initialConfig) }

    val targetGroups = remember { BatchMatchConfigDefaults.TARGET_GROUPS }

    fun updateTarget(
        target: PluginMetadataFieldTarget,
        isSelected: Boolean,
        mode: PluginMetadataWriteMode
    ) {
        val currentMap = config.targetModes.toMutableMap()
        currentMap[target] = if (isSelected) {
            mode.takeIf { it != PluginMetadataWriteMode.DISABLED }
                ?: PluginMetadataWriteMode.SUPPLEMENT
        } else {
            PluginMetadataWriteMode.DISABLED
        }
        config = config.copy(targetModes = currentMap)
    }

    YesNoBottomSheet(
        show = show,
        title = stringResource(R.string.batch_match_config_title),
        enableNestedScroll = false,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Card(
                    modifier = Modifier.padding(bottom = 12.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer,
                    )
                ) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 250.dp)
                    ) {
                        targetGroups.forEach { group ->
                            item("group_${group.titleRes}") {
                                Text(
                                    text = stringResource(group.titleRes),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            }

                            items(group.targets, key = { it.name }) { target ->
                                val mode = config.targetModes[target] ?: PluginMetadataWriteMode.DISABLED
                                val isSelected = mode != PluginMetadataWriteMode.DISABLED
                                val effectiveMode = if (isSelected) {
                                    mode
                                } else {
                                    PluginMetadataWriteMode.SUPPLEMENT
                                }

                                BatchMatchTargetItem(
                                    target = target,
                                    isSelected = isSelected,
                                    mode = effectiveMode,
                                    onCheckedChange = { checked ->
                                        updateTarget(target, checked, effectiveMode)
                                    },
                                    onModeToggle = {
                                        updateTarget(
                                            target = target,
                                            isSelected = isSelected,
                                            mode = if (effectiveMode == PluginMetadataWriteMode.OVERWRITE) {
                                                PluginMetadataWriteMode.SUPPLEMENT
                                            } else {
                                                PluginMetadataWriteMode.OVERWRITE
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                Card(
                    modifier = Modifier.padding(bottom = 12.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer,
                    )
                ) {
                    val tempConcurrency = remember(config.concurrency) {
                        mutableIntStateOf(config.concurrency)
                    }
                    CheckboxPreference(
                        title = stringResource(R.string.batch_match_prefer_filename),
                        summary = stringResource(R.string.batch_match_prefer_filename_summary),
                        checked = config.preferFileName,
                        onCheckedChange = { checked ->
                            val updatedTargetModes = config.targetModes.toMutableMap()

                            if (checked) {
                                if (updatedTargetModes[PluginMetadataFieldTarget.TITLE] != PluginMetadataWriteMode.DISABLED) {
                                    updatedTargetModes[PluginMetadataFieldTarget.TITLE] = PluginMetadataWriteMode.OVERWRITE
                                }
                                if (updatedTargetModes[PluginMetadataFieldTarget.ARTIST] != PluginMetadataWriteMode.DISABLED) {
                                    updatedTargetModes[PluginMetadataFieldTarget.ARTIST] = PluginMetadataWriteMode.OVERWRITE
                                }
                            }

                            config = config.copy(
                                preferFileName = checked,
                                targetModes = updatedTargetModes
                            )
                        },
                        insideMargin = PaddingValues(12.dp)
                    )
                    ArrowPreference(
                        title = stringResource(R.string.batch_match_config_concurrency),
                        endActions = {
                            Text(
                                text = "${tempConcurrency.intValue}",
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        insideMargin = PaddingValues(12.dp),
                        onClick = {

                        },
                        bottomAction = {
                            Slider(
                                showKeyPoints = true,
                                valueRange = 1f..5f,
                                steps = 3,
                                value = tempConcurrency.intValue.toFloat(),
                                onValueChange = {
                                    tempConcurrency.intValue = it.roundToInt()
                                },
                                onValueChangeFinished = {
                                    config = config.copy(concurrency = tempConcurrency.intValue)
                                }
                            )
                            Spacer(modifier = Modifier.height(BasicComponentDefaults.InsideMargin.calculateBottomPadding()))
                            Text(
                                text = stringResource(R.string.search_limit_tip),
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        }
                    )
                }

            }
        },
        onDismissRequest = { onDismissRequest(config) },
        onConfirm = {
            onConfirm(config)
            onDismissRequest(config)
        }
    )
}

@Composable
private fun BatchMatchTargetItem(
    target: PluginMetadataFieldTarget,
    isSelected: Boolean,
    mode: PluginMetadataWriteMode,
    onCheckedChange: (Boolean) -> Unit,
    onModeToggle: () -> Unit
) {
    BasicComponent(
        insideMargin = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.fillMaxWidth(),
        startAction = {
            Checkbox(
                state = if (isSelected) ToggleableState.On else ToggleableState.Off,
                onClick = { onCheckedChange(!isSelected) }
            )
        },
        onClick = {
            onCheckedChange(!isSelected)
        },
        endActions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isSelected) {
                    Text(
                        text = stringResource(mode.labelRes),
                        style = MiuixTheme.textStyles.footnote2
                    )
                }

                Switch(
                    checked = mode == PluginMetadataWriteMode.OVERWRITE,
                    onCheckedChange = { onModeToggle() },
                    enabled = isSelected
                )
            }
        }
    ) {
        Text(
            text = stringResource(target.labelRes),
            style = MiuixTheme.textStyles.main,
            color = if (isSelected) {
                MiuixTheme.colorScheme.onSurfaceContainer
            } else {
                MiuixTheme.colorScheme.onSecondaryContainer
            }
        )
    }
}

