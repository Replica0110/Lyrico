package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.CharacterMappingRule
import com.lonx.lyrico.data.model.ReplacementCharOption
import com.lonx.lyrico.data.model.toReplacementOption
import com.lonx.lyrico.viewmodel.BatchRenameViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@Destination<RootGraph>(route = "character_mapping")
fun CharacterMappingScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: BatchRenameViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val characterMappingConfig = uiState.characterMappingConfig
    val scrollState = rememberScrollState()

    BasicScreenBox(
        title = stringResource(R.string.configure_character_mapping),
        onBack = { navigator.popBackStack() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Card(
                modifier = Modifier.padding(12.dp),
                insideMargin = PaddingValues(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.character_mapping_description),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

            if (characterMappingConfig != null && characterMappingConfig.rules.isNotEmpty()) {
                characterMappingConfig.rules.forEach { rule ->
                    CharacterMappingRuleSection(
                        rule = rule,
                        onCharacterMappingChanged = { character, replacementChar ->
                            viewModel.updateCharacterMappingInRule(rule.id, character, replacementChar)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CharacterMappingRuleSection(
    rule: CharacterMappingRule,
    onCharacterMappingChanged: (character: String, replacementChar: String?) -> Unit
) {
    if (rule.charMappings.isEmpty()) {
        Card(
            modifier = Modifier.padding(horizontal = 12.dp),
            insideMargin = PaddingValues(16.dp)
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

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp)
    ) {
        rule.charMappings.entries.forEach { (character, currentReplacement) ->
            val currentOption = currentReplacement.toReplacementOption()
            val selectedIndex = currentOption?.let { ReplacementCharOption.entries.indexOf(it) + 1 } ?: 0

            SuperDropdown(
                title = stringResource(R.string.character_to_replace, character),
                summary = stringResource(R.string.character_replacement_selector_subtitle),
                items = optionLabels,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { index ->
                    val replacementValue = if (index == 0) {
                        null
                    } else {
                        ReplacementCharOption.entries[index - 1].value
                    }
                    onCharacterMappingChanged(character, replacementValue)
                }
            )
        }
    }
}
