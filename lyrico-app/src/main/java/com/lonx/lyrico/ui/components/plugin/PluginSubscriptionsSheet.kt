@file:OptIn(
    com.moriafly.salt.ui.UnstableSaltUiApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.lonx.lyrico.ui.components.plugin

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.data.model.entity.PluginSubscriptionEntity
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.model.entity.isEnabledAnywhere
import com.lonx.lyrico.plugin.source.PluginCatalog
import com.lonx.lyrico.plugin.source.PluginRelease
import com.lonx.lyrico.plugin.source.PluginTransferState
import com.lonx.lyrico.plugin.source.PluginUpdateState
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.ui.components.base.AppLinearProgressIndicator
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.base.PillButtonSize
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.salt.SaltIconButton
import com.lonx.lyrico.ui.components.salt.SaltTextInput
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.theme.isDarkTheme
import com.lonx.lyrico.viewmodel.PluginViewModel
import com.lonx.lyrico.viewmodel.SubscriptionEditorError
import com.lonx.lyrico.viewmodel.SubscriptionEditorState
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PluginSubscriptionsSheet(
    show: Boolean,
    viewModel: PluginViewModel,
    onDismiss: () -> Unit
) {
    val online = viewModel.online
    val salt = LocalUiEngine.current == UiEngine.SALT
    // RoundedColumn and ItemOuterTitle already provide Salt's outer spacing.
    val listPadding = if (salt) PaddingValues(0.dp) else PaddingValues(vertical = 4.dp)
    val listArrangement = if (salt) Arrangement.Top else Arrangement.spacedBy(8.dp)
    val sources by online.catalogs.subscriptions.collectAsState()
    val snapshots by online.catalogs.snapshots.collectAsState()
    val refreshing by online.catalogs.refreshing.collectAsState()
    val plugins by viewModel.plugins.collectAsState()
    val tasks by online.tasks.collectAsState()
    val editor by viewModel.subscriptionEditor.collectAsState()
    val installed = remember(plugins) { plugins.associateBy { it.id } }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<PluginSubscriptionEntity?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    val name = rememberTextFieldState()
    val url = rememberTextFieldState()
    val listState = rememberLazyListState()
    val catalogListState = rememberLazyListState()
    val selectedSource = sources.firstOrNull { it.id == selectedId }

    fun edit(source: PluginSubscriptionEntity?) {
        viewModel.beginSubscriptionEdit()
        editingId = source?.id
        name.setTextAndPlaceCursorAtEnd(source?.name.orEmpty())
        url.setTextAndPlaceCursorAtEnd(source?.url.orEmpty())
        showEditor = true
    }

    LaunchedEffect(show) {
        if (show) {
            online.checkUpdates()
        } else {
            showEditor = false
            showDelete = false
        }
    }
    LaunchedEffect(editor.savedId) {
        if (editor.savedId != null) showEditor = false
    }
    LaunchedEffect(sources.map { it.id }) {
        if (sources.none { it.id == selectedId }) selectedId = null
    }
    LaunchedEffect(selectedId) {
        if (selectedId != null) catalogListState.scrollToItem(0)
    }

    ActionBottomSheet(
        show = show,
        title = selectedSource?.name ?: stringResource(R.string.plugin_subscriptions_title),
        onDismissRequest = onDismiss,
        onDismissFinished = { selectedId = null },
        scrollContent = false,
        tools = {
            val inDetail = selectedId != null
            val isRefreshing = if (inDetail) selectedSource?.url in refreshing else refreshing.isNotEmpty()
            val refreshEnabled = !isRefreshing && (inDetail || sources.any { it.enabled })
            val colors = PillButtonDefaults.colors()
            val style = PillButtonDefaults.style(PillButtonSize.Small)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillButton(
                    text = stringResource(if (inDetail) R.string.action_back else R.string.plugin_subscription_add),
                    onClick = { if (inDetail) selectedId = null else edit(null) },
                    style = style,
                    colors = colors,
                    leading = {
                        Icon(
                            imageVector = if (inDetail) LucideIcons.ArrowLeft else LucideIcons.Plus,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.contentColor(selected = false, enabled = true)
                        )
                    }
                )
                PillButton(
                    text = stringResource(
                        if (isRefreshing) R.string.plugin_subscription_refreshing
                        else R.string.plugin_subscription_refresh
                    ),
                    enabled = refreshEnabled,
                    onClick = {
                        if (inDetail) selectedSource?.let { viewModel.refreshSubscription(it.url) }
                        else online.checkUpdates(force = true)
                    },
                    style = style,
                    colors = colors,
                    leading = {
                        Icon(
                            imageVector = LucideIcons.RefreshCw,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.contentColor(selected = false, enabled = refreshEnabled)
                        )
                    }
                )
            }
        },
        content = {
            BackHandler(enabled = show && selectedId != null && !showEditor && !showDelete) {
                selectedId = null
            }
            AnimatedContent(
                targetState = selectedId,
                modifier = Modifier.fillMaxWidth().clipToBounds(),
                transitionSpec = {
                    val direction = if (targetState != null) 1 else -1
                    slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it * direction } togetherWith
                        slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it * direction }
                },
                label = "subscriptionNavigation",
            ) { sourceId ->
                if (sourceId == null) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = listPadding,
                        verticalArrangement = listArrangement
                    ) {
                        if (sources.isEmpty()) {
                            item("empty") {
                                IllustratedEmptyState(
                                    kind = EmptyStateKind.Plugins,
                                    compact = true,
                                    title = stringResource(R.string.plugin_subscription_empty),
                                    summary = stringResource(R.string.plugin_subscription_empty_hint),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                                )
                            }
                        }
                        items(sources, key = { it.id }) { item ->
                            val snapshot = snapshots[item.url]
                            val status = subscriptionStatus(
                                item,
                                snapshot?.catalog,
                                snapshot?.record?.error,
                                installed
                            )
                            PluginSubscriptionItem(
                                source = item,
                                status = status,
                                actions = DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = stringResource(R.string.plugin_subscription_refresh),
                                            onClick = { viewModel.refreshSubscription(item.url) }
                                        ),
                                        DropdownItem(
                                            text = stringResource(R.string.common_edit),
                                            onClick = { edit(item) }
                                        ),
                                        DropdownItem(
                                            text = stringResource(
                                                if (item.enabled) R.string.plugin_subscription_disable
                                                else R.string.plugin_subscription_enable
                                            ),
                                            onClick = { viewModel.setSubscriptionEnabled(item.id, !item.enabled) }
                                        ),
                                        DropdownItem(
                                            text = stringResource(R.string.common_delete),
                                            onClick = {
                                                deleting = item
                                                showDelete = true
                                            }
                                        )
                                    )
                                ),
                                onOpen = { selectedId = item.id }
                            )
                        }
                    }
                } else {
                    val source = sources.firstOrNull { it.id == sourceId }
                    if (source != null) {
                        val snapshot = snapshots[source.url]
                        val catalog = snapshot?.catalog
                        val stored = snapshot?.record
                        val status = subscriptionStatus(source, catalog, stored?.error, installed)

                        LazyColumn(
                            state = catalogListState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = listPadding,
                            verticalArrangement = listArrangement
                        ) {
                            item("summary") {
                                SubscriptionDetailHeader(
                                    source = source,
                                    status = status
                                )
                            }
                            stored?.error?.let { error ->
                                item("error") {
                                    PluginSheetError(error)
                                }
                            }
                            if (catalog != null && catalog.plugins.isEmpty()) {
                                item("empty") {
                                    IllustratedEmptyState(
                                        kind = EmptyStateKind.Plugins,
                                        compact = true,
                                        title = stringResource(R.string.plugin_subscription_catalog_empty),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                                    )
                                }
                            } else if (catalog != null && catalog.plugins.isNotEmpty()) {
                                item("section_title") {
                                    PluginImportSectionTitle(
                                        stringResource(R.string.plugin_import_installable_title, catalog.plugins.size)
                                    )
                                }
                            }
                            items(catalog?.plugins.orEmpty(), key = { it.id }) { release ->
                                val current = installed[release.id]
                                val task = tasks[release.id]
                                PluginCatalogItem(
                                    release = release,
                                    installed = current,
                                    task = task,
                                    source = source,
                                    onAction = {
                                        if (task?.active == true) {
                                            online.cancel(release.id)
                                        } else {
                                            viewModel.installFromSubscription(source.id, release, current)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    )

    PluginSubscriptionEditor(
        show = show && showEditor,
        editing = editingId != null,
        name = name,
        url = url,
        state = editor,
        onDismiss = { if (!editor.saving) showEditor = false },
        onSave = { viewModel.saveSubscription(editingId, name.text.toString(), url.text.toString()) }
    )
    YesNoDialog(
        show = show && showDelete,
        title = stringResource(R.string.plugin_subscription_delete),
        summary = stringResource(R.string.plugin_subscription_delete_hint, deleting?.name.orEmpty()),
        onDismissRequest = { showDelete = false },
        onDismissFinished = { if (!showDelete) deleting = null },
        onConfirm = {
            deleting?.let { viewModel.deleteSubscription(it.id) }
            showDelete = false
        },
        confirmText = stringResource(R.string.common_delete)
    )
}

@Composable
private fun subscriptionStatus(
    source: PluginSubscriptionEntity,
    catalog: PluginCatalog?,
    error: String?,
    installed: Map<String, SourcePluginEntity>
): String {
    val available = catalog?.plugins.orEmpty()
    val upgrades = available.count { release ->
        release.compatible && installed[release.id]?.let { release.versionCode > it.versionCode } == true
    }
    return when {
        !source.enabled -> stringResource(R.string.plugin_subscription_disabled)
        error != null -> stringResource(
            if (catalog == null) R.string.plugin_subscription_fetch_failed
            else R.string.plugin_subscription_cached_error
        )
        catalog == null -> stringResource(R.string.plugin_subscription_not_loaded)
        else -> stringResource(R.string.plugin_subscription_summary, available.size, upgrades)
    }
}

/** 专门针对插件订阅源设计的列表项组件：干净、干练，绝无冗余多余文本 */
@Composable
private fun PluginSubscriptionItem(
    source: PluginSubscriptionEntity,
    status: String,
    actions: DropdownEntry,
    onOpen: () -> Unit
) {
    val enabled = source.enabled
    val salt = LocalUiEngine.current == UiEngine.SALT

    if (salt) {
        val popup = com.moriafly.salt.ui.popup.rememberPopupState()
        RoundedColumn(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!enabled) Modifier.alpha(0.6f) else Modifier)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    com.moriafly.salt.ui.Text(
                        text = source.name,
                        style = SaltTheme.textStyles.main.copy(fontWeight = FontWeight.SemiBold),
                        color = SaltTheme.colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    com.moriafly.salt.ui.Text(
                        text = source.url,
                        style = SaltTheme.textStyles.sub,
                        color = SaltTheme.colors.subText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    com.moriafly.salt.ui.Text(
                        text = status,
                        style = SaltTheme.textStyles.sub,
                        color = SaltTheme.colors.subText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    SaltIconButton(
                        icon = LucideIcons.EllipsisVertical,
                        label = stringResource(R.string.cd_more_actions),
                        onClick = popup::expend
                    )
                    com.moriafly.salt.ui.popup.PopupMenu(
                        expanded = popup.expend,
                        onDismissRequest = popup::dismiss
                    ) {
                        actions.items.forEach { item ->
                            com.moriafly.salt.ui.popup.PopupMenuItem(
                                text = item.text,
                                sub = item.summary,
                                onClick = {
                                    popup.dismiss()
                                    item.onClick?.invoke()
                                }
                            )
                        }
                    }
                }
            }
        }
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!enabled) Modifier.alpha(0.6f) else Modifier),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = source.url,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = status,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            WindowIconDropdownMenu(entry = actions) {
                Icon(LucideIcons.EllipsisVertical, stringResource(R.string.cd_more_actions))
            }
        }
    }
}

/** 订阅详情页头部卡片 */
@Composable
private fun SubscriptionDetailHeader(
    source: PluginSubscriptionEntity,
    status: String
) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    if (salt) {
        RoundedColumn(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                com.moriafly.salt.ui.Text(
                    text = source.url,
                    style = SaltTheme.textStyles.sub,
                    color = SaltTheme.colors.subText
                )
                com.moriafly.salt.ui.Text(
                    text = status,
                    style = SaltTheme.textStyles.main.copy(fontWeight = FontWeight.Medium),
                    color = SaltTheme.colors.text
                )
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = source.url,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = status,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** 订阅包含的插件条目：不显示可能过长的 changelog，干净对齐全局插件条目 */
@Composable
private fun PluginCatalogItem(
    release: PluginRelease,
    installed: SourcePluginEntity?,
    task: PluginTransferState?,
    source: PluginSubscriptionEntity,
    onAction: () -> Unit
) {
    val active = task?.active == true
    val failed = task?.phase == PluginTransferState.Phase.FAILED && task.sourceUrl == source.url &&
        task.release == release && task.expectedInstalledDir == installed?.pluginDir
    val cancelled = task?.phase == PluginTransferState.Phase.CANCELLED && task.sourceUrl == source.url &&
        task.release == release && task.expectedInstalledDir == installed?.pluginDir
    val same = installed?.versionCode == release.versionCode
    val action = stringResource(when {
        active -> R.string.action_abort
        failed || cancelled -> R.string.plugin_online_retry
        !release.compatible -> R.string.plugin_online_incompatible
        installed == null -> R.string.plugin_import_install
        release.versionCode > installed.versionCode -> R.string.plugin_update
        release.versionCode < installed.versionCode -> R.string.plugin_import_conflict_downgrade
        else -> R.string.plugin_task_installed
    })
    val enabled = active || (source.enabled && release.compatible && !same)
    val version = installed?.takeIf { !same }?.let { "${it.versionName} → ${release.versionName}" } ?: release.versionName

    val actionText = when {
        !active -> action
        task.phase == PluginTransferState.Phase.DOWNLOADING -> "${task.percent}%"
        task.phase == PluginTransferState.Phase.VERIFYING -> stringResource(R.string.plugin_task_verifying)
        task.phase == PluginTransferState.Phase.INSTALLING -> stringResource(R.string.plugin_online_installing)
        else -> action
    }
    val actionIcon = when {
        active -> LucideIcons.Close
        failed || cancelled -> LucideIcons.RefreshCw
        !release.compatible -> LucideIcons.Ban
        same -> LucideIcons.Check
        else -> LucideIcons.Download
    }

    val summary: List<String> = buildList {
        if (active && task.sourceUrl != source.url) add(stringResource(R.string.plugin_transfer_other_source, task.sourceUrl))
        if (!release.compatible) add(stringResource(R.string.plugin_api_versions_with_value, release.apiVersion, release.minHostApiVersion))
        if (installed != null && !installed.isEnabledAnywhere) add(stringResource(R.string.plugin_subscription_plugin_disabled))
        if (failed) add(stringResource(R.string.plugin_action_failed, task.error.orEmpty()))
    }
    PluginImportEntry(
        name = release.name,
        id = release.id,
        version = version,
        details = summary,
        compatible = release.compatible,
        enabled = enabled,
        trailingContent = {
            val salt = LocalUiEngine.current == UiEngine.SALT
            val contentColor = if (salt) SaltTheme.colors.text
                else MiuixTheme.colorScheme.onSurface.copy(alpha = if (isDarkTheme) 0.7f else 0.9f)
            val colors = PillButtonDefaults.colors(
                contentColor = contentColor,
                containerColor = if (salt) SaltTheme.colors.text.copy(alpha = 0.08f)
                    else MiuixTheme.colorScheme.surface
            )
            PillButton(
                text = actionText,
                enabled = enabled,
                onClick = onAction,
                style = PillButtonDefaults.style(PillButtonSize.Small),
                colors = colors,
                leading = {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.contentColor(selected = false, enabled = enabled)
                    )
                }
            )
        }
    )
}

/** 添加/编辑订阅弹窗 */
@Composable
private fun PluginSubscriptionEditor(
    show: Boolean,
    editing: Boolean,
    name: TextFieldState,
    url: TextFieldState,
    state: SubscriptionEditorState,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val focus = remember { FocusRequester() }
    YesNoDialog(
        show = show,
        title = stringResource(if (editing) R.string.plugin_subscription_edit else R.string.plugin_subscription_add),
        onDismissRequest = onDismiss,
        onConfirm = onSave,
        dismissOnConfirm = false,
        confirmEnabled = !state.saving && url.text.isNotBlank(),
        content = {
            LaunchedEffect(show) { if (show) focus.requestFocus() }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (LocalUiEngine.current == UiEngine.SALT) {
                    RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                        SaltTextInput(
                            state = name,
                            hint = stringResource(R.string.plugin_subscription_name),
                            readOnly = state.saving
                        )
                    }
                    RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                        SaltTextInput(
                            state = url,
                            hint = stringResource(R.string.plugin_online_url),
                            readOnly = state.saving,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.focusRequester(focus)
                        )
                    }
                } else {
                    TextField(
                        state = name,
                        label = stringResource(R.string.plugin_subscription_name),
                        useLabelAsPlaceholder = true,
                        readOnly = state.saving,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        state = url,
                        label = stringResource(R.string.plugin_online_url),
                        useLabelAsPlaceholder = true,
                        readOnly = state.saving,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                state.error?.let {
                    PluginSheetError(stringResource(when (it) {
                        SubscriptionEditorError.DUPLICATE -> R.string.plugin_subscription_duplicate
                        SubscriptionEditorError.INVALID_URL -> R.string.plugin_subscription_invalid_url
                        SubscriptionEditorError.SAVE_FAILED -> R.string.plugin_subscription_save_failed
                    }))
                }
                if (state.saving) {
                    AppLinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    )
}
