@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.batch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.model.metadata.MetadataWriteMode
import com.lonx.lyrico.ui.components.base.YesNoBottomSheet
import com.lonx.lyrico.viewmodel.BatchMatchType
import com.moriafly.salt.ui.enabledAlpha
import com.moriafly.salt.ui.icons.Check
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.icons.Uncheck
import com.moriafly.salt.ui.innerPadding
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
    matchType: BatchMatchType,
    initialConfig: BatchMatchConfig,
    visibleTargets: List<MetadataFieldTarget>,
    onDismissRequest: (BatchMatchConfig) -> Unit,
    onConfirm: (BatchMatchConfig) -> Unit
) {
    val singleTarget = matchType.targets.singleOrNull()

    var config by remember(show, initialConfig, singleTarget) {
        mutableStateOf(
            if (singleTarget != null &&
                (initialConfig.targetModes[singleTarget] ?: MetadataWriteMode.DISABLED) ==
                MetadataWriteMode.DISABLED
            ) {
                initialConfig.copy(
                    targetModes = initialConfig.targetModes +
                        (singleTarget to MetadataWriteMode.SUPPLEMENT)
                )
            } else {
                initialConfig
            }
        )
    }

    val targets = visibleTargets.filter { it in matchType.targets }

    fun updateTarget(
        target: MetadataFieldTarget,
        isSelected: Boolean,
        mode: MetadataWriteMode
    ) {
        val currentMap = config.targetModes.toMutableMap()
        currentMap[target] = if (isSelected) {
            mode.takeIf { it != MetadataWriteMode.DISABLED }
                ?: MetadataWriteMode.SUPPLEMENT
        } else {
            MetadataWriteMode.DISABLED
        }
        config = config.copy(targetModes = currentMap)
    }

    YesNoBottomSheet(
        show = show,
        title = stringResource(
            when (matchType) {
                BatchMatchType.METADATA -> R.string.batch_match_metadata_config_title
                BatchMatchType.LYRICS -> R.string.batch_match_lyrics_config_title
                BatchMatchType.COVER -> R.string.batch_match_cover_config_title
            }
        ),
        enableNestedScroll = false,
        // 内容自带 verticalScroll，外壳不再提供滚动出口。
        saltScrollContent = false,
        content = {
            if (com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
                com.lonx.lyrico.data.model.UiEngine.SALT
            ) {
                // 配置整体滚动；字段列表在分组中限高后独立滚动。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (singleTarget == null) {
                        com.moriafly.salt.ui.RoundedColumn {
                            LazyColumn(Modifier.heightIn(max = 250.dp)) {
                                items(targets, key = { it.name }) { target ->
                                    val mode = config.targetModes[target] ?: MetadataWriteMode.DISABLED
                                    val isSelected = mode != MetadataWriteMode.DISABLED
                                    val effectiveMode = if (isSelected) mode else MetadataWriteMode.SUPPLEMENT
                                    BatchMatchTargetItem(
                                        target = target,
                                        isSelected = isSelected,
                                        mode = effectiveMode,
                                        onCheckedChange = { checked -> updateTarget(target, checked, effectiveMode) },
                                        onModeToggle = {
                                            updateTarget(
                                                target = target,
                                                isSelected = isSelected,
                                                mode = if (effectiveMode == MetadataWriteMode.OVERWRITE) {
                                                    MetadataWriteMode.SUPPLEMENT
                                                } else {
                                                    MetadataWriteMode.OVERWRITE
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                    com.moriafly.salt.ui.RoundedColumn {
                        if (singleTarget != null) {
                            com.moriafly.salt.ui.ItemCheck(
                                    state = config.targetModes[singleTarget] == MetadataWriteMode.OVERWRITE,
                                    onChange = { checked ->
                                        updateTarget(
                                            target = singleTarget,
                                            isSelected = true,
                                            mode = if (checked) MetadataWriteMode.OVERWRITE
                                            else MetadataWriteMode.SUPPLEMENT
                                        )
                                    },
                                    text = stringResource(R.string.batch_match_overwrite_mode),
                                    sub = stringResource(R.string.batch_match_overwrite_mode_summary),
                                )
                        }
                        com.moriafly.salt.ui.ItemCheck(
                                state = config.preferFileName,
                                onChange = { checked ->
                                    val updatedTargetModes = config.targetModes.toMutableMap()
                                    if (checked) {
                                        if (updatedTargetModes[MetadataFieldTarget.TITLE] != MetadataWriteMode.DISABLED) {
                                            updatedTargetModes[MetadataFieldTarget.TITLE] = MetadataWriteMode.OVERWRITE
                                        }
                                        if (updatedTargetModes[MetadataFieldTarget.ARTIST] != MetadataWriteMode.DISABLED) {
                                            updatedTargetModes[MetadataFieldTarget.ARTIST] = MetadataWriteMode.OVERWRITE
                                        }
                                    }
                                    config = config.copy(preferFileName = checked, targetModes = updatedTargetModes)
                                },
                            text = stringResource(R.string.batch_match_prefer_filename),
                            sub = stringResource(R.string.batch_match_prefer_filename_summary),
                        )
                    }
                    // 滑杆属于分组控件，放进 RoundedColumn；提示用 outer tip 放在分组之外。
                    com.moriafly.salt.ui.RoundedColumn {
                        val tempConcurrency = remember(config.concurrency) {
                            mutableIntStateOf(config.concurrency)
                        }
                        com.moriafly.salt.ui.ItemSlider(
                            value = tempConcurrency.intValue.toFloat(),
                            onValueChange = { tempConcurrency.intValue = it.roundToInt() },
                            text = stringResource(R.string.batch_match_config_concurrency),
                            sub = tempConcurrency.intValue.toString(),
                            valueRange = 1f..5f,
                            steps = 3,
                            onValueChangeFinished = {
                                config = config.copy(concurrency = tempConcurrency.intValue)
                            },
                        )
                    }
                    com.moriafly.salt.ui.ItemOuterTip(
                        text = stringResource(R.string.search_limit_tip)
                    )
                }
                return@YesNoBottomSheet
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (singleTarget == null) {
                    Card(
                        modifier = Modifier.padding(bottom = 12.dp),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.secondaryContainer,
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 250.dp)
                        ) {
                            items(targets, key = { it.name }) { target ->
                                val mode = config.targetModes[target] ?: MetadataWriteMode.DISABLED
                                val isSelected = mode != MetadataWriteMode.DISABLED
                                val effectiveMode = if (isSelected) {
                                    mode
                                } else {
                                    MetadataWriteMode.SUPPLEMENT
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
                                            mode = if (effectiveMode == MetadataWriteMode.OVERWRITE) {
                                                MetadataWriteMode.SUPPLEMENT
                                            } else {
                                                MetadataWriteMode.OVERWRITE
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                Card(
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer,
                    )
                ) {
                    val tempConcurrency = remember(config.concurrency) {
                        mutableIntStateOf(config.concurrency)
                    }
                    if (singleTarget != null) {
                        // 单字段的匹配类型（如歌词 / 封面）打开即匹配，不需要勾选字段，
                        // 这一项只负责切换「覆盖」还是「补充」
                        CheckboxPreference(
                            title = stringResource(R.string.batch_match_overwrite_mode),
                            summary = stringResource(R.string.batch_match_overwrite_mode_summary),
                            checked = config.targetModes[singleTarget] == MetadataWriteMode.OVERWRITE,
                            onCheckedChange = { checked ->
                                updateTarget(
                                    target = singleTarget,
                                    isSelected = true,
                                    mode = if (checked) {
                                        MetadataWriteMode.OVERWRITE
                                    } else {
                                        MetadataWriteMode.SUPPLEMENT
                                    }
                                )
                            },
                            insideMargin = PaddingValues(12.dp)
                        )
                    }
                    CheckboxPreference(
                        title = stringResource(R.string.batch_match_prefer_filename),
                        summary = stringResource(R.string.batch_match_prefer_filename_summary),
                        checked = config.preferFileName,
                        onCheckedChange = { checked ->
                            val updatedTargetModes = config.targetModes.toMutableMap()

                            if (checked) {
                                if (updatedTargetModes[MetadataFieldTarget.TITLE] != MetadataWriteMode.DISABLED) {
                                    updatedTargetModes[MetadataFieldTarget.TITLE] = MetadataWriteMode.OVERWRITE
                                }
                                if (updatedTargetModes[MetadataFieldTarget.ARTIST] != MetadataWriteMode.DISABLED) {
                                    updatedTargetModes[MetadataFieldTarget.ARTIST] = MetadataWriteMode.OVERWRITE
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
                        startAction = { PreferenceIcon(LucideIcons.Parallel, PreferenceIconTone.Tools) },
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
                Spacer(modifier = Modifier.padding(vertical = 12.dp))
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
    target: MetadataFieldTarget,
    isSelected: Boolean,
    mode: MetadataWriteMode,
    onCheckedChange: (Boolean) -> Unit,
    onModeToggle: () -> Unit
) {
    if (com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
        com.lonx.lyrico.data.model.UiEngine.SALT
    ) {
        // SaltUI 没有「带尾部开关的勾选行」，用官方勾选图标 + Switcher 组合出同一语义。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = com.moriafly.salt.ui.SaltTheme.dimens.item)
                .toggleable(value = isSelected, role = Role.Checkbox, onValueChange = onCheckedChange)
                .innerPadding(vertical = false)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.moriafly.salt.ui.Icon(
                painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(
                    if (isSelected) com.moriafly.salt.ui.icons.SaltIcons.Check
                    else com.moriafly.salt.ui.icons.SaltIcons.Uncheck
                ),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = com.moriafly.salt.ui.SaltTheme.colors.highlight
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                com.moriafly.salt.ui.Text(
                    text = stringResource(target.labelRes),
                    style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                    color = if (isSelected) com.moriafly.salt.ui.SaltTheme.colors.text
                    else com.moriafly.salt.ui.SaltTheme.colors.subText,
                    maxLines = 1
                )
                if (isSelected) {
                    com.moriafly.salt.ui.Text(
                        text = stringResource(mode.labelRes),
                        style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                        color = com.moriafly.salt.ui.SaltTheme.colors.subText,
                        maxLines = 1
                    )
                }
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(48.dp).toggleable(
                    value = mode == MetadataWriteMode.OVERWRITE,
                    enabled = isSelected, role = Role.Switch,
                    onValueChange = { onModeToggle() },
                ), contentAlignment = Alignment.Center,
            ) {
                com.moriafly.salt.ui.Switcher(
                    state = mode == MetadataWriteMode.OVERWRITE,
                    modifier = Modifier.enabledAlpha(isSelected)
                )
            }
        }
        return
    }
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
                    checked = mode == MetadataWriteMode.OVERWRITE,
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

