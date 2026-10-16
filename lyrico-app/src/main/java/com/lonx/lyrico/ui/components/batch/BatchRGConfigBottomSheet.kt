@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.batch

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.base.YesNoBottomSheet
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun BatchRGConfigBottomSheet(
    show: Boolean,
    initialConcurrency: Int,
    onDismissRequest: (Int) -> Unit,
    onConfirm: (Int) -> Unit
) {
    var concurrency by remember { mutableIntStateOf(initialConcurrency) }
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
            com.lonx.lyrico.data.model.UiEngine.SALT
    val tempConcurrency = remember(initialConcurrency) {
        mutableIntStateOf(initialConcurrency)
    }

    YesNoBottomSheet(
        show = show,
        title = stringResource(R.string.action_batch_replay_gain),
        content = {
            if (salt) {
                com.moriafly.salt.ui.RoundedColumn {
                // 原生行负责内容边距，共享壳负责安全区与滚动。
                com.moriafly.salt.ui.ItemSlider(
                    value = tempConcurrency.intValue.toFloat(),
                    onValueChange = { tempConcurrency.intValue = it.roundToInt() },
                    text = stringResource(R.string.batch_replay_gain_concurrency),
                    sub = tempConcurrency.intValue.toString(),
                    valueRange = 1f..5f,
                    steps = 3,
                    onValueChangeFinished = { concurrency = tempConcurrency.intValue },
                )
                com.moriafly.salt.ui.ItemTip(
                    text = stringResource(R.string.batch_replay_gain_concurrency_tip)
                )
                }
            } else Card(
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                )
            ) {
                ArrowPreference(
                    startAction = { PreferenceIcon(LucideIcons.Parallel, PreferenceIconTone.Tools) },
                    title = stringResource(R.string.batch_replay_gain_concurrency),
                    endActions = {
                        Text(
                            text = "${tempConcurrency.intValue}",
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    },
                    insideMargin = PaddingValues(12.dp),
                    onClick = { },
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
                                concurrency = tempConcurrency.intValue
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.batch_replay_gain_concurrency_tip),
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                )
            }
            if (!salt) Spacer(modifier = Modifier.padding(vertical = 12.dp))
        },
        onDismissRequest = { onDismissRequest(concurrency) },
        onConfirm = {
            onConfirm(concurrency)
        }
    )
}
