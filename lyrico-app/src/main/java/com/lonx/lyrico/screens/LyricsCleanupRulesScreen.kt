@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens

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
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.ChipGrid
import com.lonx.lyrico.ui.components.ManagedChip
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.ItemButton
import com.moriafly.salt.ui.ItemEdit
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text as SaltText
import com.moriafly.salt.ui.dialog.BasicDialog
import com.moriafly.salt.ui.dialog.DialogTitle
import com.moriafly.salt.ui.dialog.YesNoDialog as SaltYesNoDialog
import com.moriafly.salt.ui.screen.ScreenCard
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
@Destination<RootGraph>(route = "lyrics_cleanup_rules")
fun LyricsCleanupRulesScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var addingRule by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<String?>(null) }
    var deletingRule by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val invalidOrDuplicateRule = stringResource(R.string.invalid_or_duplicate_rule)
    val salt = LocalUiEngine.current == UiEngine.SALT

    val topBarBackdrop = rememberBarBlurBackdrop()
    LibraryDetailPage(
        title = stringResource(R.string.non_lyrics_cleanup_rules_title),
        onBack = { navigator.popBackStack() },
        miuixTopBar = {
            BlurredTopBar(backdrop = topBarBackdrop) {
                SmallTopAppBar(
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    title = stringResource(R.string.non_lyrics_cleanup_rules_title),
                    navigationIcon = {
                        IconButton(onClick = { navigator.popBackStack() }) {
                            Icon(
                                LucideIcons.ArrowLeft,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    scrollBehavior = topAppBarScrollBehavior
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = (if (salt) Modifier else Modifier
                .blurSource(topBarBackdrop)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection))
                .fillMaxHeight(),
            contentPadding = scaffoldContentPadding(
                paddingValues = paddingValues,
                bottomExtra = 12.dp
            ),
            overscrollEffect = if (salt) rememberOverscrollEffect() else null
        ) {
            item(key = "rules") {
                if (salt) {
                    ItemOuterTitle(text = stringResource(R.string.non_lyrics_cleanup_rules_section))
                    ScreenCard {
                        ChipGrid(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            uiState.lyricsTagLineKeywords.forEach { rule ->
                                ManagedChip(
                                    text = rule,
                                    onClick = { editingRule = rule },
                                    onDelete = { deletingRule = rule }
                                )
                            }
                        }
                        SaltText(
                            text = stringResource(R.string.non_lyrics_cleanup_rules_hint),
                            color = SaltTheme.colors.subText,
                            style = SaltTheme.textStyles.sub,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        ItemButton(
                            onClick = {
                                errorMessage = null
                                addingRule = true
                            },
                            text = stringResource(R.string.add_non_lyrics_cleanup_rule),
                            iconPainter = rememberVectorPainter(LucideIcons.Plus)
                        )
                        ItemButton(
                            onClick = viewModel::resetNonLyricsContentRules,
                            text = stringResource(R.string.reset_to_default),
                            iconPainter = rememberVectorPainter(LucideIcons.RotateCcw),
                            iconColor = SaltTheme.colors.subText
                        )
                    }
                } else {
                    SmallTitle(text = stringResource(R.string.non_lyrics_cleanup_rules_section))
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        ChipGrid(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            uiState.lyricsTagLineKeywords.forEach { rule ->
                                ManagedChip(
                                    text = rule,
                                    onClick = { editingRule = rule },
                                    onDelete = { deletingRule = rule }
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.non_lyrics_cleanup_rules_hint),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MiuixButton(
                                onClick = {
                                    errorMessage = null
                                    addingRule = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = MiuixButtonDefaults.buttonColorsPrimary()
                            ) {
                                Icon(LucideIcons.Plus, null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.add_non_lyrics_cleanup_rule), color = MiuixTheme.colorScheme.onPrimary,
                                    style = MiuixTheme.textStyles.button)
                            }
                            MiuixButton(
                                onClick = viewModel::resetNonLyricsContentRules,
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
            }
        }
    }

    CleanupRuleInputDialog(
        show = addingRule,
        title = stringResource(R.string.add_non_lyrics_cleanup_rule),
        initialValue = "",
        errorMessage = errorMessage,
        onDismiss = {
            addingRule = false
            errorMessage = null
        },
        onSave = { value ->
            if (viewModel.addNonLyricsContentRule(value)) {
                addingRule = false
                errorMessage = null
            } else {
                errorMessage = invalidOrDuplicateRule
            }
        }
    )

    CleanupRuleInputDialog(
        show = editingRule != null,
        title = stringResource(R.string.edit_non_lyrics_cleanup_rule),
        initialValue = editingRule.orEmpty(),
        errorMessage = errorMessage,
        onDismiss = {
            editingRule = null
            errorMessage = null
        },
        onSave = { value ->
            val oldRule = editingRule ?: return@CleanupRuleInputDialog
            if (viewModel.updateNonLyricsContentRule(oldRule, value)) {
                editingRule = null
                errorMessage = null
            } else {
                errorMessage = invalidOrDuplicateRule
            }
        }
    )

    if (salt) {
        if (deletingRule != null) SaltYesNoDialog(
            title = stringResource(R.string.non_lyrics_cleanup_delete_rule_title),
            content = stringResource(
                R.string.non_lyrics_cleanup_delete_rule_message,
                deletingRule.orEmpty()
            ),
            onDismissRequest = { deletingRule = null },
            onConfirm = {
                deletingRule?.let(viewModel::removeNonLyricsContentRule)
                deletingRule = null
            },
            cancelText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.common_delete),
        )
    } else WindowDialog(
        show = deletingRule != null,
        title = stringResource(R.string.non_lyrics_cleanup_delete_rule_title),
        onDismissRequest = { deletingRule = null }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(
                    R.string.non_lyrics_cleanup_delete_rule_message,
                    deletingRule.orEmpty()
                ),
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { deletingRule = null },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.common_delete),
                    onClick = {
                        deletingRule?.let(viewModel::removeNonLyricsContentRule)
                        deletingRule = null
                    },
                    modifier = Modifier.weight(1f),
                    colors = MiuixButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
private fun CleanupRuleInputDialog(
    show: Boolean,
    title: String,
    initialValue: String,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(show, initialValue) { mutableStateOf(initialValue) }
    val salt = LocalUiEngine.current == UiEngine.SALT

    if (salt) {
        com.lonx.lyrico.ui.components.base.SaltConfirmDialog(
            show = show, title = title, onDismissRequest = onDismiss, onConfirm = { onSave(text) },
            cancelText = stringResource(R.string.cancel), confirmText = stringResource(R.string.confirm),
            dismissOnConfirm = false,
        ) {
            RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                ItemEdit(text = text, onChange = { text = it })
            }
            Spacer(modifier = Modifier.height(6.dp))
            SaltText(
                text = errorMessage ?: stringResource(R.string.non_lyrics_cleanup_rule_input_hint),
                color = if (errorMessage == null) SaltTheme.colors.subText else SaltTheme.colors.error,
                style = SaltTheme.textStyles.sub
            )
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
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage ?: stringResource(R.string.non_lyrics_cleanup_rule_input_hint),
                color = if (errorMessage == null) {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                } else {
                    MiuixTheme.colorScheme.error
                },
                fontSize = MiuixTheme.textStyles.footnote1.fontSize
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = { onSave(text) },
                    modifier = Modifier.weight(1f),
                    colors = MiuixButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}
