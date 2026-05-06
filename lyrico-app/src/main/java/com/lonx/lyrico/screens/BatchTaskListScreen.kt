package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.BatchTaskStatus
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.viewmodel.BatchTaskListViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.BatchTaskDetailDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Destination<RootGraph>(route = "batch_task_list")
@Composable
fun BatchTaskListScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: BatchTaskListViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    val typeItems = remember {
        BatchTaskType.entries.map { it.labelRes }
    }
    val statusItems = remember {
        BatchTaskStatus.entries.map { it.labelRes }
    }
    val selectedTypeIndex = remember(uiState.filterType) {
        uiState.filterType?.let { type -> BatchTaskType.entries.indexOf(type) + 1 } ?: 0
    }
    val selectedStatusIndex = remember(uiState.filterStatus) {
        uiState.filterStatus?.let { status -> BatchTaskStatus.entries.indexOf(status) + 1 } ?: 0
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var selectedTaskIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeleteTaskIds by remember { mutableStateOf<List<String>>(emptyList()) }

    val deletableFilteredTaskIds = remember(uiState.filteredTasks) {
        uiState.filteredTasks
            .filterNot { it.status == BatchTaskStatus.RUNNING || it.status == BatchTaskStatus.QUEUED }
            .map { it.taskId }
    }

    LaunchedEffect(uiState.filteredTasks) {
        val visibleIds = uiState.filteredTasks.mapTo(mutableSetOf()) { it.taskId }
        selectedTaskIds = selectedTaskIds.intersect(visibleIds)
    }

    WindowDialog(
        title = stringResource(R.string.batch_task_delete_title),
        show = showDeleteDialog,
        onDismissRequest = {
            showDeleteDialog = false
        },
        onDismissFinished = {
            showDeleteDialog = false
            selectedTaskId = null
            pendingDeleteTaskIds = emptyList()
        }
    ) {
        Column {
            Text(
                text = if (pendingDeleteTaskIds.size > 1) {
                    stringResource(
                        R.string.batch_task_delete_selected_message,
                        pendingDeleteTaskIds.size
                    )
                } else {
                    stringResource(R.string.batch_task_delete_message)
                },
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
                        showDeleteDialog = false
                        selectedTaskId = null
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        if (pendingDeleteTaskIds.isNotEmpty()) {
                            viewModel.deleteTasks(pendingDeleteTaskIds)
                        } else {
                            selectedTaskId?.let(viewModel::deleteTask)
                        }
                        selectedTaskIds = emptySet()
                        showDeleteDialog = false
                        selectedTaskId = null
                        pendingDeleteTaskIds = emptyList()
                    },
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }

    WindowDialog(
        title = stringResource(R.string.batch_task_clear_title),
        show = showClearDialog,
        onDismissRequest = { showClearDialog = false }
    ) {
        Column {
            Text(
                text = stringResource(R.string.batch_task_clear_message),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showClearDialog = false },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        viewModel.deleteTasks(deletableFilteredTaskIds)
                        selectedTaskIds = emptySet()
                        showClearDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.batch_task_list_title),
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.popBackStack() }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    val canClearFinishedTasks = deletableFilteredTaskIds.isNotEmpty()
                    IconButton(
                        enabled = canClearFinishedTasks,
                        onClick = {
                            if (canClearFinishedTasks) {
                                showClearDialog = true
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete_24dp),
                            contentDescription = stringResource(R.string.batch_task_clear_title),
                            tint = if (canClearFinishedTasks) {
                                MiuixTheme.colorScheme.error
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantActions
                            }
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .fillMaxHeight(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null,
        ) {
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    WindowDropdownPreference(
                        title = stringResource(R.string.batch_task_filter_type),
                        items = listOf(stringResource(R.string.batch_task_filter_all)) + typeItems.map {
                            stringResource(
                                it
                            )
                        },
                        selectedIndex = selectedTypeIndex,
                        onSelectedIndexChange = { index ->
                            viewModel.setFilterType(
                                if (index == 0) null else BatchTaskType.entries[index - 1]
                            )
                        }
                    )
                    WindowDropdownPreference(
                        title = stringResource(R.string.batch_task_filter_status),
                        items = listOf(stringResource(R.string.batch_task_filter_all)) + statusItems.map {
                            stringResource(
                                it
                            )
                        },
                        selectedIndex = selectedStatusIndex,
                        onSelectedIndexChange = { index ->
                            viewModel.setFilterStatus(
                                if (index == 0) null else BatchTaskStatus.entries[index - 1]
                            )
                        }
                    )
                }
            }
            if (uiState.filteredTasks.isEmpty()) {
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        BasicComponent(title = stringResource(R.string.batch_task_no_tasks))
                    }
                }
            } else {
                items(
                    items = uiState.filteredTasks,
                    key = { it.taskId }
                ) { task ->
                    BatchTaskCard(
                        modifier = Modifier.animateItem(),
                        task = task,
                        formattedDate = dateFormat.format(Date(task.createdAt)),
                        onClick = {
                            navigator.navigate(BatchTaskDetailDestination(task.taskId))
                        },
                        onDeleteClick = {
                            selectedTaskId = task.taskId
                            pendingDeleteTaskIds = emptyList()
                            showDeleteDialog = true
                        },
                        onCancelClick = {
                            viewModel.cancelTask(task.taskId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchTaskCard(
    modifier: Modifier = Modifier,
    task: BatchTaskEntity,
    formattedDate: String,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Card(
        modifier = modifier.padding(horizontal = 12.dp)
    ) {
        val typeLabel = stringResource(task.type.labelRes)
        val statusLabel = stringResource(task.status.labelRes)
        val durationSecs = if (task.startedAt != null && task.finishedAt != null) {
            (task.finishedAt - task.startedAt) / 1000.0
        } else null

        val isRunning =
            task.status == BatchTaskStatus.RUNNING || task.status == BatchTaskStatus.QUEUED

        BasicComponent(
            onClick = onClick,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            endActions = {
                if (isRunning) {
                    IconButton(onClick = onCancelClick) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                            contentDescription = stringResource(R.string.action_close),
                            tint = MiuixTheme.colorScheme.error
                        )
                    }
                } else {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete_24dp),
                            contentDescription = stringResource(R.string.common_delete),
                            tint = MiuixTheme.colorScheme.error
                        )
                    }
                }
            }
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formattedDate,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.batch_task_type_label) + typeLabel,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.batch_task_status_label) + statusLabel,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (task.status == BatchTaskStatus.SUCCEEDED || task.status == BatchTaskStatus.FAILED || task.status == BatchTaskStatus.CANCELLED) {
                    Text(
                        text = stringResource(
                            R.string.batch_match_stat_format,
                            task.successCount,
                            task.failureCount,
                            task.skippedCount
                        ),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                } else if (task.status == BatchTaskStatus.RUNNING || task.status == BatchTaskStatus.QUEUED) {
                    Text(
                        text = "${task.current}/${task.total}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
                if (durationSecs != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.batch_match_duration_format,
                            durationSecs
                        ),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
