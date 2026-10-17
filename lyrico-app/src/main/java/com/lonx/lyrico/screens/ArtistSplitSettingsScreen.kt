@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lonx.lyrico.screens

import com.lonx.lyrico.ui.components.salt.SaltTextInput

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.lonx.lyrico.ui.components.library.libraryDetailScroll
import com.lonx.lyrico.ui.components.salt.SaltIconButton
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.screen.ScreenCard
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.data.model.artist.ArtistSplitDefaults
import com.lonx.lyrico.data.model.artist.CustomArtistSeparator
import com.lonx.lyrico.data.model.artist.CustomNoSplitArtist
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.viewmodel.ArtistSplitSettingsViewModel
import com.lonx.lyrico.viewmodel.ArtistSplitValidationError
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
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

    val salt = LocalUiEngine.current == UiEngine.SALT
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

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val topBarBackdrop = rememberBarBlurBackdrop()
    LibraryDetailPage(
        title = stringResource(R.string.artist_split_settings_title),
        onBack = { navigator.popBackStack() },
        miuixTopBar = {
            BlurredTopBar(backdrop = topBarBackdrop) {
                SmallTopAppBar(
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    title = stringResource(R.string.artist_split_settings_title),
                    navigationIcon = {
                        IconButton(onClick = { navigator.popBackStack() }) {
                            Icon(LucideIcons.ArrowLeft, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    scrollBehavior = topAppBarScrollBehavior
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = (if (salt) Modifier else Modifier.blurSource(topBarBackdrop))
                .libraryDetailScroll(topAppBarScrollBehavior)
                .fillMaxHeight(),
            contentPadding = scaffoldContentPadding(
                paddingValues = paddingValues,
                bottomExtra = 12.dp
            ),
            overscrollEffect = if (salt) rememberOverscrollEffect() else null
        ) {
            if (salt) {
                item(key = "config") {
                    ItemOuterTitle(stringResource(R.string.artist_split_general_section))
                    ScreenCard {
                        ItemSwitcher(
                            text = stringResource(R.string.artist_split_enabled),
                            sub = stringResource(R.string.artist_split_enabled_summary),
                            state = uiState.config.enabled,
                            onChange = viewModel::setEnabled,
                            iconPainter = rememberVectorPainter(LucideIcons.Split),
                            iconColor = PreferenceIconTone.Appearance.color(),
                        )
                        Item(
                            text = stringResource(if (uiState.hasPendingIndexRebuild) R.string.rebuild_artist_index_now else R.string.rebuild_artist_index),
                            sub = when {
                                uiState.isRebuildingIndex -> stringResource(R.string.artist_index_rebuilding)
                                uiState.hasPendingIndexRebuild -> stringResource(R.string.artist_index_rebuild_pending)
                                else -> stringResource(R.string.artist_index_current)
                            },
                            iconPainter = rememberVectorPainter(LucideIcons.RefreshCw),
                            iconColor = PreferenceIconTone.Tools.color(),
                            onClick = viewModel::rebuildArtistIndex
                        )
                    }
                }
                item(key = "separators") {
                    ItemOuterTitle(stringResource(R.string.section_artist_separators))
                    ScreenCard {
                        ChipGrid(Modifier.padding(12.dp)) {
                            separatorChips.forEach { chip ->
                                com.lonx.lyrico.ui.components.ManagedChip(
                                    text = chip.text, checked = chip.checked,
                                    onClick = {
                                        val enabled = !chip.checked
                                        if (chip.isBuiltin) viewModel.setBuiltinSeparatorEnabled(chip.id, enabled)
                                        else viewModel.setCustomSeparatorEnabled(chip.id, enabled)
                                    },
                                    onDelete = { pendingDelete = PendingRuleDelete.Separator(chip.id, chip.text, chip.isBuiltin) },
                                    editDescription = stringResource(R.string.edit_custom_separator),
                                    onEdit = if (chip.isBuiltin) null else {
                                        { editingSeparator = uiState.config.customSeparators.firstOrNull { it.id == chip.id } }
                                    }
                                )
                            }
                        }
                        Item(
                            text = stringResource(R.string.add_custom_separator),
                            iconPainter = rememberVectorPainter(LucideIcons.Plus),
                            iconColor = PreferenceIconTone.Library.color(),
                            onClick = { addingSeparator = true }
                        )
                        Item(
                            text = stringResource(R.string.reset_to_default),
                            iconPainter = rememberVectorPainter(LucideIcons.RotateCcw),
                            iconColor = PreferenceIconTone.Lyrics.color(),
                            onClick = viewModel::resetSeparators
                        )
                    }
                }
                item(key = "custom_no_split") {
                    ItemOuterTitle(stringResource(R.string.section_no_split_artists))
                    ScreenCard {
                        uiState.config.customNoSplitArtists.forEach { artist ->
                            SaltRuleRow(
                                text = artist.name, checked = artist.enabled,
                                onCheckedChange = { viewModel.setCustomNoSplitArtistEnabled(artist.id, it) },
                                onDelete = { pendingDelete = PendingRuleDelete.NoSplitArtist(artist.id, artist.name) },
                                onEdit = { editingNoSplitArtist = artist },
                                editDescription = stringResource(R.string.edit_no_split_artist)
                            )
                        }
                        Item(
                            text = stringResource(R.string.add_no_split_artist),
                            iconPainter = rememberVectorPainter(LucideIcons.UserPlus),
                            iconColor = PreferenceIconTone.Library.color(),
                            onClick = { addingNoSplitArtist = true }
                        )
                    }
                }
            } else {
                item(key = "config") {
                    SmallTitle(text = stringResource(R.string.artist_split_general_section))
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        SwitchPreference(
                            startAction = { PreferenceIcon(LucideIcons.Split, PreferenceIconTone.Appearance) },
                            title = stringResource(R.string.artist_split_enabled),
                            summary = stringResource(R.string.artist_split_enabled_summary),
                            checked = uiState.config.enabled,
                            onCheckedChange = viewModel::setEnabled
                        )
                        ArrowPreference(
                            startAction = { PreferenceIcon(LucideIcons.RefreshCw, PreferenceIconTone.Tools) },
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
                            Button(
                                onClick = { addingSeparator = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Icon(LucideIcons.Plus, null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.add_custom_separator), color = MiuixTheme.colorScheme.onPrimary,
                                    style = MiuixTheme.textStyles.button)
                            }
                            Button(
                                onClick = viewModel::resetSeparators,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(LucideIcons.RotateCcw, null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.reset_to_default), color = MiuixTheme.colorScheme.onSecondaryContainer,
                                    style = MiuixTheme.textStyles.button)
                            }
                        }
                    }
                }

                item(key = "custom_no_split") {
                    SmallTitle(text = stringResource(R.string.section_no_split_artists))
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        if (uiState.config.customNoSplitArtists.isNotEmpty()) {
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
                            startAction = { PreferenceIcon(LucideIcons.UserPlus, PreferenceIconTone.Library) },
                            title = stringResource(R.string.add_no_split_artist),
                            onClick = { addingNoSplitArtist = true }
                        )
                    }
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
            imageVector = LucideIcons.Trash,
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

    if (LocalUiEngine.current == UiEngine.SALT) {
        if (show) com.lonx.lyrico.ui.components.salt.SaltBasicDialog(onDismissRequest = onDismiss) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                com.moriafly.salt.ui.dialog.DialogTitle(title)
                com.moriafly.salt.ui.RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                    SaltTextInput(
                        text = text, onChange = { text = it }, hint = hint,
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                }
                ItemTip(hint)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showDelete) {
                    com.moriafly.salt.ui.Button(
                        text = stringResource(R.string.common_delete), onClick = onDelete,
                        appearance = com.moriafly.salt.ui.ButtonAppearance.Plain,
                        intent = com.moriafly.salt.ui.ButtonIntent.Destructive
                    )
                }
                com.moriafly.salt.ui.Button(
                    text = stringResource(R.string.cancel), onClick = onDismiss,
                    appearance = com.moriafly.salt.ui.ButtonAppearance.Subtle
                )
                com.moriafly.salt.ui.Button(
                    text = stringResource(R.string.confirm), onClick = { onSave(text) },
                )
            }
        }
        return
    }

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
    if (LocalUiEngine.current == UiEngine.SALT) {
        pendingDelete?.let { rule ->
            com.moriafly.salt.ui.dialog.YesNoDialog(
                title = stringResource(R.string.artist_split_delete_rule_title),
                content = stringResource(R.string.artist_split_delete_rule_message, rule.label),
                onDismissRequest = onDismiss,
                onConfirm = { onConfirm(rule) },
                cancelText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.common_delete)
            )
        }
        return
    }

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

@Composable
private fun SaltRuleRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
    editDescription: String
) {
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ItemSwitcher(text = text, state = checked, onChange = onCheckedChange, modifier = Modifier.weight(1f))
        onEdit?.let { edit ->
            SaltIconButton(LucideIcons.Pencil, editDescription, edit)
        }
        SaltIconButton(LucideIcons.Trash, stringResource(R.string.common_delete), onDelete)
    }
}
