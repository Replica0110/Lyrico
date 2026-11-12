@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens.settings.library

import com.lonx.lyrico.ui.components.ChipGrid
import com.lonx.lyrico.ui.components.ManagedChip
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.salt.SaltTextInput

import android.widget.Toast
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.screen.ScreenCard
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.data.model.artist.visibleBuiltinSeparators
import com.lonx.lyrico.data.model.artist.CustomArtistSeparator
import com.lonx.lyrico.data.model.artist.CustomNoSplitArtist
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.viewmodel.ArtistSplitSettingsViewModel
import com.lonx.lyrico.viewmodel.ArtistSplitValidationError
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState

@Composable
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
    val separatorChips = uiState.config.visibleBuiltinSeparators()
        .map { separator ->
            ArtistSplitChipItem(
                id = separator.id,
                text = separator.displayName,
                isBuiltin = true
            )
        } + uiState.config.customSeparators.map { separator ->
            ArtistSplitChipItem(
                id = separator.id,
                text = separator.value,
                isBuiltin = false
            )
        }

    val separatorRules: @Composable () -> Unit = {
        ChipGrid(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            separatorChips.forEach { chip ->
                ManagedChip(
                    text = chip.text,
                    onDelete = { pendingDelete = PendingRuleDelete.Separator(chip.id, chip.text, chip.isBuiltin) },
                    onClick = if (chip.isBuiltin) null else {
                        { editingSeparator = uiState.config.customSeparators.firstOrNull { it.id == chip.id } }
                    }
                )
            }
        }
    }
    val noSplitArtistRules: @Composable () -> Unit = {
        if (uiState.config.customNoSplitArtists.isNotEmpty()) {
            ChipGrid(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                uiState.config.customNoSplitArtists.forEach { artist ->
                    ManagedChip(
                        text = artist.name,
                        onDelete = { pendingDelete = PendingRuleDelete.NoSplitArtist(artist.id, artist.name) },
                        onClick = { editingNoSplitArtist = artist }
                    )
                }
            }
        }
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
                        separatorRules()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            com.moriafly.salt.ui.Button(
                                text = stringResource(R.string.add_custom_separator),
                                modifier = Modifier.weight(1f),
                                onClick = { addingSeparator = true }
                            )
                            com.moriafly.salt.ui.Button(
                                text = stringResource(R.string.reset_to_default),
                                modifier = Modifier.weight(1f),
                                appearance = com.moriafly.salt.ui.ButtonAppearance.Subtle,
                                onClick = viewModel::resetSeparators
                            )
                        }
                    }
                }
                item(key = "custom_no_split") {
                    ItemOuterTitle(stringResource(R.string.section_no_split_artists))
                    ScreenCard {
                        noSplitArtistRules()
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
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
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
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        separatorRules()
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
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        noSplitArtistRules()
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

    YesNoDialog(
        show = pendingDelete != null,
        title = stringResource(R.string.artist_split_delete_rule_title),
        summary = stringResource(R.string.artist_split_delete_rule_message, pendingDelete?.label.orEmpty()),
        confirmText = stringResource(R.string.common_delete),
        onDismissRequest = { pendingDelete = null },
        onConfirm = {
            when (val delete = pendingDelete) {
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
                null -> Unit
            }
            pendingDelete = null
        }
    )
}

private data class ArtistSplitChipItem(
    val id: String,
    val text: String,
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
    val inputState = remember(show, initialValue) { TextFieldState(initialValue) }
    val text = inputState.text.toString()

    if (LocalUiEngine.current == UiEngine.SALT) {
        if (show) com.lonx.lyrico.ui.components.salt.SaltConfirmDialog(
            title = title,
            onDismissRequest = onDismiss,
            onConfirm = { onSave(text) },
            cancelText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.confirm),
            dismissOnConfirm = false,
            leadingAction = if (showDelete) {
                {
                    com.lonx.lyrico.ui.components.salt.SaltDialogButton(
                        text = stringResource(R.string.common_delete),
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        appearance = com.moriafly.salt.ui.ButtonAppearance.Plain,
                        intent = com.moriafly.salt.ui.ButtonIntent.Destructive
                    )
                }
            } else null,
        ) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            com.moriafly.salt.ui.RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                SaltTextInput(
                    state = inputState, hint = hint,
                    modifier = Modifier.focusRequester(focusRequester),
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
                state = inputState,
                label = hint,
                useLabelAsPlaceholder = true,
                lineLimits = TextFieldLineLimits.SingleLine,
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
