@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.plugin

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.data.plugin.PluginSourceType
import com.lonx.lyrico.data.plugin.isSatisfied
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.ui.components.blur.rememberBarBlurEnabled
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.scaffold.PageScaffold
import com.lonx.lyrico.ui.components.scaffold.scaffoldContentPadding
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.ItemOuterTip
import com.moriafly.salt.ui.ItemValue
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.screen.ScreenCard
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "plugin_config")
fun PluginConfigScreen(
    pluginId: String,
    navigator: DestinationsNavigator
) {
    val viewModel: SearchSourceConfigViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val salt = LocalUiEngine.current == UiEngine.SALT
    val barBackdrop = rememberBarBlurBackdrop(enabled = !salt && rememberBarBlurEnabled())

    val requiredMessage = stringResource(R.string.source_config_required_error)

    LaunchedEffect(pluginId, configuration) {
        com.lonx.lyrico.plugin.i18n.PluginLocales.update(configuration)
        viewModel.load(pluginId)
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            Toast.makeText(context, R.string.source_config_saved, Toast.LENGTH_SHORT).show()
            viewModel.consumeSaved()
        }
    }

    val title = uiState.title.ifBlank { stringResource(R.string.plugin_config_title) }

    val hasConfigContent by remember {
        derivedStateOf {
            uiState.errorMessage == null &&
                    !uiState.isLoading &&
                    uiState.configFields.any { it.dependency.isSatisfied(uiState.values) }
        }
    }

    PageScaffold(
        title = title,
        onBack = { navigator.navigateUp() },
        actions = {
            SaltIconButton(LucideIcons.Check, stringResource(R.string.source_config_save),
                onClick = { viewModel.save(requiredMessage) })
        },
        miuixTopBar = {
            BlurredTopBar(barBackdrop) {
                SmallTopAppBar(
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = { navigator.navigateUp() }) {
                            Icon(
                                imageVector = LucideIcons.ArrowLeft,
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
                                imageVector = LucideIcons.Check,
                                contentDescription = stringResource(R.string.source_config_save)
                            )
                        }
                    },
                    scrollBehavior = topAppBarScrollBehavior
                )
            }
        }
    ) { paddingValues ->
        // Salt 的 verticalScroll 默认没有过度滚动，短内容时拖不动；挂上 Cupertino 效果对齐列表页。
        val overscrollEffect = rememberOverscrollEffect()
        Column(
            modifier = (if (salt) Modifier else Modifier
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection))
                .then(if (salt && overscrollEffect != null) Modifier.overscroll(overscrollEffect) else Modifier)
                .fillMaxSize()
                .blurSource(barBackdrop)
                .then(if (salt) Modifier else Modifier.imePadding())
                .verticalScroll(rememberScrollState())
                .padding(com.lonx.lyrico.ui.components.scaffold.scaffoldContentPadding(paddingValues, bottomExtra = 12.dp))
        ) {
            if (uiState.errorMessage != null) {
                if (salt) com.moriafly.salt.ui.Text(
                    text = stringResource(R.string.source_config_invalid_source),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center,
                    style = SaltTheme.textStyles.main,
                    color = SaltTheme.colors.subText
                )
                else Text(
                    text = stringResource(R.string.source_config_invalid_source),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                return@PageScaffold
            }

            val typeText = stringResource(
                R.string.plugin_type_with_value,
                uiState.sourceTypes
                    .map { stringResource(it.labelRes()) }
                    .joinToString(" / ")
            )
            val apiText = stringResource(
                R.string.plugin_api_versions_with_value,
                uiState.apiVersion,
                uiState.minHostApiVersion
            )

            if (salt) {
                ScreenCard {
                    ItemValue(text = typeText, sub = apiText)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        ItemValue(text = stringResource(R.string.plugin_update_url), sub = uiState.updateUrl ?: "—")
                        uiState.installSourceUrl?.let {
                            ItemValue(text = stringResource(R.string.plugin_install_source), sub = it)
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (uiState.sourceTypes.isNotEmpty()) {
                            Text(
                                text = typeText,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Text(
                            text = apiText,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stringResource(R.string.plugin_update_url), fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions)
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(text = uiState.updateUrl ?: "—", fontSize = 13.sp)
                        }
                        uiState.installSourceUrl?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = stringResource(R.string.plugin_install_source), fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions)
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(text = it, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            if (!hasConfigContent) {
                IllustratedEmptyState(
                    kind = EmptyStateKind.Plugins,
                    title = stringResource(R.string.source_config_empty),
                    modifier = Modifier.fillMaxWidth(),
                )
                return@PageScaffold
            }

            if (!uiState.pluginEnabled) {
                val hint = stringResource(R.string.source_config_plugin_disabled_hint)
                if (salt) ItemOuterTip(text = hint)
                else Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = hint,
                        modifier = Modifier.padding(16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        fontSize = 14.sp
                    )
                }
            }

            PluginConfigFormItems(
                fields = uiState.configFields,
                values = uiState.values,
                validationErrors = uiState.validationErrors,
                onValueChange = viewModel::updateValue
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun PluginSourceType.labelRes(): Int = when (this) {
    PluginSourceType.METADATA -> R.string.plugin_type_metadata
    PluginSourceType.LYRICS -> R.string.plugin_type_lyrics
    PluginSourceType.COVER -> R.string.plugin_type_cover
}
