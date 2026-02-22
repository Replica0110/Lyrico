package com.lonx.lyrico.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.BuildConfig
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.rememberScrollState
import com.moriafly.salt.ui.verticalScroll
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.lonx.lyrico.App.Companion.OWNER_ID
import com.lonx.lyrico.App.Companion.REPO_NAME
import com.lonx.lyrico.App.Companion.TELEGRAM_GROUP_LINK
import com.lonx.lyrico.ui.components.ItemExt
import com.lonx.lyrico.utils.UpdateEffect
import com.lonx.lyrico.viewmodel.AboutViewModel
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemArrow
import com.moriafly.salt.ui.ItemInfo
import com.moriafly.salt.ui.ItemInfoType
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.RoundedColumnType
import com.moriafly.salt.ui.icons.ChevronRight
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.lazy.LazyColumn
import com.moriafly.salt.ui.lazy.items
import org.koin.androidx.compose.koinViewModel

@OptIn(UnstableSaltUiApi::class)
@Composable
@Destination<RootGraph>(route = "about")
fun AboutScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: AboutViewModel = koinViewModel()
    val checkUpdateEnabled by viewModel.checkUpdateEnabled.collectAsStateWithLifecycle()
    val updateEffect by viewModel.updateEffect.collectAsStateWithLifecycle(
        initialValue = UpdateEffect("立即检查应用是否有更新")
    )
    val contributors by viewModel.contributors.collectAsStateWithLifecycle()
    val loading by viewModel.loadingContributors.collectAsStateWithLifecycle()
    val error by viewModel.contributorsError.collectAsStateWithLifecycle()

    val context = LocalContext.current
    BasicScreenBox(
        title = "关于",
        onBack = { navigator.popBackStack() }
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {

            item {
                ItemOuterTitle("应用信息")
                RoundedColumn(
                    type = RoundedColumnType.InList
                ) {
                    Item(
                        text = "应用版本",
                        sub = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
                        onClick = {},
                        arrowType = ItemArrowType.None
                    )
                    Item(
                        text = "项目地址",
                        sub = "在 GitHub 上查看项目源码",
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
                        text = "自动检查新版本",
                        sub = "应用启动时检查是否有新版本"
                    )
                    Item(
                        text = "检查更新",
                        sub = updateEffect.message,
                        onClick = {
                            viewModel.checkUpdate()
                        },
                        arrowType = ItemArrowType.Arrow
                    )
                }
            }

            item {
                ItemOuterTitle("贡献者")
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
                    item {
                        RoundedColumn {
                            ItemInfo(
                                text = error!!,
                                infoType = ItemInfoType.Error,
                            )
                        }
                    }
                }

                contributors.isEmpty() -> {
                    item {
                        RoundedColumn {
                            Item(
                                text = "暂无贡献者",
                                arrowType = ItemArrowType.None,
                                onClick = {  },
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
                                sub = "贡献了 ${contributor.contributions} 个提交",
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