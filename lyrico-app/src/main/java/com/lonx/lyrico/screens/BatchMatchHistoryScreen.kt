package com.lonx.lyrico.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lonx.lyrico.data.model.BatchMatchHistory
import com.lonx.lyrico.viewmodel.BatchMatchHistoryViewModel
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
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
            title = "是否确认删除？",
            content = "删除记录不可恢复",
            cancelText = "取消",
            confirmText = "确认"
        )
    }
    BasicScreenBox(
        title = "批量匹配记录",
        onBack = { navigator.popBackStack() }
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                RoundedColumn {
                    if (uiState.historyList.isEmpty()) {
                        ItemTip(text = "暂无记录")
                    }
                }
            }
            items(items = uiState.historyList, key = { it.id }) { history ->
                RoundedColumn(
                    type = RoundedColumnType.InList
                ) {
                    ItemExt(
                        onClick = {
                            navigator.navigate(BatchMatchHistoryDetailDestination(history.id))
                        },
                        text = dateFormat.format(Date(history.timestamp)),
                        sub = "成功: ${history.successCount} | 失败: ${history.failureCount} | 跳过: ${history.skippedCount}\n耗时: ${history.durationMillis / 1000.0}s",
                        iconEnd = {
                            IconButton(
                                onClick = {
                                    selectedHistoryId.value = history.id
                                }) {
                                Icon(painter = painterResource(R.drawable.ic_delete_24dp), contentDescription = "删除", tint = Color.Red)
                            }
                        },
                    )
                }

            }
        }
    }
}

@OptIn(UnstableSaltUiApi::class)
@Composable
fun HistoryItem(
    history: BatchMatchHistory,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var isLongClick by remember { mutableStateOf(false) }

    Item(
        text = dateFormat.format(Date(history.timestamp)),
        sub = "成功: ${history.successCount} | 失败: ${history.failureCount} | 跳过: ${history.skippedCount}\n耗时: ${history.durationMillis / 1000.0}s",
        onClick = onClick,
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { isLongClick = true },
                onTap = { onClick() }
            )
        }
    )

    PopupMenu(
        expanded = isLongClick,
        onDismissRequest = { isLongClick = false }
    ) {
        PopupMenuItem(
            text = "删除记录",
            onClick = {
                onDelete()
                isLongClick = false
            }
        )
    }
}
