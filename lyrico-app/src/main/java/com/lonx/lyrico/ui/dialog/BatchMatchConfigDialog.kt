package com.lonx.lyrico.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.BatchMatchField
import com.lonx.lyrico.data.model.BatchMatchMode
import com.moriafly.salt.ui.UnstableSaltUiApi
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@OptIn(UnstableSaltUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BatchMatchConfigDialog(
    title: String,
    show: MutableState<Boolean>,
    onDismissRequest: () -> Unit,
    onConfirm: (BatchMatchConfig) -> Unit
) {
    var config by remember {
        mutableStateOf(
            BatchMatchConfig(
                fields = BatchMatchField.entries.associateWith { BatchMatchMode.SUPPLEMENT },
                concurrency = 3
            )
        )
    }

    val allFields = remember { BatchMatchField.entries }

    fun updateField(field: BatchMatchField, isSelected: Boolean, mode: BatchMatchMode) {
        val currentMap = config.fields.toMutableMap()
        if (isSelected) {
            currentMap[field] = mode
        } else {
            currentMap.remove(field)
        }
        config = config.copy(fields = currentMap)
    }

    SuperDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title
    ) {

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
                    items(allFields, key = { it.name }) { field ->
                        val isSelected = config.fields.containsKey(field)
                        val mode = config.fields[field] ?: BatchMatchMode.SUPPLEMENT

                        BatchMatchFieldItem(
                            field = field,
                            isSelected = isSelected,
                            mode = mode,
                            onCheckedChange = { checked ->
                                updateField(field, checked, mode)
                            },
                            onModeToggle = {
                                updateField(
                                    field,
                                    isSelected,
                                    if (mode == BatchMatchMode.OVERWRITE)
                                        BatchMatchMode.SUPPLEMENT
                                    else
                                        BatchMatchMode.OVERWRITE
                                )
                            }
                        )
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
                SuperArrow(
                    title = stringResource(R.string.batch_match_config_concurrency),
                    endActions = {
                        Text(
                            text = "${tempConcurrency.intValue}",
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    },
                    onClick = {

                    },
                    bottomAction = {
                        Slider(
                            showKeyPoints = true,
                            valueRange = 1f .. 5f,
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

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = {
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        onDismissRequest()
                        onConfirm(config)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }

    }
}

@Composable
private fun BatchMatchFieldItem(
    field: BatchMatchField,
    isSelected: Boolean,
    mode: BatchMatchMode,
    onCheckedChange: (Boolean) -> Unit,
    onModeToggle: () -> Unit
) {
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        startAction = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onCheckedChange(!isSelected) }
            )
        },
        onClick =  {
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
                    checked = mode == BatchMatchMode.OVERWRITE,
                    onCheckedChange = { onModeToggle() },
                    enabled = isSelected
                )
            }
        }
    ) {
        Text(
            text = stringResource(field.labelRes),
            style = MiuixTheme.textStyles.main,
            color = if (isSelected) MiuixTheme.colorScheme.onSurfaceContainer else MiuixTheme.colorScheme.onSecondaryContainer,
        )
    }
//    ItemContainer(
//        modifier = Modifier.fillMaxWidth()
//    ) {
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Icon(
//                imageVector = if (isSelected) SaltIcons.Check else SaltIcons.Uncheck,
//                contentDescription = stringResource(field.labelRes),
//                tint = if (isSelected) SaltTheme.colors.highlight else SaltTheme.colors.subText,
//                modifier = Modifier
//                    .size(SaltTheme.dimens.itemIcon)
//                    .clickable(onClick = { onCheckedChange(!isSelected) })
//            )
//
//            Spacer(modifier = Modifier.width(SaltTheme.dimens.subPadding))
//
//            Text(
//                text = stringResource(field.labelRes),
//                style = SaltTheme.textStyles.main,
//                color = if (isSelected) SaltTheme.colors.text else SaltTheme.colors.subText,
//                modifier = Modifier.weight(1f)
//            )
//
//
//            if (isSelected) {
//                Text(
//                    text = if (mode == BatchMatchMode.OVERWRITE) {
//                        stringResource(R.string.batch_match_mode_overwrite)
//                    } else {
//                        stringResource(R.string.batch_match_mode_supplement)
//                    },
//                    style = SaltTheme.textStyles.sub,
//                    color = if (mode == BatchMatchMode.OVERWRITE)
//                        SaltTheme.colors.highlight
//                    else
//                        SaltTheme.colors.subText
//                )
//            }
//
//            Spacer(modifier = Modifier.width(SaltTheme.dimens.subPadding))
//
//            Switcher(
//                state = mode == BatchMatchMode.OVERWRITE,
//                modifier = Modifier.clickable(
//                    enabled = isSelected,
//                    onClick = onModeToggle
//                )
//            )
//        }
//    }
}



@Preview(showBackground = true)
@Composable
private fun BatchMatchFieldItemPreview() {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        BatchMatchFieldItem(
            field = BatchMatchField.TITLE,
            isSelected = true,
            mode = BatchMatchMode.SUPPLEMENT,
            onCheckedChange = {},
            onModeToggle = {}
        )

        Spacer(modifier = Modifier.height(8.dp))

        BatchMatchFieldItem(
            field = BatchMatchField.ARTIST,
            isSelected = true,
            mode = BatchMatchMode.OVERWRITE,
            onCheckedChange = {},
            onModeToggle = {}
        )

        Spacer(modifier = Modifier.height(8.dp))

        BatchMatchFieldItem(
            field = BatchMatchField.ALBUM,
            isSelected = false,
            mode = BatchMatchMode.SUPPLEMENT,
            onCheckedChange = {},
            onModeToggle = {}
        )
    }
}
