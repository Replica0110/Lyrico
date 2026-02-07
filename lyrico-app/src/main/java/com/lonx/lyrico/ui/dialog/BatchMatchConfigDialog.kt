package com.lonx.lyrico.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.BatchMatchField
import com.lonx.lyrico.data.model.BatchMatchMode
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemContainer
import com.moriafly.salt.ui.ItemSlider
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Switcher
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.YesNoDialog
import com.moriafly.salt.ui.icons.Check
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.icons.Uncheck

@OptIn(UnstableSaltUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BatchMatchConfigDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (BatchMatchConfig) -> Unit
) {
    var config by remember {
        mutableStateOf(
            BatchMatchConfig(
                fields = BatchMatchField.entries.associateWith { BatchMatchMode.SUPPLEMENT },
                parallelism = 3
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

    YesNoDialog(
        onDismissRequest = onDismissRequest,
        title = "批量匹配配置",
        content = "",
        drawContent = {

            Column {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                val tempParallelism = remember(config.parallelism) {
                    mutableIntStateOf(config.parallelism)
                }

                ItemSlider(
                    text = "并发数",
                    sub = "${tempParallelism.intValue}",
                    steps = 3,
                    value = tempParallelism.intValue.toFloat(),
                    valueRange = 1f..5f,
                    onValueChangeFinished = {
                        config = config.copy(parallelism = tempParallelism.intValue)
                    },
                    onValueChange = {
                        tempParallelism.intValue = it.toInt()
                    }
                )

                ItemTip(
                    text = "并发数越高，匹配速度越快，但可能导致请求失败或被临时限制。"
                )
            }
        },
        onConfirm = {
            onDismissRequest()
            onConfirm(config)
        }
    )

}

@Composable
private fun BatchMatchFieldItem(
    field: BatchMatchField,
    isSelected: Boolean,
    mode: BatchMatchMode,
    onCheckedChange: (Boolean) -> Unit,
    onModeToggle: () -> Unit
) {
    ItemContainer(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSelected) SaltIcons.Check else SaltIcons.Uncheck,
                contentDescription = field.displayName,
                tint = if (isSelected) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                modifier = Modifier.weight(0.5f).clickable(onClick = { onCheckedChange(!isSelected) })
            )
            Text(
                text = field.displayName,
                style = SaltTheme.textStyles.main,
                color = if (isSelected) SaltTheme.colors.text else SaltTheme.colors.subText,
                modifier = Modifier
                    .weight(2f)
                    .padding(start = 6.dp)
            )



            if (isSelected) {
                Text(
                    text = if (mode == BatchMatchMode.OVERWRITE) "覆盖" else "补充",
                    style = SaltTheme.textStyles.sub,
                    color = if (mode == BatchMatchMode.OVERWRITE)
                        SaltTheme.colors.highlight
                    else
                        SaltTheme.colors.subText,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            Switcher(
                state = mode == BatchMatchMode.OVERWRITE,
                modifier = Modifier.clickable(
                    enabled = isSelected,
                    onClick = onModeToggle
                )
            )
        }
    }
}
