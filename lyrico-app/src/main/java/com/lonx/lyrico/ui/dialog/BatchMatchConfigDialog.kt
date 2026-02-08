package com.lonx.lyrico.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.dialog.BasicDialog
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.BatchMatchField
import com.lonx.lyrico.data.model.BatchMatchMode
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemContainer
import com.moriafly.salt.ui.ItemSlider
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Switcher
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.icons.Check
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.icons.Uncheck
import com.moriafly.salt.ui.outerPadding

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

    BasicDialog(
        onDismissRequest = onDismissRequest,
    ) {
        // 标题
        Text(
            text = "批量匹配配置",
            modifier = Modifier.outerPadding(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SaltTheme.dimens.padding)
        ) {
            RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp)
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

            Spacer(modifier = Modifier.height(SaltTheme.dimens.subPadding))

            RoundedColumn(paddingValues = PaddingValues(0.dp)) {
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
        }

        Row(
            modifier = Modifier.outerPadding()
        ) {
            Button(
                onClick = onDismissRequest,
                text = "取消",
                modifier = Modifier.weight(1f),
                type = com.moriafly.salt.ui.ButtonType.Sub
            )
            Spacer(modifier = Modifier.width(SaltTheme.dimens.padding))
            Button(
                onClick = {
                    onDismissRequest()
                    onConfirm(config)
                },
                text = "确定",
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
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
    ItemContainer(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSelected) SaltIcons.Check else SaltIcons.Uncheck,
                contentDescription = field.displayName,
                tint = if (isSelected) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                modifier = Modifier
                    .size(SaltTheme.dimens.itemIcon)
                    .clickable(onClick = { onCheckedChange(!isSelected) })
            )

            Spacer(modifier = Modifier.width(SaltTheme.dimens.subPadding))

            Text(
                text = field.displayName,
                style = SaltTheme.textStyles.main,
                color = if (isSelected) SaltTheme.colors.text else SaltTheme.colors.subText,
                modifier = Modifier.weight(1f)
            )


            if (isSelected) {
                Text(
                    text = if (mode == BatchMatchMode.OVERWRITE) "覆盖" else "补充",
                    style = SaltTheme.textStyles.sub,
                    color = if (mode == BatchMatchMode.OVERWRITE)
                        SaltTheme.colors.highlight
                    else
                        SaltTheme.colors.subText
                )
            }

            Spacer(modifier = Modifier.width(SaltTheme.dimens.subPadding))

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

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun BatchMatchConfigDialogPreview() {
    SaltTheme {
        BatchMatchConfigDialog(
            onDismissRequest = {},
            onConfirm = {}
        )
    }
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
