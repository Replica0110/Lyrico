package com.lonx.lyrico.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.MetadataFieldTarget
import com.lonx.lyrico.data.model.MetadataFieldWriteRule
import com.lonx.lyrico.data.model.MetadataWriteMode
import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginConfigFieldType
import com.lonx.lyrico.data.model.plugin.PluginMetadataField
import com.lonx.lyrico.plugin.source.toMetadataFieldTarget
import com.lonx.lyrico.utils.isSatisfied
import com.lonx.lyrico.viewmodel.SearchSourceConfigViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.SwitchPreference
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
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    var editingFieldKey by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var showMetadataRuleSheet by rememberSaveable {
        mutableStateOf(false)
    }

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

    val editingField = remember(editingFieldKey, uiState.metadataFields) {
        uiState.metadataFields.firstOrNull { it.key == editingFieldKey }
    }

    val editingRule = remember(editingFieldKey, uiState.pluginId, uiState.metadataRules) {
        uiState.metadataRules.firstOrNull {
            it.pluginId == uiState.pluginId && it.normalizedKey == editingFieldKey
        }
    }

    val hasConfigContent by remember {
        derivedStateOf {
            uiState.errorMessage == null &&
                    !uiState.isLoading &&
                    uiState.configFields.any { it.dependency.isSatisfied(uiState.values) }
        }
    }

    val hasMetadataContent by remember {
        derivedStateOf {
            uiState.errorMessage == null &&
                    !uiState.isLoading &&
                    uiState.metadataFields.isNotEmpty()
        }
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(paddingValues)
        ) {
            if (uiState.errorMessage != null) {
                Text(
                    text = stringResource(R.string.source_config_invalid_source),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                return@Scaffold
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp)
            ) {
                TabRowWithContour(
                    tabs = listOf(
                        stringResource(R.string.source_config_tab_config),
                        stringResource(R.string.source_config_tab_metadata)
                    ),
                    selectedTabIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                )
            }

            if (!hasConfigContent && !hasMetadataContent) {
                Text(
                    text = stringResource(R.string.source_config_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                return@Scaffold
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> PluginConfigTab(
                        fields = uiState.configFields,
                        values = uiState.values,
                        validationErrors = uiState.validationErrors,
                        hasContent = hasConfigContent,
                        topAppBarScrollBehavior = topAppBarScrollBehavior,
                        onValueChange = viewModel::updateValue
                    )

                    1 -> PluginMetadataTab(
                        pluginId = uiState.pluginId,
                        metadataFields = uiState.metadataFields,
                        metadataRules = uiState.metadataRules,
                        hasContent = hasMetadataContent,
                        topAppBarScrollBehavior = topAppBarScrollBehavior,
                        onEditField = { fieldKey ->
                            editingFieldKey = fieldKey
                            showMetadataRuleSheet = true
                        }
                    )
                }
            }
        }
    }

    MetadataRuleBottomSheet(
        show = showMetadataRuleSheet,
        field = editingField,
        rule = editingRule,
        onDismissRequest = {
            showMetadataRuleSheet = false
        },
        onDismissFinished = {
            editingFieldKey = null
        },
        onRuleChanged = viewModel::updateMetadataRule
    )
}

