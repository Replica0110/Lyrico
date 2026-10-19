@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.CharacterMappingRule
import com.lonx.lyrico.data.model.ReplacementCharOption
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.data.model.toReplacementOption
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.lonx.lyrico.ui.components.library.libraryDetailScroll
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.CharacterMappingViewModel
import com.moriafly.salt.ui.ItemDropdown
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.screen.ScreenCard
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@Destination<RootGraph>(route = "character_mapping")
fun CharacterMappingScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: CharacterMappingViewModel = koinViewModel()
    val characterMappingConfig by viewModel.characterMappingConfig.collectAsState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val salt = LocalUiEngine.current == UiEngine.SALT
    LibraryDetailPage(
        title = stringResource(R.string.configure_character_mapping),
        onBack = { navigator.popBackStack() },
        miuixTopBar = {
            SmallTopAppBar(
                title = stringResource(R.string.configure_character_mapping),
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.popBackStack() }) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().libraryDetailScroll(topAppBarScrollBehavior),
            contentPadding = scaffoldContentPadding(paddingValues, bottomExtra = 12.dp),
            overscrollEffect = if (salt) rememberOverscrollEffect() else null
        ) {
            val config = characterMappingConfig
            if (config != null && config.rules.isNotEmpty()) {
                items(items = config.rules, key = { it.id }) { rule ->
                    CharacterMappingRuleSection(
                        rule = rule,
                        onCharacterMappingChanged = { character, replacementChar ->
                            viewModel.updateCharacterMappingInRule(rule.id, character, replacementChar)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterMappingRuleSection(
    rule: CharacterMappingRule,
    onCharacterMappingChanged: (character: String, replacementChar: String?) -> Unit
) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    if (rule.charMappings.isEmpty()) {
        if (salt) {
            ScreenCard { ItemTip(stringResource(R.string.no_character_mappings)) }
            return
        }
        Card(
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.no_character_mappings),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        return
    }

    val optionLabels = listOf(
        stringResource(R.string.replacement_not_selected)
    ) + ReplacementCharOption.entries.map { option ->
        stringResource(option.labelRes)
    }

    val rows: @Composable () -> Unit = {
        rule.charMappings.entries.forEach { (character, currentReplacement) ->
            val currentOption = currentReplacement.toReplacementOption()
            val selectedIndex = currentOption?.let { ReplacementCharOption.entries.indexOf(it) + 1 } ?: 0
            val onSelected: (Int) -> Unit = { index ->
                val replacementValue = if (index == 0) null else ReplacementCharOption.entries[index - 1].value
                onCharacterMappingChanged(character, replacementValue)
            }
            if (salt) {
                ItemDropdown(
                    text = stringResource(R.string.character_to_replace, character),
                    sub = stringResource(R.string.character_replacement_selector_subtitle),
                    value = optionLabels[selectedIndex]
                ) {
                    optionLabels.forEachIndexed { index, label ->
                        PopupMenuItem(text = label, selected = index == selectedIndex, onClick = {
                            state.dismiss()
                            onSelected(index)
                        })
                    }
                }
            } else WindowDropdownPreference(
                title = stringResource(R.string.character_to_replace, character),
                summary = stringResource(R.string.character_replacement_selector_subtitle),
                items = optionLabels,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = onSelected
            )
        }
    }
    if (salt) ScreenCard { rows() }
    else Card(modifier = Modifier.padding(horizontal = 12.dp).padding(top = 12.dp)) { rows() }
}
