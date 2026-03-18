package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.RenamePreview
import com.ramcosta.composedestinations.generated.destinations.CharacterMappingDestination
import com.lonx.lyrico.ui.theme.LyricoColors.modifiedBorder
import com.lonx.lyrico.utils.RenameEngine
import com.lonx.lyrico.utils.TagField
import com.lonx.lyrico.viewmodel.BatchRenameViewModel
import com.lonx.lyrico.viewmodel.SongForBatchRename
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemButton
import com.moriafly.salt.ui.ItemCheck
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.ItemDropdown
import com.moriafly.salt.ui.ItemInfo
import com.moriafly.salt.ui.ItemInfoType
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.YesNoDialog
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import java.io.File

@OptIn(UnstableSaltUiApi::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
@Destination<RootGraph>(route = "batch_rename")
fun BatchRenameScreen(
    navigator: DestinationsNavigator,
    filePaths: Array<String> = arrayOf(),
    fileLastModifieds: LongArray = longArrayOf()
) {
    val viewModel: BatchRenameViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(filePaths, fileLastModifieds) {
        if (filePaths.isNotEmpty()) {
            val songList = filePaths.mapIndexed { index, path ->
                val lastModified = fileLastModifieds.getOrNull(index) ?: File(path).lastModified()
                SongForBatchRename(path, path.substringAfterLast('/'), null, lastModified)
            }
            viewModel.setSongs(context, songList)
        }
    }

    var showPlaceholderInfo by remember { mutableStateOf(false) }

    BasicScreenBox(
        title = stringResource(id = R.string.batch_rename_title),
        onBack = { if (!uiState.isRenamingInProgress) navigator.popBackStack() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                ItemOuterTitle(text = stringResource(id = R.string.rename_format))
                RoundedColumn {
                    ItemEdit(
                        value = uiState.format,
                        onValueChange = { viewModel.setFormat(context, it) },
                        placeholder = stringResource(id = R.string.format_placeholder),
                        hintText = stringResource(id = R.string.format_hint)
                    )
                    ItemDropdown(
                        text = stringResource(id = R.string.format_preset),
                        value = if (uiState.presetFormats.contains(uiState.format)) uiState.format else "",
                        content = {
                            uiState.presetFormats.forEach { format ->
                                ItemCheck(
                                    text = format,
                                    state = uiState.format == format,
                                    onChange = {
                                        viewModel.setFormat(
                                            context,
                                            format
                                        ); state.dismiss()
                                    }
                                )
                            }
                        }
                    )
                    ItemSwitcher(
                        text = stringResource(id = R.string.format_preset_show_placeholders),
                        state = showPlaceholderInfo,
                        onChange = { showPlaceholderInfo = it }
                    )
                    Item(
                        onClick = { navigator.navigate(CharacterMappingDestination()) },
                        text = stringResource(id = R.string.configure_character_mapping)
                    )
                    AnimatedVisibility(visible = showPlaceholderInfo) {
                        PlaceholderInfoContent()
                    }
                    ItemButton(
                        onClick = { viewModel.executeRename() },
                        enabled = uiState.previews.isNotEmpty() && !uiState.isRenamingInProgress,
                        text = stringResource(id = R.string.action_rename),
                    )
                }
            }

            item {
                ItemOuterTitle(
                    text = stringResource(
                        if (uiState.isGeneratingPreview)
                            R.string.preview_title_generating
                        else
                            R.string.preview_title,
                        uiState.previews.size
                    )
                )
            }

            if (uiState.previews.isEmpty()) {
                item {
                    RoundedColumn { ItemTip(text =stringResource(id = R.string.preview_empty_tip)) }
                }
            } else {
                item {
                    RoundedColumn {
                        uiState.previews.forEachIndexed { index, preview ->
                            PreviewItem(preview = preview)
                            if (index < uiState.previews.size - 1) {
                                ItemDivider()
                            }
                        }
                    }
                }
            }

            uiState.errorMessage?.let {
                item {
                    RoundedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                        it.asString(context)?.let { text -> ItemInfo(text = text, infoType = ItemInfoType.Error) }
                    }
                }
            }
        }

        if (uiState.renameResult != null) {
            RenameResultDialog(
                result = uiState.renameResult!!,
                onDismiss = { viewModel.clearResult(); navigator.popBackStack() }
            )
        }
    }
}

@Composable
private fun PreviewItem(preview: RenamePreview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(
                R.string.label_old_name,
                preview.originalPath.substringAfterLast('/')
            ),
            fontSize = 13.sp,
            color = SaltTheme.colors.subText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = stringResource(
                R.string.label_new_name,
                preview.newPath.substringAfterLast('/')
            ),
            fontSize = 14.sp,
            color = if (preview.conflict) modifiedBorder else SaltTheme.colors.highlight,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (preview.conflict) {
            Text(
                text = stringResource(R.string.rename_conflict_warning),
                fontSize = 11.sp,
                color = modifiedBorder,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun PlaceholderInfoContent() {
    val placeholders = TagField.entries.map {
        "@${it.index}" to it.description
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        placeholders.forEach { (placeholder, description) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = placeholder,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaltTheme.colors.highlight
                )
                Text(
                    text = stringResource(description),
                    fontSize = 13.sp,
                    color = SaltTheme.colors.subText
                )
            }
        }
    }
}

@Composable
private fun RenameResultDialog(
    result: RenameEngine.Result,
    onDismiss: () -> Unit
) {
    YesNoDialog(
        onDismissRequest = onDismiss,
        onConfirm = onDismiss,
        title = stringResource(
            if (result.isSuccessful)
                R.string.rename_success_title
            else
                R.string.rename_finished_title
        ),
        confirmText = stringResource(R.string.confirm),
        content = buildString {
            append(
                stringResource(
                    R.string.rename_result_summary,
                    result.successCount,
                    result.totalCount
                )
            )
            append("\n")

            if (result.failureCount > 0) {
                append(
                    stringResource(
                        R.string.rename_result_failure,
                        result.failureCount
                    )
                )
                append("\n")

                result.failed.take(3).forEach { (_, error) ->
                    append("• $error\n")
                }

                if (result.failed.size > 3) {
                    append("...")
                }
            }
        }
    )
}

@Composable
private fun ItemEdit(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    hintText: String,
    readOnly: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SaltTheme.dimens.padding)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    color = SaltTheme.colors.subText,
                    fontSize = 14.sp
                )
            },
            readOnly = readOnly,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = SaltTheme.colors.subText.copy(alpha = 0.2f),
                focusedBorderColor = SaltTheme.colors.highlight,
                cursorColor = SaltTheme.colors.highlight
            ),
            textStyle = TextStyle(fontSize = 14.sp, color = SaltTheme.colors.text)
        )
        Text(
            text = hintText,
            fontSize = 11.sp,
            color = SaltTheme.colors.subText,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp)
        )
    }
}
