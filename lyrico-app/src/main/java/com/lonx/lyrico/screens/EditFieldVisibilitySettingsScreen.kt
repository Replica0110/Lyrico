package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.editfield.EditFieldScope
import com.lonx.lyrico.viewmodel.EditFieldVisibilityGroupUiState
import com.lonx.lyrico.viewmodel.EditFieldVisibilitySettingsViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
@Destination<RootGraph>(route = "edit_field_visibility")
fun EditFieldVisibilitySettingsScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: EditFieldVisibilitySettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedGroupCode by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var showBottomSheet by rememberSaveable {
        mutableStateOf(false)
    }

    val selectedGroup = remember(uiState.groups, selectedGroupCode) {
        uiState.groups.firstOrNull { it.group.code == selectedGroupCode }
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.edit_field_visibility_settings),
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = null)
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .overScrollVertical()
                .scrollEndHaptic(),
        ) {
            items(
                items = uiState.groups,
                key = { it.group.code },
            ) { groupState ->
                EditFieldVisibilityGroupItem(
                    groupState = groupState,
                    selected = selectedGroupCode == groupState.group.code,
                    onClick = {
                        selectedGroupCode = groupState.group.code
                        showBottomSheet = true
                    },
                    onGroupCheckedChange = { checked ->
                        viewModel.setGroupVisible(groupState.group.code, checked)
                    },
                )
            }

            item {
                TextButton(
                    text = stringResource(R.string.reset_to_default),
                    onClick = viewModel::resetAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
        }

        WindowBottomSheet(
            show = showBottomSheet,
            title = selectedGroup?.let { stringResource(it.group.titleRes) }.orEmpty(),
            onDismissRequest = {
                showBottomSheet = false
            },
            onDismissFinished = {
                selectedGroupCode = null
            },
        ) {
            selectedGroup?.let { groupState ->
                EditFieldVisibilityGroupBottomSheetContent(
                    groupState = groupState,
                    onGroupCheckedChange = { checked ->
                        viewModel.setGroupVisible(groupState.group.code, checked)
                    },
                    onFieldCheckedChange = { fieldCode, checked ->
                        viewModel.setFieldVisible(fieldCode, checked)
                    },
                )
            }
        }
    }
}
@Composable
private fun EditFieldVisibilityGroupItem(
    groupState: EditFieldVisibilityGroupUiState,
    selected: Boolean,
    onClick: () -> Unit,
    onGroupCheckedChange: (Boolean) -> Unit,
) {
    val selectedCount = groupState.fields.count { it.checked }
    val totalCount = groupState.fields.size

    val preview = groupState.fields
        .take(4)
        .map { fieldState -> stringResource(fieldState.field.titleRes) }
        .joinToString(separator = ", ")
        .let { text ->
            if (groupState.fields.size > 4) "$text..." else text
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        BasicComponent(
            onClick = onClick,
            holdDownState = selected,
            endActions = {
                Switch(
                    checked = groupState.checked,
                    onCheckedChange = onGroupCheckedChange,
                )
            },
        ) {
            Text(
                text = stringResource(groupState.group.titleRes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(
                    R.string.edit_field_visibility_group_summary,
                    selectedCount,
                    totalCount,
                ),
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (preview.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = preview,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EditFieldGroupFieldsCard(
    groupState: EditFieldVisibilityGroupUiState,
    onFieldCheckedChange: (String, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer,
        ),
    ) {
        groupState.fields.forEach { fieldState ->
            SwitchPreference(
                title = stringResource(fieldState.field.titleRes),
                summary = stringResource(R.string.edit_field_visibility_field_scope_summary,stringResource(fieldState.field.scope.toStringRes())),
                checked = fieldState.checked,
                enabled = groupState.checked && fieldState.enabled,
                onCheckedChange = { checked ->
                    onFieldCheckedChange(fieldState.field.code, checked)
                },
            )
        }
    }
}
@Composable
private fun EditFieldVisibilityGroupBottomSheetContent(
    groupState: EditFieldVisibilityGroupUiState,
    onGroupCheckedChange: (Boolean) -> Unit,
    onFieldCheckedChange: (String, Boolean) -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        EditFieldGroupStatusCard(
            groupState = groupState,
            onGroupCheckedChange = onGroupCheckedChange,
        )

        EditFieldGroupFieldsCard(
            groupState = groupState,
            onFieldCheckedChange = onFieldCheckedChange,
        )
    }
}
@Composable
private fun EditFieldGroupStatusCard(
    groupState: EditFieldVisibilityGroupUiState,
    onGroupCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer,
        ),
    ) {
        BasicComponent(
            endActions = {
                Switch(
                    checked = groupState.checked,
                    onCheckedChange = onGroupCheckedChange,
                )
            },
        ) {
            Text(
                text = stringResource(R.string.edit_field_visibility_group_enabled),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.edit_field_visibility_group_enabled_summary),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            )
        }
    }
}
