package com.lonx.lyrico.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lonx.lyrico.viewmodel.BatchMatchHistoryViewModel
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.ItemExt
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumnType
import com.moriafly.salt.ui.dialog.YesNoDialog
import com.moriafly.salt.ui.lazy.LazyColumn
import com.moriafly.salt.ui.lazy.items
import com.ramcosta.composedestinations.generated.destinations.BatchMatchHistoryDetailDestination

@OptIn(UnstableSaltUiApi::class, ExperimentalMaterial3Api::class)
@Destination<RootGraph>(route = "batch_match_history")
@Composable
fun BatchMatchHistoryScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: BatchMatchHistoryViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val selectedHistoryId = remember { mutableStateOf<Long?>(null) }
    if (selectedHistoryId.value != null){
        YesNoDialog(
            onDismissRequest = {
                selectedHistoryId.value = null
            },
            onConfirm = {
                viewModel.deleteHistory(selectedHistoryId.value!!)
                selectedHistoryId.value = null
            },
            title = stringResource(R.string.batch_match_delete_title),
            content = stringResource(R.string.batch_match_delete_message),
            cancelText = stringResource(id = R.string.cancel),
            confirmText = stringResource(id = R.string.confirm)
        )
    }
    BasicScreenBox(
        title = stringResource(R.string.batch_match_history_title),
        onBack = { navigator.popBackStack() }
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                RoundedColumn {
                    if (uiState.historyList.isEmpty()) {
                        ItemTip(text = stringResource(R.string.batch_match_history_empty))
                    }
                }
            }
            items(items = uiState.historyList, key = { it.id }) { history ->
                RoundedColumn(
                    type = RoundedColumnType.InList
                ) {
                    val statText = stringResource(
                        R.string.batch_match_stat_format,
                        history.successCount,
                        history.failureCount,
                        history.skippedCount
                    )

                    val durationText = stringResource(
                        R.string.batch_match_duration_format,
                        history.durationMillis / 1000.0
                    )

                    ItemExt(

                        onClick = {
                            navigator.navigate(BatchMatchHistoryDetailDestination(history.id))
                        },
                        text = dateFormat.format(Date(history.timestamp)),
                        sub = "$statText\n$durationText",
                        iconEnd = {
                            IconButton(
                                onClick = {
                                    selectedHistoryId.value = history.id
                                }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete_24dp),
                                    contentDescription = stringResource(R.string.common_delete)
                                )
                            }
                        },
                    )
                }

            }
        }
    }
}

