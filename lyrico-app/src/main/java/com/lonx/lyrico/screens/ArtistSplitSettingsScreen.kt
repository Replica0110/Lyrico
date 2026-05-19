package com.lonx.lyrico.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.artist.ArtistSplitDefaults
import com.lonx.lyrico.data.model.artist.CustomArtistSeparator
import com.lonx.lyrico.data.model.artist.CustomNoSplitArtist
import com.lonx.lyrico.viewmodel.ArtistSplitSettingsViewModel
import com.lonx.lyrico.viewmodel.ArtistSplitValidationError
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
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
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
@OptIn(ExperimentalLayoutApi::class)
@Destination<RootGraph>(route = "artist_split_settings")
fun ArtistSplitSettingsScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: ArtistSplitSettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editingSeparator by remember { mutableStateOf<CustomArtistSeparator?>(null) }
    var addingSeparator by remember { mutableStateOf(false) }
    var editingNoSplitArtist by remember { mutableStateOf<CustomNoSplitArtist?>(null) }
    var addingNoSplitArtist by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PendingRuleDelete?>(null) }

    uiState.error?.let { error ->
        val message = when (error) {
            ArtistSplitValidationError.EMPTY -> stringResource(R.string.invalid_empty_input)
            ArtistSplitValidationError.DUPLICATE -> stringResource(R.string.duplicate_item)
            ArtistSplitValidationError.DUPLICATE_BUILTIN -> stringResource(R.string.duplicate_builtin_item)
            ArtistSplitValidationError.REBUILD_FAILED -> stringResource(R.string.artist_index_rebuild_failed)
        }
        LaunchedEffect(error) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    if (uiState.rebuildCompleted) {
        val message = stringResource(R.string.artist_index_rebuild_complete)
        LaunchedEffect(Unit) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearRebuildCompleted()
        }
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.artist_split_settings_title),
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
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
                bottom = paddingValues.calculateBottomPadding() + 12.dp
            ),
            overscrollEffect = null
        ) {
            item(key = "config") {
                SmallTitle(text = stringResource(R.string.artist_split_general_section))
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SwitchPreference(
                        title = stringResource(R.string.artist_split_enabled),
                        summary = stringResource(R.string.artist_split_enabled_summary),
                        checked = uiState.config.enabled,
                        onCheckedChange = viewModel::setEnabled
                    )
                    ArrowPreference(
                        title = stringResource(
                            if (uiState.hasPendingIndexRebuild) {
                                R.string.rebuild_artist_index_now
                            } else {
                                R.string.rebuild_artist_index
                            }
                        ),
                        onClick = viewModel::rebuildArtistIndex,
                        summary = when {
                            uiState.isRebuildingIndex -> stringResource(R.string.artist_index_rebuilding)
                            uiState.hasPendingIndexRebuild -> stringResource(R.string.artist_index_rebuild_pending)
                            else -> stringResource(R.string.artist_index_current)
                        }
                    )
                }
            }

            item(key = "separators") {
                SmallTitle(text = stringResource(R.string.section_artist_separators))
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    val separatorChips = ArtistSplitDefaults.BUILTIN_SEPARATORS
                        .filterNot { it.id in uiState.config.hiddenBuiltinSeparatorIds }
                        .map { separator ->
                            ArtistSplitChipItem(
                                id = separator.id,
                                text = separator.displayName,
                                checked = uiState.config.builtinSeparatorOverrides[separator.id]
                                    ?: separator.defaultEnabled,
                                isBuiltin = true
                            )
                        } + uiState.config.customSeparators.map { separator ->
                            ArtistSplitChipItem(
                                id = separator.id,
                                text = separator.value,
                                checked = separator.enabled,
                                isBuiltin = false
                            )
                        }

                    ChipGrid(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        separatorChips.forEach { chip ->
                            RuleChip(
                                text = chip.text,
                                checked = chip.checked,
                                onCheckedChange = {
                                    if (chip.isBuiltin) {
                                        viewModel.setBuiltinSeparatorEnabled(chip.id, it)
                                    } else {
                                        viewModel.setCustomSeparatorEnabled(chip.id, it)
                                    }
                                },
                                onDelete = {
                                    pendingDelete = PendingRuleDelete.Separator(chip.id, chip.text, chip.isBuiltin)
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = stringResource(R.string.add_custom_separator),
                            onClick = { addingSeparator = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                        TextButton(
                            text = stringResource(R.string.reset_to_default),
                            onClick = viewModel::resetSeparators,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item(key = "custom_no_split") {
                SmallTitle(text = stringResource(R.string.section_no_split_artists))
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    if (uiState.config.customNoSplitArtists.isNotEmpty()){
                        ChipGrid(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            uiState.config.customNoSplitArtists.forEach { artist ->
                                RuleChip(
                                    text = artist.name,
                                    checked = artist.enabled,
                                    onCheckedChange = {
                                        viewModel.setCustomNoSplitArtistEnabled(artist.id, it)
                                    },
                                    onDelete = {
                                        pendingDelete =
                                            PendingRuleDelete.NoSplitArtist(artist.id, artist.name)
                                    }
                                )
                            }
                        }
                    }
                    ArrowPreference(
                        title = stringResource(R.string.add_no_split_artist),
                        onClick = { addingNoSplitArtist = true }
                    )
                }
            }
        }
    }

    ArtistSplitInputDialog(
        show = addingSeparator,
        title = stringResource(R.string.add_custom_separator),
        initialValue = "",
        hint = stringResource(R.string.separator_input_hint),
        showDelete = false,
        onDismiss = { addingSeparator = false },
        onSave = {
            if (viewModel.addCustomSeparator(it)) addingSeparator = false
        }
    )

    ArtistSplitInputDialog(
        show = editingSeparator != null,
        title = stringResource(R.string.edit_custom_separator),
        initialValue = editingSeparator?.value.orEmpty(),
        hint = stringResource(R.string.separator_input_hint),
        showDelete = true,
        onDismiss = { editingSeparator = null },
        onDelete = {
            editingSeparator?.let { viewModel.removeCustomSeparator(it.id) }
            editingSeparator = null
        },
        onSave = { value ->
            val separator = editingSeparator ?: return@ArtistSplitInputDialog
            if (viewModel.updateCustomSeparator(separator.id, value)) editingSeparator = null
        }
    )

    ArtistSplitInputDialog(
        show = addingNoSplitArtist,
        title = stringResource(R.string.add_no_split_artist),
        initialValue = "",
        hint = stringResource(R.string.artist_name_input_hint),
        showDelete = false,
        onDismiss = { addingNoSplitArtist = false },
        onSave = {
            if (viewModel.addCustomNoSplitArtist(it)) addingNoSplitArtist = false
        }
    )

    ArtistSplitInputDialog(
        show = editingNoSplitArtist != null,
        title = stringResource(R.string.edit_no_split_artist),
        initialValue = editingNoSplitArtist?.name.orEmpty(),
        hint = stringResource(R.string.artist_name_input_hint),
        showDelete = true,
        onDismiss = { editingNoSplitArtist = null },
        onDelete = {
            editingNoSplitArtist?.let { viewModel.removeCustomNoSplitArtist(it.id) }
            editingNoSplitArtist = null
        },
        onSave = { name ->
            val artist = editingNoSplitArtist ?: return@ArtistSplitInputDialog
            if (viewModel.updateCustomNoSplitArtist(artist.id, name)) editingNoSplitArtist = null
        }
    )

    ConfirmRuleDeleteDialog(
        pendingDelete = pendingDelete,
        onDismiss = { pendingDelete = null },
        onConfirm = { delete ->
            when (delete) {
                is PendingRuleDelete.Separator -> {
                    if (delete.isBuiltin) {
                        viewModel.removeBuiltinSeparator(delete.id)
                    } else {
                        viewModel.removeCustomSeparator(delete.id)
                    }
                }
                is PendingRuleDelete.NoSplitArtist -> {
                    viewModel.removeCustomNoSplitArtist(delete.id)
                }
            }
            pendingDelete = null
        }
    )
}

private data class ArtistSplitChipItem(
    val id: String,
    val text: String,
    val checked: Boolean,
    val isBuiltin: Boolean
)

private sealed interface PendingRuleDelete {
    val id: String
    val label: String

    data class Separator(
        override val id: String,
        override val label: String,
        val isBuiltin: Boolean
    ) : PendingRuleDelete

    data class NoSplitArtist(
        override val id: String,
        override val label: String
    ) : PendingRuleDelete
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGrid(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
private fun RuleChip(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val background = if (checked) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MiuixTheme.colorScheme.secondaryContainer
    }
    val borderColor = if (checked) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.48f)
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.16f)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable { onCheckedChange(!checked) }
            .padding(start = 8.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (checked) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.16f)
                    }
                )
                .clickable { onCheckedChange(!checked) },
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    color = MiuixTheme.colorScheme.onPrimary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize
                )
            }
        }
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp),
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = MiuixIcons.Delete,
            contentDescription = stringResource(R.string.common_delete),
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
        )
    }
}

@Composable
private fun ArtistSplitInputDialog(
    show: Boolean,
    title: String,
    initialValue: String,
    hint: String,
    showDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit = {}
) {
    var text by remember(show, initialValue) { mutableStateOf(initialValue) }

    WindowDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = text,
                onValueChange = { text = it },
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = hint,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                if (showDelete) {
                    TextButton(
                        text = stringResource(R.string.common_delete),
                        onClick = onDelete,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                }
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = { onSave(text) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
private fun ConfirmRuleDeleteDialog(
    pendingDelete: PendingRuleDelete?,
    onDismiss: () -> Unit,
    onConfirm: (PendingRuleDelete) -> Unit
) {
    WindowDialog(
        show = pendingDelete != null,
        title = stringResource(R.string.artist_split_delete_rule_title),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(
                    R.string.artist_split_delete_rule_message,
                    pendingDelete?.label.orEmpty()
                ),
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.common_delete),
                    onClick = {
                        pendingDelete?.let(onConfirm)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}
