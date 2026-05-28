package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.viewmodel.CustomTagManagementViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
@Destination<RootGraph>(route = "custom_tag_management")
fun CustomTagManagementScreen(
    navigator: DestinationsNavigator,
) {
    val viewModel: CustomTagManagementViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()

    val visibleItems = uiState.visibleKeys.map { key -> key to uiState.keyCounts[key] }
    val discoveredItems = uiState.keyCounts
        .filterKeys { key ->
            uiState.searchQuery.isBlank() ||
                key.contains(uiState.searchQuery, ignoreCase = true)
        }
        .toList()
        .sortedWith(
            compareByDescending<Pair<String, Int>> { it.second }
                .thenBy { it.first.lowercase() }
        )

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.custom_tag_management_title),
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(
                            MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .fillMaxHeight(),
            contentPadding = scaffoldContentPadding(
                paddingValues = paddingValues,
                bottomExtra = 12.dp
            ),
            overscrollEffect = null,
        ) {
            item(key = "visible") {
                SmallTitle(text = stringResource(R.string.custom_tag_visible_section))
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(
                        text = stringResource(R.string.custom_tag_visible_summary),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    if (visibleItems.isEmpty()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = stringResource(R.string.custom_tag_empty_visible_title),
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.custom_tag_empty_visible_summary),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    } else {
                        visibleItems.forEach { (key, count) ->
                            CustomTagRow(
                                keyName = key,
                                songCount = count,
                                actionText = stringResource(R.string.custom_tag_remove_visible),
                                onAction = { viewModel.removeVisibleKey(key) },
                            )
                        }
                    }
                    ArrowPreference(
                        title = stringResource(R.string.custom_tag_add_key),
                        summary = stringResource(R.string.custom_tag_remove_notice),
                        onClick = { showAddDialog = true },
                    )
                }
            }

            item(key = "discovered") {
                SmallTitle(text = stringResource(R.string.custom_tag_discovered_section))
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(
                        text = stringResource(R.string.custom_tag_discovered_summary),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    TextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        label = stringResource(R.string.custom_tag_search_hint),
                        leadingIcon = {
                            Icon(
                                MiuixIcons.Search,
                                contentDescription = stringResource(R.string.custom_tag_search_hint)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    discoveredItems.forEach { (key, count) ->
                        val isVisible = key in uiState.visibleKeys
                        CustomTagRow(
                            keyName = key,
                            songCount = count,
                            actionText = stringResource(
                                if (isVisible) {
                                    R.string.custom_tag_already_visible
                                } else {
                                    R.string.custom_tag_add_to_visible
                                }
                            ),
                            actionEnabled = !isVisible,
                            onAction = { viewModel.addVisibleKey(key) },
                        )
                    }
                }
            }
        }
    }

    AddCustomTagKeyDialog(
        show = showAddDialog,
        errorText = uiState.inputError,
        onDismiss = {
            showAddDialog = false
            viewModel.clearInputError()
        },
        onConfirm = { key ->
            viewModel.addVisibleKey(key)
            showAddDialog = false
        },
    )
}

@Composable
private fun CustomTagRow(
    keyName: String,
    songCount: Int?,
    actionText: String,
    actionEnabled: Boolean = true,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = keyName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (songCount != null) {
                    stringResource(R.string.custom_tag_song_count, songCount)
                } else {
                    stringResource(R.string.custom_tag_song_count, 0)
                },
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        TextButton(
            text = actionText,
            enabled = actionEnabled,
            onClick = onAction,
        )
    }
}

@Composable
private fun AddCustomTagKeyDialog(
    show: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!show) return

    WindowDialog(
        show = show,
        title = stringResource(R.string.custom_tag_add_key),
        onDismissRequest = onDismiss,
    ) {
        var key by remember { mutableStateOf("") }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.custom_tag_key_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = key,
                onValueChange = { key = it },
                label = stringResource(R.string.custom_tag_key),
                modifier = Modifier.fillMaxWidth(),
            )
            if (errorText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = { onConfirm(key) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
