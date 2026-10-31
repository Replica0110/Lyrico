@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.salt.SaltBottomSheet
import com.lonx.lyrico.ui.components.salt.SaltDropdownRow
import com.lonx.lyrico.ui.components.salt.SaltIconButton
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.LogRetentionOption
import com.lonx.lyrico.data.model.entity.AppLogEntity
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.AppLogEvent
import com.lonx.lyrico.viewmodel.AppLogViewModel
import com.lonx.lyrico.ui.components.miuix.MiuixBottomSheet
import com.moriafly.salt.ui.SaltTheme
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.moriafly.salt.ui.screen.ScreenCard
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
@Destination<RootGraph>(route = "app_logs")
fun AppLogScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: AppLogViewModel = koinViewModel()
    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedLevel by remember { mutableStateOf<AppLogLevel?>(null) }
    var selectedType by remember { mutableStateOf<AppLogType?>(null) }
    var selectedLog by remember { mutableStateOf<AppLogEntity?>(null) }
    var selectedLogIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var pendingExportIds by rememberSaveable { mutableStateOf<List<Long>?>(null) }
    val copiedMessage = stringResource(R.string.msg_copied_to_clipboard)
    val logRetentionOption by viewModel.logRetentionOption.collectAsState()
    val logRetentionItems = LogRetentionOption.entries.map { stringResource(it.labelRes) }
    val selectedLogRetentionIndex = LogRetentionOption.entries.indexOf(logRetentionOption).coerceAtLeast(0)
    val filteredLogs by remember(viewModel, selectedLevel, selectedType) {
        viewModel.observeLogs(selectedLevel, selectedType)
    }.collectAsState(initial = emptyList())
    var preparingDelete by remember { mutableStateOf(false) }
    val prepareDelete: () -> Unit = {
        if (!preparingDelete) {
            preparingDelete = true
            val level = selectedLevel
            val type = selectedType
            scope.launch {
                try {
                    pendingDeleteIds = viewModel.getMatchingIds(level, type)
                    showDeleteConfirmDialog = pendingDeleteIds.isNotEmpty()
                } finally {
                    preparingDelete = false
                }
            }
        }
    }

    val levelItems = remember {
        AppLogLevel.entries.map { it.labelRes }
    }
    val typeItems = remember {
        AppLogType.entries.map { it.labelRes }
    }
    val selectedLevelIndex = remember(selectedLevel) {
        selectedLevel?.let { level -> AppLogLevel.entries.indexOf(level) + 1 } ?: 0
    }
    val selectedTypeIndex = remember(selectedType) {
        selectedType?.let { type -> AppLogType.entries.indexOf(type) + 1 } ?: 0
    }
    val salt = LocalUiEngine.current == UiEngine.SALT

    LaunchedEffect(filteredLogs) {
        val visibleIds = filteredLogs.mapTo(mutableSetOf()) { it.id }
        selectedLogIds = selectedLogIds.intersect(visibleIds)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { viewModel.exportLogs(context, it, pendingExportIds) }
        pendingExportIds = null
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AppLogEvent.ShowMessage -> {
                    event.message.asString(context)?.let { text ->
                        if (salt) android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
                        else snackbarHostState.showSnackbar(text)
                    }
                }
            }
        }
    }

    val topBarBackdrop = rememberBarBlurBackdrop()
    LibraryDetailPage(
        title = stringResource(R.string.app_log_title),
        onBack = { navigator.popBackStack() },
        actions = {
            SaltIconButton(
                icon = LucideIcons.Share2,
                label = stringResource(R.string.action_export_logs),
                enabled = filteredLogs.isNotEmpty(),
                onClick = {
                    pendingExportIds = filteredLogs.map { it.id }
                    exportLauncher.launch("lyrico_log_${System.currentTimeMillis()}.log")
                }
            )
            SaltIconButton(
                icon = LucideIcons.Trash,
                label = stringResource(R.string.action_delete),
                enabled = filteredLogs.isNotEmpty(),
                tint = SaltTheme.colors.error,
                onClick = {
                    prepareDelete()
                }
            )
        },
        // SaltUI 3.0.0-beta02 没有 Snackbar，Salt 侧改用平台 Toast（与项目其它 Salt 路径一致）。
        snackbarHost = { if (!salt) SnackbarHost(snackbarHostState) },
        miuixTopBar = {
            BlurredTopBar(backdrop = topBarBackdrop) {
                SmallTopAppBar(
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    title = stringResource(R.string.app_log_title),
                    navigationIcon = {
                        IconButton(onClick = { navigator.popBackStack() }) {
                            Icon(
                                imageVector = LucideIcons.ArrowLeft,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            enabled = filteredLogs.isNotEmpty(),
                            onClick = {
                                pendingExportIds = filteredLogs.map { it.id }
                                exportLauncher.launch("lyrico_log_${System.currentTimeMillis()}.log")
                            }
                        ) {
                            Icon(
                                imageVector = LucideIcons.Share2,
                                contentDescription = stringResource(R.string.action_export_logs)
                            )
                        }
                        IconButton(
                            enabled = filteredLogs.isNotEmpty(),
                            onClick = {
                                prepareDelete()
                            }
                        ) {
                            Icon(
                                imageVector = LucideIcons.Trash,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MiuixTheme.colorScheme.error
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
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .scrollEndHaptic()
                .overScrollVertical())
                .fillMaxSize(),
            contentPadding = scaffoldContentPadding(
                paddingValues = paddingValues,
                topExtra = 8.dp,
                bottomExtra = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item("filters") {
                val levelLabels = listOf(stringResource(R.string.app_log_filter_all)) +
                        levelItems.map { stringResource(it) }
                val typeLabels = listOf(stringResource(R.string.app_log_filter_all)) +
                        typeItems.map { stringResource(it) }
                if (salt) {
                    ScreenCard {
                        SaltDropdownRow(
                            title = stringResource(R.string.app_log_filter_level),
                            icon = LucideIcons.ListFilter, tone = PreferenceIconTone.Appearance,
                            labels = levelLabels,
                            selectedIndex = selectedLevelIndex,
                        ) { index ->
                            selectedLevel = if (index == 0) null else AppLogLevel.entries[index - 1]
                        }
                        SaltDropdownRow(
                            title = stringResource(R.string.app_log_filter_type),
                            icon = LucideIcons.Logs, tone = PreferenceIconTone.Tools,
                            labels = typeLabels,
                            selectedIndex = selectedTypeIndex,
                        ) { index ->
                            selectedType = if (index == 0) null else AppLogType.entries[index - 1]
                        }
                        SaltDropdownRow(
                            title = stringResource(R.string.log_retention_title),
                            icon = LucideIcons.Calendar, tone = PreferenceIconTone.Library,
                            labels = logRetentionItems,
                            selectedIndex = selectedLogRetentionIndex,
                        ) { index ->
                            viewModel.setLogRetentionOption(LogRetentionOption.entries[index])
                        }
                    }
                } else Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    WindowDropdownPreference(
                        startAction = { PreferenceIcon(LucideIcons.ListFilter, PreferenceIconTone.Appearance) },
                        title = stringResource(R.string.app_log_filter_level),
                        items = levelLabels,
                        selectedIndex = selectedLevelIndex,
                        onSelectedIndexChange = { index ->
                            selectedLevel = if (index == 0) null else AppLogLevel.entries[index - 1]
                        }
                    )
                    WindowDropdownPreference(
                        startAction = { PreferenceIcon(LucideIcons.Logs, PreferenceIconTone.Tools) },
                        title = stringResource(R.string.app_log_filter_type),
                        items = typeLabels,
                        selectedIndex = selectedTypeIndex,
                        onSelectedIndexChange = { index ->
                            selectedType = if (index == 0) null else AppLogType.entries[index - 1]
                        }
                    )
                    WindowDropdownPreference(
                        startAction = { PreferenceIcon(LucideIcons.Calendar, PreferenceIconTone.Library) },
                        title = stringResource(R.string.log_retention_title),
                        summary = stringResource(R.string.log_retention_summary),
                        items = logRetentionItems,
                        selectedIndex = selectedLogRetentionIndex,
                        onSelectedIndexChange = { index ->
                            viewModel.setLogRetentionOption(LogRetentionOption.entries[index])
                        }
                    )
                }
            }

            if (filteredLogs.isEmpty()) {
                item("empty") {
                    IllustratedEmptyState(
                        kind = EmptyStateKind.Logs,
                        title = stringResource(R.string.app_log_empty),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                items(filteredLogs, key = { it.id }) { log ->
                    AppLogItem(
                        log = log,
                        onClick = {
                            selectedLog = log
                            showDetailSheet = true
                        }
                    )
                }
            }
        }
    }

    AppLogDetailSheet(
        show = showDetailSheet,
        log = selectedLog,
        onDismiss = { showDetailSheet = false },
        onDismissFinished = {
            showDetailSheet = false
            selectedLog = null
        },
        onCopy = { log ->
            scope.launch {
                val clipData = ClipData.newPlainText("copy log", log.formatForCopy())
                val clipEntry = ClipEntry(clipData)
                clipboardManager.setClipEntry(clipEntry)
                if (salt) android.widget.Toast.makeText(context, copiedMessage, android.widget.Toast.LENGTH_SHORT).show()
                else snackbarHostState.showSnackbar(copiedMessage)
            }
            showDetailSheet = false
        }
    )

    YesNoDialog(
        show = showDeleteConfirmDialog,
        title = stringResource(R.string.app_log_delete_title),
        summary = stringResource(R.string.app_log_delete_message, pendingDeleteIds.size),
        onDismissRequest = { showDeleteConfirmDialog = false },
        onDismissFinished = {
            if (!showDeleteConfirmDialog) pendingDeleteIds = emptyList()
        },
        onConfirm = {
            viewModel.deleteLogs(pendingDeleteIds)
            selectedLogIds = emptySet()
            showDeleteConfirmDialog = false
        },
    )
}

@Composable
private fun AppLogItem(
    log: AppLogEntity,
    onClick: () -> Unit
) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    if (salt) {
        com.moriafly.salt.ui.Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SeverityBadge(log.level)
                        com.moriafly.salt.ui.Text(
                            text = stringResource(log.type.labelRes),
                            style = SaltTheme.textStyles.main,
                            fontWeight = FontWeight.Bold,
                            color = SaltTheme.colors.text,
                        )
                    }
                    com.moriafly.salt.ui.Text(
                        text = formatTime(log.createdAt),
                        style = SaltTheme.textStyles.sub,
                        color = SaltTheme.colors.subText,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                com.moriafly.salt.ui.Text(
                    text = log.message,
                    style = SaltTheme.textStyles.main,
                    color = SaltTheme.colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                com.moriafly.salt.ui.Text(
                    text = log.tag,
                    style = SaltTheme.textStyles.sub,
                    color = SaltTheme.colors.subText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                log.displayRelatedId?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    com.moriafly.salt.ui.Text(
                        text = it,
                        style = SaltTheme.textStyles.sub,
                        color = SaltTheme.colors.subText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        return
    }
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
    ) {
        BasicComponent(
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SeverityBadge(log.level)
                    Text(
                        text = stringResource(log.type.labelRes),
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
                Text(
                    style = MiuixTheme.textStyles.body2,
                    text = formatTime(log.createdAt),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = log.message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.tag,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            log.displayRelatedId?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SeverityBadge(level: AppLogLevel) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    val background = if (salt) {
        when (level) {
            AppLogLevel.ERROR -> SaltTheme.colors.error
            AppLogLevel.WARNING -> SaltTheme.colors.warning
            AppLogLevel.INFO -> SaltTheme.colors.highlight
            AppLogLevel.DEBUG -> SaltTheme.colors.subBackground
        }
    } else when (level) {
        AppLogLevel.ERROR -> MiuixTheme.colorScheme.error
        AppLogLevel.WARNING -> MiuixTheme.colorScheme.tertiaryContainer
        AppLogLevel.INFO -> MiuixTheme.colorScheme.primary
        AppLogLevel.DEBUG -> MiuixTheme.colorScheme.secondaryContainer
    }
    val content = if (salt) {
        when (level) {
            AppLogLevel.ERROR, AppLogLevel.INFO -> SaltTheme.colors.onHighlight
            AppLogLevel.WARNING, AppLogLevel.DEBUG -> SaltTheme.colors.text
        }
    } else when (level) {
        AppLogLevel.ERROR -> MiuixTheme.colorScheme.onError
        AppLogLevel.WARNING -> MiuixTheme.colorScheme.onSurface
        AppLogLevel.INFO -> MiuixTheme.colorScheme.onPrimary
        AppLogLevel.DEBUG -> MiuixTheme.colorScheme.onSurfaceVariantActions
    }
    if (salt) {
        com.moriafly.salt.ui.Text(
            text = stringResource(level.labelRes),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(background)
                .padding(horizontal = 7.dp, vertical = 2.dp),
            color = content,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        return
    }
    Text(
        text = stringResource(level.labelRes),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = content,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
}

@Composable
private fun AppLogDetailSheet(
    show: Boolean,
    log: AppLogEntity?,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onCopy: (AppLogEntity) -> Unit
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltBottomSheet(
            show = show,
            onDismissRequest = onDismiss,
            onDismissFinished = onDismissFinished,
            groupContent = true,
            // 详情是纯文本，没有自带滚动，由弹层滚动。
            scrollContent = true,
            title = stringResource(R.string.app_log_detail_title),
            header = log?.let { current ->
                {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SaltIconButton(
                            icon = LucideIcons.Copy,
                            label = stringResource(R.string.action_copy_log),
                            onClick = { onCopy(current) },
                        )
                    }
                }
            },
        ) {
            val current = log ?: return@SaltBottomSheet
            AppLogDetailContent(current)
        }
        return
    }
    MiuixBottomSheet(
        show = show,
        enableNestedScroll = false,
        title = stringResource(R.string.app_log_detail_title),
        tools = log?.let { current ->
            {
                IconButton(onClick = { onCopy(current) }) {
                    Icon(
                        imageVector = LucideIcons.Copy,
                        contentDescription = stringResource(R.string.action_copy_log)
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished
    ) {
        val current = log ?: return@MiuixBottomSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            AppLogDetailContent(current)
        }
    }
}

@Composable
private fun AppLogDetailContent(log: AppLogEntity) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        DetailRow(
            stringResource(R.string.app_log_field_time),
            formatDateTime(log.createdAt)
        )
        DetailRow(
            stringResource(R.string.app_log_field_level),
            stringResource(log.level.labelRes)
        )
        DetailRow(
            stringResource(R.string.app_log_field_type),
            stringResource(log.type.labelRes)
        )
        DetailRow(stringResource(R.string.app_log_field_tag), log.tag)
        log.displayRelatedId?.let {
            DetailRow(stringResource(R.string.app_log_field_related), it)
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (salt) com.moriafly.salt.ui.Text(
            text = stringResource(R.string.app_log_field_message),
            color = SaltTheme.colors.subText,
            style = SaltTheme.textStyles.sub,
            fontWeight = FontWeight.Bold
        ) else Text(
            text = stringResource(R.string.app_log_field_message),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontWeight = FontWeight.Bold
        )
        SelectionContainer {
            if (salt) com.moriafly.salt.ui.Text(
                text = log.message,
                modifier = Modifier.padding(top = 4.dp),
                color = SaltTheme.colors.text
            ) else Text(
                text = log.message,
                modifier = Modifier.padding(top = 4.dp),
                color = MiuixTheme.colorScheme.onSurface
            )
        }
        log.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            Spacer(modifier = Modifier.height(12.dp))
            if (salt) com.moriafly.salt.ui.Text(
                text = stringResource(R.string.app_log_field_detail),
                color = SaltTheme.colors.subText,
                style = SaltTheme.textStyles.sub,
                fontWeight = FontWeight.Bold
            ) else Text(
                text = stringResource(R.string.app_log_field_detail),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontWeight = FontWeight.Bold
            )
            SelectionContainer {
                if (salt) com.moriafly.salt.ui.Text(
                    text = detail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    color = SaltTheme.colors.text,
                    fontFamily = FontFamily.Monospace
                ) else Text(
                    text = detail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        if (salt) com.moriafly.salt.ui.Text(
            text = label,
            color = SaltTheme.colors.subText,
            style = SaltTheme.textStyles.sub,
            fontWeight = FontWeight.Bold
        ) else Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontWeight = FontWeight.Bold
        )
        SelectionContainer {
            if (salt) com.moriafly.salt.ui.Text(
                text = value,
                color = SaltTheme.colors.text,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            ) else Text(
                text = value,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


private val AppLogEntity.displayRelatedId: String?
    get() = relatedId?.let {
        if (level <= AppLogLevel.INFO) com.lonx.lyrico.data.model.log.LogFileReference.compact(it) else it
    }

private fun AppLogEntity.formatForCopy(): String = buildString {
    appendLine("${formatDateTime(createdAt)} [$level/$type] $tag")
    appendLine(message)
    displayRelatedId?.let { appendLine("Related: $it") }
    detail?.takeIf { it.isNotBlank() }?.let {
        appendLine()
        appendLine(it)
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

private fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

private fun Set<Long>.toggle(id: Long): Set<Long> =
    if (contains(id)) this - id else this + id
