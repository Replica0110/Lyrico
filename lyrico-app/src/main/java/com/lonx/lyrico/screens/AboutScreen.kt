package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.lyrico.App.Companion.OWNER_ID
import com.lonx.lyrico.App.Companion.REPO_NAME
import com.lonx.lyrico.App.Companion.TELEGRAM_GROUP_LINK
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.utils.UpdateEffect
import com.lonx.lyrico.viewmodel.AboutViewModel
import com.lonx.lyrico.viewmodel.UiError
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "about")
fun AboutScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: AboutViewModel = koinViewModel()
    val checkUpdateEnabled by viewModel.checkUpdateEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val updateEffect by viewModel.updateEffect.collectAsStateWithLifecycle(
        initialValue = UpdateEffect(R.string.about_check_update_default)
    )
    val contributors by viewModel.contributors.collectAsStateWithLifecycle()
    val loading by viewModel.loadingContributors.collectAsStateWithLifecycle()
    val error by viewModel.contributorsError.collectAsStateWithLifecycle()

    val errorText = when (val e = error) {
        null -> null
        UiError.LoadFailed -> stringResource(R.string.load_failed)
        is UiError.Message -> e.text
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.about_title),
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 12.dp),
                        onClick = { navigator.popBackStack() }
                    ) {
                        Icon(
                            MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
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
                bottom = paddingValues.calculateBottomPadding() + 12.dp,
            ),
            overscrollEffect = null,
        ) {
            item {
                SmallTitle(text = stringResource(R.string.about_app_info))
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    BasicComponent(
                        title = stringResource(R.string.about_app_version),
                        summary = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
                    )
                    SuperArrow(
                        title = stringResource(R.string.about_project_url),
                        summary = stringResource(R.string.about_project_url_sub),
                        endActions = {
                            Text(
                                text = "GitHub",
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        onClick = {
                            viewModel.openBrowser(
                                context,
                                "https://github.com/$OWNER_ID/$REPO_NAME"
                            )
                        }
                    )
                    SuperArrow(
                        title = "Telegram",
                        summary = TELEGRAM_GROUP_LINK,
                        endActions = {
                            Text(
                                text = "Telegram",
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        onClick = {
                            viewModel.openBrowser(context, TELEGRAM_GROUP_LINK)
                        }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.about_auto_check_update),
                        summary = stringResource(R.string.about_auto_check_update_sub),
                        checked = checkUpdateEnabled,
                        onCheckedChange = viewModel::setCheckUpdateEnabled
                    )
                    SuperArrow(
                        title = stringResource(R.string.about_check_update),
                        summary = stringResource(
                            updateEffect.messageRes,
                            *updateEffect.formatArgs.toTypedArray()
                        ),
                        onClick = viewModel::checkUpdate
                    )
                }
            }

            item {
                SmallTitle(text = stringResource(R.string.about_contributors))
            }

            when {
                loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(size = 32.dp)
                        }
                    }
                }

                errorText != null -> {
                    item {
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            insideMargin = PaddingValues(16.dp),
                            colors = CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.errorContainer,
                                contentColor = MiuixTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text(
                                text = errorText,
                                color = MiuixTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                contributors.isEmpty() -> {
                    item {
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            BasicComponent(
                                title = stringResource(R.string.about_no_contributors)
                            )
                        }
                    }
                }

                else -> {
                    itemsIndexed(
                        items = contributors,
                        key = { _, contributor -> contributor.id }
                    ) { index, contributor ->
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(top = if (index == 0) 0.dp else 12.dp)
                        ) {
                            SuperArrow(
                                title = contributor.login,
                                summary = stringResource(
                                    R.string.about_contribution_count,
                                    contributor.contributions
                                ),
                                startAction = {
                                    AsyncImage(
                                        model = contributor.avatar_url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(end = 16.dp)
                                            .size(36.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                },
                                endActions = {
                                    Text(
                                        text = "GitHub",
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = {
                                    viewModel.openBrowser(context, contributor.html_url)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
