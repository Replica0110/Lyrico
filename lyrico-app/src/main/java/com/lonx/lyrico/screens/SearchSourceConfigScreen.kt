package com.lonx.lyrico.screens

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.ExtraMetadataWriteRule
import com.lonx.lyrico.data.model.ExtraWriteMode
import com.lonx.lyrico.ui.components.preference.SourceConfigFieldPreference
import com.lonx.lyrico.utils.isSatisfied
import com.lonx.lyrico.viewmodel.SearchSourceConfigViewModel
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.Source
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "search_source_config")
fun SearchSourceConfigScreen(
    sourceName: String,
    navigator: DestinationsNavigator
) {
    val viewModel: SearchSourceConfigViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val searchSources: List<SearchSource> = koinInject()
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(sourceName) {
        viewModel.load(sourceName)
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            Toast.makeText(context, R.string.source_config_saved, Toast.LENGTH_SHORT).show()
            viewModel.consumeSaved()
        }
    }

    val source = uiState.source
    val title = source?.let { stringResource(it.labelRes) } ?: stringResource(R.string.search_source_config)

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
                            viewModel.save(context.getString(R.string.source_config_required_error))
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
            val visibleFields = uiState.fields.filter { it.dependency.isSatisfied(uiState.values) }
            if (uiState.errorMessage != null) {
                item("error") {
                    Text(
                        text = stringResource(R.string.source_config_invalid_source),
                        modifier = Modifier.padding(12.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            } else if (!uiState.isLoading && visibleFields.isEmpty() && uiState.extraRules.isEmpty()) {
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

                if (source != null && uiState.extraRules.isNotEmpty()) {
                    val sourceImpl = searchSources.firstOrNull { it.sourceType == source }
                    item("extra_title") {
                        SmallTitle(text = stringResource(R.string.source_config_extra_metadata))
                    }
                    item("extra_card") {
                        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                            uiState.extraRules.forEach { rule ->
                                ExtraRulePreference(
                                    source = source,
                                    sourceImpl = sourceImpl,
                                    rule = rule,
                                    onRuleChanged = viewModel::updateExtraRule
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraRulePreference(
    source: Source,
    sourceImpl: SearchSource?,
    rule: ExtraMetadataWriteRule,
    onRuleChanged: (ExtraMetadataWriteRule) -> Unit
) {
    val field = sourceImpl?.extraFields?.firstOrNull { it.key == rule.normalizedKey }
    val modeItems = ExtraWriteMode.entries.map { stringResource(it.labelRes) }
    val selectedModeIndex = ExtraWriteMode.entries.indexOf(rule.mode).coerceAtLeast(0)
    WindowDropdownPreference(
        title = field?.title ?: rule.normalizedKey,
        summary = field?.summary.orEmpty().ifBlank { stringResource(source.labelRes) },
        items = modeItems,
        selectedIndex = selectedModeIndex,
        onSelectedIndexChange = { index ->
            onRuleChanged(rule.copy(key = rule.normalizedKey, mode = ExtraWriteMode.entries[index]))
        }
    )
}
