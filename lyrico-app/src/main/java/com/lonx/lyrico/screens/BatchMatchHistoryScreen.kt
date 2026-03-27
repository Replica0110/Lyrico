package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.viewmodel.BatchMatchHistoryViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.BatchMatchHistoryDetailDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Destination<RootGraph>(route = "batch_match_history")
@Composable
fun BatchMatchHistoryScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: BatchMatchHistoryViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val showConfirmDialog = remember { mutableStateOf(false) }
    var selectedHistoryId by remember { mutableStateOf<Long?>(null) }

    BasicScreenBox(
        title = stringResource(R.string.batch_match_history_title),
        onBack = { navigator.popBackStack() }
    ) {
        if (showConfirmDialog.value && selectedHistoryId != null) {
            SuperDialog(
                title = stringResource(R.string.batch_match_delete_title),
                show = showConfirmDialog,
                onDismissRequest = {
                    showConfirmDialog.value = false
                    selectedHistoryId = null
                }
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.batch_match_delete_message),
                        modifier = Modifier.fillMaxWidth(),
                        color = MiuixTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TextButton(
                            text = stringResource(R.string.cancel),
                            onClick = {
                                showConfirmDialog.value = false
                                selectedHistoryId = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = stringResource(R.string.confirm),
                            onClick = {
                                selectedHistoryId?.let(viewModel::deleteHistory)
                                showConfirmDialog.value = false
                                selectedHistoryId = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.historyList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        BasicComponent(
                            title = stringResource(R.string.batch_match_history_empty)
                        )
                    }
                }
            } else {
                items(
                    items = uiState.historyList,
                    key = { it.id }
                ) { history ->
                    BatchMatchHistoryCard(
                        formattedDate = dateFormat.format(Date(history.timestamp)),
                        statText = stringResource(
                            R.string.batch_match_stat_format,
                            history.successCount,
                            history.failureCount,
                            history.skippedCount
                        ),
                        durationText = stringResource(
                            R.string.batch_match_duration_format,
                            history.durationMillis / 1000.0
                        ),
                        onClick = {
                            navigator.navigate(BatchMatchHistoryDetailDestination(history.id))
                        },
                        onDeleteClick = {
                            selectedHistoryId = history.id
                            showConfirmDialog.value = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchMatchHistoryCard(
    formattedDate: String,
    statText: String,
    durationText: String,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        BasicComponent(
            title = formattedDate,
            summary = statText,
            endActions = {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = stringResource(R.string.common_delete),
                        modifier = Modifier.size(18.dp),
                        tint = MiuixTheme.colorScheme.error
                    )
                }
            },
            bottomAction = {
                Text(
                    text = durationText,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    fontSize = MiuixTheme.textStyles.body2.fontSize
                )
            },
            onClick = onClick
        )
    }
}
