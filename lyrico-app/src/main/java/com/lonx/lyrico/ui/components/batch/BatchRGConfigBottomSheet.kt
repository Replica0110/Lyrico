@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.batch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.base.YesNoBottomSheet
import com.lonx.lyrico.ui.components.base.ConcurrencyDropdown
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
            val field: @Composable () -> Unit = {
                ConcurrencyDropdown(
                    title = stringResource(R.string.batch_replay_gain_concurrency),
                    summary = stringResource(R.string.batch_replay_gain_concurrency_tip),
                    value = tempConcurrency.intValue,
                    onChange = {
                        tempConcurrency.intValue = it
                        concurrency = it
                    },
                )
            }
            if (salt) {
                com.moriafly.salt.ui.RoundedColumn { field() }
            } else Card(
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
            ) { field() }
        },
        onDismissRequest = { onDismissRequest(concurrency) },
        onConfirm = {
            onConfirm(concurrency)
        }
    )
}
