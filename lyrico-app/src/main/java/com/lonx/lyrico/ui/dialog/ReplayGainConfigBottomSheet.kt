package com.lonx.lyrico.ui.dialog

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
import com.lonx.lyrico.ui.components.base.YesNoBottomSheet
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun ReplayGainConfigBottomSheet(
    show: Boolean,
    initialConcurrency: Int,
    onDismissRequest: (Int) -> Unit,
    onConfirm: (Int) -> Unit
) {
    var concurrency by remember { mutableIntStateOf(initialConcurrency) }

    YesNoBottomSheet(
        show = show,
        onConfirm = {
            onConfirm(concurrency)
        },
        onDismissRequest = { onDismissRequest(concurrency) },
        title = stringResource(R.string.action_batch_replay_gain),
        content = {
            Card(
                modifier = Modifier.padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                )
            ) {
                val tempConcurrency = remember(initialConcurrency) {
                    mutableIntStateOf(initialConcurrency)
                }
                ArrowPreference(
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
        }
    )
}