package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.BuildConfig
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.lyrico.App.Companion.OWNER_ID
import com.lonx.lyrico.App.Companion.REPO_NAME
import com.lonx.lyrico.App.Companion.TELEGRAM_GROUP_LINK
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.ItemExt
import com.lonx.lyrico.utils.UpdateEffect
import com.lonx.lyrico.viewmodel.AboutViewModel
import com.lonx.lyrico.viewmodel.UiError
import com.moriafly.salt.ui.ItemArrow
import com.moriafly.salt.ui.ItemInfo
import com.moriafly.salt.ui.ItemInfoType
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.RoundedColumnType
import com.moriafly.salt.ui.lazy.LazyColumn
import com.moriafly.salt.ui.lazy.items
import org.koin.androidx.compose.koinViewModel

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(UnstableSaltUiApi::class)
@Composable
@Destination<RootGraph>(route = "about")
fun AboutScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: AboutViewModel = koinViewModel()
    val checkUpdateEnabled by viewModel.checkUpdateEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val updateEffect by viewModel.updateEffect.collectAsStateWithLifecycle(
        initialValue = UpdateEffect(R.string.about_check_update_default
        )
    )
    val contributors by viewModel.contributors.collectAsStateWithLifecycle()
    val loading by viewModel.loadingContributors.collectAsStateWithLifecycle()
    val error by viewModel.contributorsError.collectAsStateWithLifecycle()

    BasicScreenBox(
        title = stringResource(R.string.about_title),
        onBack = { navigator.popBackStack() }
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {

            item {
                ItemOuterTitle(stringResource(R.string.about_app_info))
                RoundedColumn(
                    type = RoundedColumnType.InList
                ) {
                    Item(
                        text = stringResource(R.string.about_app_version),
                        sub = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
                        onClick = {},
                        arrowType = ItemArrowType.None
                    )
                    Item(
                        text = stringResource(R.string.about_project_url),
                        sub = stringResource(R.string.about_project_url_sub),
                        onClick = {
                            viewModel.openBrowser(
                                context,
                                "https://github.com/$OWNER_ID/$REPO_NAME"
                            )
                        },
                        arrowType = ItemArrowType.Link
                    )
                    Item(
                        text = "Telegram",
                        sub = TELEGRAM_GROUP_LINK,
                        onClick = {
                            viewModel.openBrowser(
                                context,
                                TELEGRAM_GROUP_LINK
                            )
                        },
                        arrowType = ItemArrowType.Link
                    )
                    ItemSwitcher(
                        state = checkUpdateEnabled,
                        onChange = { newValue ->
                            viewModel.setCheckUpdateEnabled(newValue)
                        },
                        text = stringResource(R.string.about_auto_check_update),
                        sub = stringResource(R.string.about_auto_check_update_sub)
                    )
                    Item(
                        text = stringResource(R.string.about_check_update),
                        sub = stringResource(updateEffect.messageRes, *updateEffect.formatArgs.toTypedArray()),
                        onClick = {
                            viewModel.checkUpdate()
                        },
                        arrowType = ItemArrowType.Arrow
                    )
                }
            }

            item {
                ItemOuterTitle(stringResource(R.string.about_contributors))
            }

            when {
                loading -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = SaltTheme.colors.highlight
                            )
                        }
                    }
                }

                error != null -> {
                    val errorText = when (val e = error) {
                        null -> null
                        UiError.LoadFailed -> context.getString(R.string.load_failed)
                        is UiError.Message -> e.text
                    }
                    item {
                        RoundedColumn {
                            ItemInfo(
                                text = errorText!!,
                                infoType = ItemInfoType.Error,
                            )
                        }
                    }
                }

                contributors.isEmpty() -> {
                    item {
                        RoundedColumn {
                            Item(
                                text = stringResource(R.string.about_no_contributors),
                                arrowType = ItemArrowType.None,
                                onClick = { },
                            )
                        }
                    }
                }

                else -> {
                    items(
                        items = contributors,
                        key = { it.id }
                    ) { contributor ->

                        RoundedColumn(
                            type = RoundedColumnType.InList
                        ) {
                            ItemExt(
                                text = contributor.login,
                                iconColor = null,
                                sub = stringResource(
                                    R.string.about_contribution_count,
                                    contributor.contributions
                                ),
                                iconContent = {
                                    AsyncImage(
                                        model = contributor.avatar_url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                },
                                onClick = {
                                    viewModel.openBrowser(context, contributor.html_url)
                                },
                                iconEnd = {
                                    ItemArrow(
                                        arrowType = ItemArrowType.Link
                                    )
                                }
                            )
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(SaltTheme.dimens.padding))
            }
        }
    }
}