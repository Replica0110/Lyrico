package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.moriafly.salt.ui.ItemCheck
import com.moriafly.salt.ui.ItemDropdown
import com.moriafly.salt.ui.ItemOuterTip
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.RoundedColumnType
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.rememberScrollState
import com.moriafly.salt.ui.verticalScroll
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(UnstableSaltUiApi::class)
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
            // 显示说明信息
            ItemOuterTip(
                text = stringResource(R.string.character_mapping_description)
            )

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

            Spacer(modifier = Modifier.height(SaltTheme.dimens.padding))
        }
    }
}

@OptIn(UnstableSaltUiApi::class)
@Composable
private fun CharacterMappingRuleSection(
    rule: CharacterMappingRule,
    onCharacterMappingChanged: (character: String, replacementChar: String?) -> Unit
) {
    if (rule.charMappings.isEmpty()) {
        RoundedColumn {
            Text(
                text = stringResource(R.string.no_character_mappings),
                modifier = Modifier.padding(SaltTheme.dimens.padding)
            )
        }
    } else {
        RoundedColumn(
            type = RoundedColumnType.InList,
        ) {
            rule.charMappings.entries.forEachIndexed { _, (character, currentReplacement) ->

                val currentOption = currentReplacement.toReplacementOption()

                ItemDropdown(
                    text = stringResource(R.string.character_to_replace, character),

                    // 🔥 显示
                    value = currentOption?.let { stringResource(it.labelRes) }
                        ?: stringResource(R.string.replacement_not_selected),

                    sub = stringResource(R.string.character_replacement_selector_subtitle),

                    content = {
                        ReplacementCharOption.entries.forEach { option ->

                            ItemCheck(
                                text = stringResource(option.labelRes),

                                // 🔥 判断选中
                                state = currentOption == option,

                                onChange = {
                                    // 🔥 Enum → String
                                    onCharacterMappingChanged(
                                        character,
                                        option.value
                                    )
                                    state.dismiss()
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}
