@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.salt.SaltDropdownRow
import com.lonx.lyrico.ui.components.salt.SaltIconButton
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.lonx.lyrico.data.model.BatchTaskStatus
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.BatchTaskListViewModel
import com.moriafly.salt.ui.SaltTheme
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.moriafly.salt.ui.screen.ScreenCard
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
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
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
    val salt = LocalUiEngine.current == UiEngine.SALT

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

    val deleteMessage = if (pendingDeleteTaskIds.size > 1) {
        stringResource(
            R.string.batch_task_delete_selected_message,
            pendingDeleteTaskIds.size
        )
    } else {
        stringResource(R.string.batch_task_delete_message)
    }

    YesNoDialog(
        show = showDeleteDialog,
        title = stringResource(R.string.batch_task_delete_title),
        summary = deleteMessage,
        onDismissRequest = { showDeleteDialog = false },
        onDismissFinished = {
            if (!showDeleteDialog) {
                selectedTaskId = null
                pendingDeleteTaskIds = emptyList()
            }
        },
        onConfirm = {
            if (pendingDeleteTaskIds.isNotEmpty()) {
                viewModel.deleteTasks(pendingDeleteTaskIds)
            } else {
                selectedTaskId?.let(viewModel::deleteTask)
            }
            selectedTaskIds = emptySet()
            showDeleteDialog = false
        },
    )
    YesNoDialog(
        show = showClearDialog,
        title = stringResource(R.string.batch_task_clear_title),
        summary = stringResource(R.string.batch_task_clear_message),
        onDismissRequest = { showClearDialog = false },
        onConfirm = {
            viewModel.deleteTasks(deletableFilteredTaskIds)
            selectedTaskIds = emptySet()
            showClearDialog = false
        },
    )
    val canClearFinishedTasks = deletableFilteredTaskIds.isNotEmpty()
    val topBarBackdrop = rememberBarBlurBackdrop()
    LibraryDetailPage(
        title = stringResource(R.string.batch_task_list_title),
        onBack = { navigator.popBackStack() },
        actions = {
            SaltIconButton(
                icon = LucideIcons.Trash,
                label = stringResource(R.string.batch_task_clear_title),
                enabled = canClearFinishedTasks,
                tint = if (canClearFinishedTasks) SaltTheme.colors.error else SaltTheme.colors.subText,
                onClick = { showClearDialog = true }
            )
        },
        miuixTopBar = {
            BlurredTopBar(backdrop = topBarBackdrop) {
                SmallTopAppBar(
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    title = stringResource(R.string.batch_task_list_title),
                    navigationIcon = {
                        IconButton(
                            onClick = { navigator.popBackStack() }
                        ) {
                            Icon(
                                imageVector = LucideIcons.ArrowLeft,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            enabled = canClearFinishedTasks,
                            onClick = {
                                if (canClearFinishedTasks) {
                                    showClearDialog = true
                                }
                            }
                        ) {
                            Icon(
                                LucideIcons.Trash,
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
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = (if (salt) Modifier else Modifier
                .blurSource(topBarBackdrop)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection))
                .fillMaxHeight(),
            contentPadding = scaffoldContentPadding(
                paddingValues = paddingValues,
                bottomExtra = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = if (salt) rememberOverscrollEffect() else null,
        ) {
            item {
                val typeLabels = listOf(stringResource(R.string.batch_task_filter_all)) +
                        typeItems.map { stringResource(it) }
                val statusLabels = listOf(stringResource(R.string.batch_task_filter_all)) +
                        statusItems.map { stringResource(it) }
                if (salt) {
                    ScreenCard {
                        SaltDropdownRow(
                            title = stringResource(R.string.batch_task_filter_type),
                            icon = LucideIcons.ListFilter, tone = PreferenceIconTone.Appearance,
                            labels = typeLabels,
                            selectedIndex = selectedTypeIndex,
                        ) { index ->
                            viewModel.setFilterType(
                                if (index == 0) null else BatchTaskType.entries[index - 1]
                            )
                        }
                        SaltDropdownRow(
                            title = stringResource(R.string.batch_task_filter_status),
                            icon = LucideIcons.ListChecks, tone = PreferenceIconTone.Tools,
                            labels = statusLabels,
                            selectedIndex = selectedStatusIndex,
                        ) { index ->
                            viewModel.setFilterStatus(
                                if (index == 0) null else BatchTaskStatus.entries[index - 1]
                            )
                        }
                    }
                } else Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    WindowDropdownPreference(
                        startAction = { PreferenceIcon(LucideIcons.ListFilter, PreferenceIconTone.Appearance) },
                        title = stringResource(R.string.batch_task_filter_type),
                        items = typeLabels,
                        selectedIndex = selectedTypeIndex,
                        onSelectedIndexChange = { index ->
                            viewModel.setFilterType(
                                if (index == 0) null else BatchTaskType.entries[index - 1]
                            )
                        }
                    )
                    WindowDropdownPreference(
                        startAction = { PreferenceIcon(LucideIcons.ListChecks, PreferenceIconTone.Tools) },
                        title = stringResource(R.string.batch_task_filter_status),
                        items = statusLabels,
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
                    IllustratedEmptyState(
                        kind = EmptyStateKind.Tasks,
                        title = stringResource(R.string.batch_task_no_tasks),
                        modifier = Modifier.fillMaxWidth(),
                    )
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
    val isRunning =
        task.status == BatchTaskStatus.RUNNING || task.status == BatchTaskStatus.QUEUED

    if (LocalUiEngine.current == UiEngine.SALT) {
        com.moriafly.salt.ui.Card(modifier = modifier.padding(horizontal = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    BatchTaskCardDetails(
                        task = task,
                        formattedDate = formattedDate,
                        salt = true,
                        isRunning = isRunning
                    )
                }
                if (isRunning) {
                    SaltIconButton(
                        icon = LucideIcons.Close,
                        label = stringResource(R.string.action_close),
                        onClick = onCancelClick,
                        tint = SaltTheme.colors.error,
                        size = 36.dp,
                        iconSize = 20.dp
                    )
                } else {
                    SaltIconButton(
                        icon = LucideIcons.Trash,
                        label = stringResource(R.string.common_delete),
                        onClick = onDeleteClick,
                        tint = SaltTheme.colors.error,
                        size = 36.dp,
                        iconSize = 20.dp
                    )
                }
            }
        }
        return
    }

    Card(
        modifier = modifier.padding(horizontal = 12.dp)
    ) {
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
                            LucideIcons.Trash,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = MiuixTheme.colorScheme.error
                        )
                    }
                }
            }
        ) {
            BatchTaskCardDetails(
                task = task,
                formattedDate = formattedDate,
                salt = false,
                isRunning = isRunning
            )
        }
    }
}

/** 卡片正文：两引擎共用同一组字段与顺序，只换排版语义。 */
@Composable
private fun BatchTaskCardDetails(
    task: BatchTaskEntity,
    formattedDate: String,
    salt: Boolean,
    isRunning: Boolean,
) {
    val typeLabel = stringResource(task.type.labelRes)
    val statusLabel = stringResource(task.status.labelRes)
    val durationSecs = if (task.startedAt != null && task.finishedAt != null) {
        (task.finishedAt - task.startedAt) / 1000.0
    } else null
    val finished = task.status == BatchTaskStatus.SUCCEEDED ||
            task.status == BatchTaskStatus.FAILED ||
            task.status == BatchTaskStatus.CANCELLED

    if (!salt) {
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
            if (finished) {
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
            } else if (isRunning) {
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
        return
    }

    Column {
        com.moriafly.salt.ui.Text(
            text = formattedDate,
            style = SaltTheme.textStyles.main,
            fontWeight = FontWeight.Medium,
            color = SaltTheme.colors.text
        )
        Spacer(modifier = Modifier.height(8.dp))
        com.moriafly.salt.ui.Text(
            text = stringResource(R.string.batch_task_type_label) + typeLabel,
            style = SaltTheme.textStyles.sub,
            color = SaltTheme.colors.subText
        )
        com.moriafly.salt.ui.Text(
            text = stringResource(R.string.batch_task_status_label) + statusLabel,
            style = SaltTheme.textStyles.sub,
            color = SaltTheme.colors.subText
        )
        if (finished) {
            com.moriafly.salt.ui.Text(
                text = stringResource(
                    R.string.batch_match_stat_format,
                    task.successCount,
                    task.failureCount,
                    task.skippedCount
                ),
                style = SaltTheme.textStyles.sub,
                color = SaltTheme.colors.subText
            )
        } else if (isRunning) {
            com.moriafly.salt.ui.Text(
                text = "${task.current}/${task.total}",
                style = SaltTheme.textStyles.sub,
                color = SaltTheme.colors.subText
            )
        }
        if (durationSecs != null) {
            com.moriafly.salt.ui.Text(
                text = stringResource(
                    R.string.batch_match_duration_format,
                    durationSecs
                ),
                style = SaltTheme.textStyles.sub,
                color = SaltTheme.colors.subText
            )
        }
    }
}
