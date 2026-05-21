package com.lonx.lyrico.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.viewmodel.PluginDebugViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.SearchSourceConfigDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "plugin_debug")
fun PluginDebugScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: PluginDebugViewModel = koinViewModel()
    val plugins by viewModel.plugins.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importPlugin(context, it) }
    }

    LaunchedEffect(uiState.messageVersion) {
        if (uiState.message.isNotBlank()) {
            Toast.makeText(context, uiState.message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.plugin_debug_title),
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "actions") {
                SmallTitle(text = stringResource(R.string.plugin_debug_actions))
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    BasicComponent(
                        title = stringResource(R.string.plugin_debug_install_mock),
                        summary = stringResource(R.string.plugin_debug_install_mock_summary),
                        onClick = { if (!uiState.isBusy) viewModel.installMockPlugin(context) }
                    )
                    BasicComponent(
                        title = stringResource(R.string.plugin_debug_import_archive),
                        summary = stringResource(R.string.plugin_debug_import_archive_summary),
                        onClick = { if (!uiState.isBusy) importLauncher.launch(arrayOf("*/*")) }
                    )
                    BasicComponent(
                        title = stringResource(R.string.plugin_debug_run_smoke),
                        summary = stringResource(R.string.plugin_debug_run_smoke_summary),
                        onClick = { if (!uiState.isBusy) viewModel.runSmokeTest() }
                    )
                }
            }

            item(key = "plugins-title") {
                SmallTitle(text = stringResource(R.string.plugin_debug_installed_plugins))
            }

            if (plugins.isEmpty()) {
                item(key = "empty") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            text = stringResource(R.string.plugin_debug_empty),
                            modifier = Modifier.padding(16.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            } else {
                items(plugins, key = { it.id }) { plugin ->
                    PluginDebugItem(
                        plugin = plugin,
                        enabled = plugin.enabled,
                        onEnabledChanged = { if (!uiState.isBusy) viewModel.setEnabled(plugin.id, it) },
                        onConfigure = { navigator.navigate(SearchSourceConfigDestination(plugin.id)) },
                        onSmokeTest = { if (!uiState.isBusy) viewModel.runSmokeTest(plugin.id) },
                        onDelete = { if (!uiState.isBusy) viewModel.deletePlugin(plugin) }
                    )
                }
            }

            if (uiState.smokeResult.isNotBlank()) {
                item(key = "smoke-result") {
                    SmallTitle(text = stringResource(R.string.plugin_debug_smoke_result))
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        SelectionContainer {
                            Text(
                                text = uiState.smokeResult,
                                modifier = Modifier.padding(16.dp),
                                fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            if (uiState.isBusy) {
                item(key = "busy") {
                    Text(
                        text = stringResource(R.string.plugin_debug_busy),
                        modifier = Modifier.padding(16.dp),
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginDebugItem(
    plugin: SourcePluginEntity,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onConfigure: () -> Unit,
    onSmokeTest: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            BasicComponent(
                title = plugin.name,
                summary = "${plugin.id}  v${plugin.versionName}  api ${plugin.apiVersion}",
                endActions = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChanged
                    )
                }
            )

            Text(
                text = plugin.description.ifBlank { plugin.pluginDir },
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize
            )
            Spacer(Modifier.height(12.dp))

            Row {
                TextButton(
                    text = stringResource(R.string.plugin_debug_configure),
                    onClick = onConfigure,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.plugin_debug_test_this),
                    onClick = onSmokeTest,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.action_delete),
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
