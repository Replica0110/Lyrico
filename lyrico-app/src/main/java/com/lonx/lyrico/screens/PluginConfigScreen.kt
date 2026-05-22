package com.lonx.lyrico.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.MetadataFieldTarget
import com.lonx.lyrico.data.model.MetadataFieldWriteRule
import com.lonx.lyrico.data.model.MetadataWriteMode
import com.lonx.lyrico.data.model.plugin.PluginMetadataField
import com.lonx.lyrico.ui.components.preference.SourceConfigFieldPreference
import com.lonx.lyrico.utils.isSatisfied
import com.lonx.lyrico.viewmodel.SearchSourceConfigViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
@Destination<RootGraph>(route = "plugin_config")
fun PluginConfigScreen(
    pluginId: String,
    navigator: DestinationsNavigator
) {
    val viewModel: SearchSourceConfigViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var editingFieldKey by remember { mutableStateOf<String?>(null) }


    val requiredMessage = stringResource(R.string.source_config_required_error)
    LaunchedEffect(pluginId) {
        viewModel.load(pluginId)
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            Toast.makeText(context, R.string.source_config_saved, Toast.LENGTH_SHORT).show()
            viewModel.consumeSaved()
        }
    }

    val title = uiState.title.ifBlank { stringResource(R.string.plugin_config_title) }
    val editingField = uiState.metadataFields.firstOrNull { it.key == editingFieldKey }
    val editingRule = uiState.metadataRules.firstOrNull { it.normalizedKey == editingFieldKey }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.save(requiredMessage)
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Ok,
                            contentDescription = stringResource(R.string.source_config_save)
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
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 12.dp
            ),
            overscrollEffect = null
        ) {
            val visibleFields = uiState.configFields.filter { it.dependency.isSatisfied(uiState.values) }
            if (uiState.errorMessage != null) {
                item("error") {
                    Text(
                        text = stringResource(R.string.source_config_invalid_source),
                        modifier = Modifier.padding(12.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            } else if (!uiState.isLoading && visibleFields.isEmpty() && uiState.metadataRules.isEmpty()) {
                item("empty") {
                    Text(
                        text = stringResource(R.string.source_config_empty),
                        modifier = Modifier.padding(12.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            } else {
                if (visibleFields.isNotEmpty()) {
                    item("basic_title") {
                        SmallTitle(text = stringResource(R.string.source_config_basic))
                    }
                    item("basic_card") {
                        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                            visibleFields.forEach { field ->
                                SourceConfigFieldPreference(
                                    field = field,
                                    value = uiState.values[field.key].orEmpty(),
                                    values = uiState.values,
                                    error = uiState.validationErrors[field.key],
                                    onValueChange = { viewModel.updateValue(field.key, it) }
                                )
                            }
                        }
                    }
                }

                if (uiState.metadataRules.isNotEmpty()) {
                    item("extra_title") {
                        SmallTitle(text = stringResource(R.string.source_config_metadata_rules))
                    }
                    uiState.metadataFields
                        .groupBy { it.group.ifBlank { "extended" } }
                        .forEach { (group, fields) ->
                            item("metadata_group_$group") {
                                SmallTitle(text = group)
                            }
                            item("metadata_card_$group") {
                                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                                    fields.forEach { field ->
                                        val rule = uiState.metadataRules.firstOrNull {
                                            it.normalizedKey == field.key
                                        } ?: return@forEach
                                        MetadataRulePreference(
                                            field = field,
                                            rule = rule,
                                            onClick = { editingFieldKey = field.key }
                                        )
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    MetadataRuleBottomSheet(
        field = editingField,
        rule = editingRule,
        onDismiss = { editingFieldKey = null },
        onRuleChanged = viewModel::updateMetadataRule
    )
}

@Composable
private fun MetadataRulePreference(
    field: PluginMetadataField,
    rule: MetadataFieldWriteRule,
    onClick: () -> Unit
) {
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        onClick = onClick,
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(rule.mode.labelRes),
                    style = MiuixTheme.textStyles.footnote2
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(rule.target.labelRes),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
        }
    ) {
        Column {
            Text(text = field.title.ifBlank { field.key })
            if (field.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = field.summary,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
        }
    }
}

@Composable
private fun MetadataRuleBottomSheet(
    field: PluginMetadataField?,
    rule: MetadataFieldWriteRule?,
    onDismiss: () -> Unit,
    onRuleChanged: (MetadataFieldWriteRule) -> Unit
) {
    WindowBottomSheet(
        show = field != null && rule != null,
        onDismissRequest = onDismiss
    ) {
        val currentField = field ?: return@WindowBottomSheet
        val currentRule = rule ?: return@WindowBottomSheet
        val targetCandidates = currentField.targetOptions
            .takeIf { it.isNotEmpty() }
            ?.mapNotNull { target ->
                MetadataFieldTarget.entries.firstOrNull { it.name == target.name }
            }
            ?: listOf(
                MetadataFieldTarget.entries.firstOrNull { it.name == currentField.defaultTarget.name }
                    ?: MetadataFieldTarget.COMMENT
            )
        val selectedModeIndex = MetadataWriteMode.entries.indexOf(currentRule.mode).coerceAtLeast(0)
        val selectedTargetIndex = targetCandidates.indexOf(currentRule.target).coerceAtLeast(0)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(text = currentField.title.ifBlank { currentField.key })
                if (currentField.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentField.summary,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }
            Card {
                WindowDropdownPreference(
                    title = stringResource(R.string.source_config_write_mode),
                    items = MetadataWriteMode.entries.map { stringResource(it.labelRes) },
                    selectedIndex = selectedModeIndex,
                    onSelectedIndexChange = { index ->
                        onRuleChanged(
                            currentRule.copy(
                                fieldKey = currentRule.normalizedKey,
                                mode = MetadataWriteMode.entries[index]
                            )
                        )
                    }
                )
                WindowDropdownPreference(
                    title = stringResource(R.string.source_config_write_target),
                    items = targetCandidates.map { stringResource(it.labelRes) },
                    selectedIndex = selectedTargetIndex,
                    enabled = currentField.targetOptions.isNotEmpty(),
                    onSelectedIndexChange = { index ->
                        targetCandidates.getOrNull(index)?.let { target ->
                            onRuleChanged(
                                currentRule.copy(
                                    fieldKey = currentRule.normalizedKey,
                                    target = target
                                )
                            )
                        }
                    }
                )
                if (currentRule.target == MetadataFieldTarget.CUSTOM) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(text = stringResource(R.string.source_config_custom_tag_key))
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = currentRule.customTagKey.orEmpty(),
                            maxLines = 1,
                            onValueChange = { value ->
                                onRuleChanged(
                                    currentRule.copy(
                                        fieldKey = currentRule.normalizedKey,
                                        customTagKey = value.takeIf { it.isNotBlank() }
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
