package com.lonx.lyrico.ui.components.batch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.ui.components.base.YesNoBottomSheet
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun BatchLyricsFormatConfigBottomSheet(
    show: Boolean,
    initialConcurrency: Int,
    initialTargetFormat: LyricFormat,
    onDismissRequest: (Int, LyricFormat) -> Unit,
    onConfirm: (Int, LyricFormat) -> Unit
) {
    var concurrency by remember(initialConcurrency) { mutableIntStateOf(initialConcurrency) }
    var targetFormat by remember(initialTargetFormat) { mutableStateOf(initialTargetFormat) }

    YesNoBottomSheet(
        show = show,
        onDismissRequest = { onDismissRequest(concurrency, targetFormat) },
        title = stringResource(R.string.action_batch_convert_lyrics_format),
        onConfirm = {
            onDismissRequest(concurrency, targetFormat)
            onConfirm(concurrency, targetFormat)
        },
        content = {
            Column(
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Card(
                    modifier = Modifier.padding(bottom = 12.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer,
                    )
                ) {
                    LyricFormat.entries.forEach { format ->
                        RadioButtonPreference(
                            title = stringResource(format.labelRes),
                            selected = format == targetFormat,
                            onClick = { targetFormat = format }
                        )
                    }
                }

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
        }
    )
}