@Composable
private fun PluginConfigTab(
    fields: List<PluginConfigField>,
    values: Map<String, String>,
    validationErrors: Map<String, String>,
    hasContent: Boolean,
    topAppBarScrollBehavior: ScrollBehavior,
    onValueChange: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(bottom = 12.dp),
        overscrollEffect = null
    ) {
        if (!hasContent) {
            item("empty_config") {
                Text(
                    text = stringResource(R.string.source_config_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
            return@LazyColumn
        }

        pluginConfigFormItems(
            fields = fields,
            values = values,
            validationErrors = validationErrors,
            onValueChange = onValueChange
        )
    }
}

@Composable
private fun PluginMetadataTab(
    pluginId: String,
    metadataFields: List<PluginMetadataField>,
    metadataRules: List<MetadataFieldWriteRule>,
    hasContent: Boolean,
    topAppBarScrollBehavior: ScrollBehavior,
    onEditField: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(bottom = 12.dp),
        overscrollEffect = null
    ) {
        if (!hasContent) {
            item("empty_metadata") {
                Text(
                    text = stringResource(R.string.source_config_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
            return@LazyColumn
        }

        metadataRuleItems(
            pluginId = pluginId,
            metadataFields = metadataFields,
            metadataRules = metadataRules,
            onEditField = onEditField
        )
    }
}

private fun LazyListScope.pluginConfigFormItems(
    fields: List<PluginConfigField>,
    values: Map<String, String>,
    validationErrors: Map<String, String>,
    onValueChange: (String, String) -> Unit
) {
    if (fields.isEmpty()) return

    val grouped = fields.groupBy { field ->
        field.group.ifBlank { DEFAULT_CONFIG_GROUP }
    }

    grouped.forEach { (group, groupFields) ->
        val hasVisibleField = groupFields.any { field ->
            field.dependency.isSatisfied(values)
        }

        if (!hasVisibleField) {
            return@forEach
        }

        item("config_title_$group") {
            SmallTitle(
                text = if (group == DEFAULT_CONFIG_GROUP) {
                    stringResource(R.string.source_config_basic)
                } else {
                    group
                }
            )
        }

        item("config_card_$group") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                Column(
                    modifier = Modifier.animateContentSize()
                ) {
                    groupFields.forEach { field ->
                        val visible by remember(field.dependency, values) {
                            derivedStateOf {
                                field.dependency.isSatisfied(values)
                            }
                        }

                        AnimatedVisibility(visible = visible) {
                            PluginConfigFormItem(
                                field = field,
                                value = values[field.key].orEmpty(),
                                error = validationErrors[field.key],
                                onValueChange = { value ->
                                    onValueChange(field.key, value)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginConfigFormItem(
    field: PluginConfigField,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit
) {
    when (field.type) {
        PluginConfigFieldType.SWITCH -> {
            SwitchPreference(
                title = field.title,
                summary = helperText(field, error),
                checked = value.toBooleanStrictOrNull() ?: false,
                onCheckedChange = { checked ->
                    onValueChange(checked.toString())
                }
            )
        }

        PluginConfigFieldType.DROPDOWN -> {
            val selectedIndex = field.options
                .indexOfFirst { it.value == value }
                .coerceAtLeast(0)

            WindowDropdownPreference(
                title = field.title,
                summary = helperText(field, error),
                items = field.options.map { it.label },
                selectedIndex = selectedIndex,
                enabled = field.options.isNotEmpty(),
                onSelectedIndexChange = { index ->
                    field.options.getOrNull(index)?.let { option ->
                        onValueChange(option.value)
                    }
                }
            )
        }

        PluginConfigFieldType.TEXT,
        PluginConfigFieldType.PASSWORD,
        PluginConfigFieldType.NUMBER -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = value,
                    label = field.title,
                    maxLines = 1,
                    visualTransformation = if (field.type == PluginConfigFieldType.PASSWORD) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = if (field.type == PluginConfigFieldType.NUMBER) {
                        KeyboardOptions(keyboardType = KeyboardType.Number)
                    } else {
                        KeyboardOptions.Default
                    },
                    onValueChange = { input ->
                        onValueChange(
                            if (field.type == PluginConfigFieldType.NUMBER) {
                                input.filter(Char::isDigit)
                            } else {
                                input
                            }
                        )
                    }
                )

                helperText(field, error).takeIf { it.isNotBlank() }?.let { helper ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = helper,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = if (error == null) {
                            MiuixTheme.colorScheme.onSurfaceVariantActions
                        } else {
                            MiuixTheme.colorScheme.error
                        }
                    )
                }
            }
        }
    }
}

private fun LazyListScope.metadataRuleItems(
    pluginId: String,
    metadataFields: List<PluginMetadataField>,
    metadataRules: List<MetadataFieldWriteRule>,
    onEditField: (String) -> Unit
) {
    if (metadataFields.isEmpty()) return

    val rulesByKey = metadataRules
        .filter { it.pluginId == pluginId }
        .associateBy { it.normalizedKey }

    val grouped = metadataFields.groupBy { field ->
        field.group.ifBlank { DEFAULT_METADATA_GROUP }
    }

    item("metadata_title") {
        SmallTitle(text = stringResource(R.string.source_config_metadata_rules))
    }

    grouped.forEach { (group, fields) ->
        val visibleFields = fields.filter { field ->
            rulesByKey.containsKey(field.key)
        }

        if (visibleFields.isEmpty()) {
            return@forEach
        }

        item("metadata_group_title_$group") {
            SmallTitle(text = group)
        }

        item("metadata_card_$group") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                Column {
                    visibleFields.forEach { field ->
                        val rule = rulesByKey[field.key] ?: return@forEach
                        MetadataRulePreference(
                            field = field,
                            rule = rule,
                            onClick = {
                                onEditField(field.key)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRulePreference(
    field: PluginMetadataField,
    rule: MetadataFieldWriteRule,
    onClick: () -> Unit
) {
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
        endActions = {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Text(
                    text = stringResource(rule.mode.labelRes),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = when (rule.mode) {
                        MetadataWriteMode.DISABLED ->
                            MiuixTheme.colorScheme.onSurfaceVariantActions
                        MetadataWriteMode.SUPPLEMENT,
                        MetadataWriteMode.OVERWRITE ->
                            MiuixTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = stringResource(rule.target.labelRes),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Text(
                text = field.title.ifBlank { field.key },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = field.summary.ifBlank { field.key },
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetadataRuleBottomSheet(
    show: Boolean,
    field: PluginMetadataField?,
    rule: MetadataFieldWriteRule?,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onRuleChanged: (MetadataFieldWriteRule) -> Unit
) {
    WindowBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished
    ) {
        val currentField = field ?: return@WindowBottomSheet
        val currentRule = rule ?: return@WindowBottomSheet

        val targetCandidates = remember(currentField) {
            currentField.targetOptions
                .takeIf { it.isNotEmpty() }
                ?.map { it.toMetadataFieldTarget() }
                ?: listOf(currentField.defaultTarget.toMetadataFieldTarget())
        }

        val selectedModeIndex = MetadataWriteMode.entries
            .indexOf(currentRule.mode)
            .coerceAtLeast(0)

        val selectedTargetIndex = targetCandidates
            .indexOf(currentRule.target)
            .coerceAtLeast(0)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SmallTitle(
                text = currentField.title,
                insideMargin = PaddingValues(4.dp)
            )
            Card(
                modifier = Modifier.padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                )
            ) {
                WindowDropdownPreference(
                    title = stringResource(R.string.source_config_write_mode),
                    items = MetadataWriteMode.entries.map { stringResource(it.labelRes) },
                    selectedIndex = selectedModeIndex,
                    onSelectedIndexChange = { index ->
                        MetadataWriteMode.entries.getOrNull(index)?.let { mode ->
                            onRuleChanged(
                                currentRule.copy(
                                    fieldKey = currentRule.normalizedKey,
                                    mode = mode
                                )
                            )
                        }
                    }
                )

                WindowDropdownPreference(
                    title = stringResource(R.string.source_config_write_target),
                    items = targetCandidates.map { stringResource(it.labelRes) },
                    selectedIndex = selectedTargetIndex,
                    enabled = targetCandidates.isNotEmpty(),
                    onSelectedIndexChange = { index ->
                        targetCandidates.getOrNull(index)?.let { target ->
                            onRuleChanged(
                                currentRule.copy(
                                    fieldKey = currentRule.normalizedKey,
                                    target = target,
                                    customTagKey = if (target == MetadataFieldTarget.CUSTOM) {
                                        currentRule.customTagKey
                                    } else {
                                        null
                                    }
                                )
                            )
                        }
                    }
                )

                AnimatedVisibility(visible = currentRule.target == MetadataFieldTarget.CUSTOM) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.source_config_custom_tag_key),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )

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

private fun helperText(field: PluginConfigField, error: String?): String {
    return listOfNotNull(
        field.summary.takeIf { it.isNotBlank() },
        error
    ).joinToString("\n")
}

private const val DEFAULT_CONFIG_GROUP = "__basic__"
private const val DEFAULT_METADATA_GROUP = "extended"
