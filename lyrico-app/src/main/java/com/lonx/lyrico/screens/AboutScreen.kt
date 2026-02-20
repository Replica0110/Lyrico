package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lonx.lyrico.BuildConfig
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.rememberScrollState
import com.moriafly.salt.ui.verticalScroll
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.utils.UpdateEffect
import com.lonx.lyrico.viewmodel.AboutViewModel
import com.moriafly.salt.ui.ItemSwitcher
import org.koin.androidx.compose.koinViewModel

@OptIn(UnstableSaltUiApi::class)
@Composable
@Destination<RootGraph>(route = "about")
fun AboutScreen(
    navigator: DestinationsNavigator
) {
    val scrollState = rememberScrollState()
    val viewModel: AboutViewModel = koinViewModel()
    val checkUpdateEnabled by viewModel.checkUpdateEnabled.collectAsStateWithLifecycle()
    val updateEffect by viewModel.updateEffect.collectAsStateWithLifecycle(
        initialValue = UpdateEffect("立即检查应用是否有更新")
    )

    val context = LocalContext.current
    BasicScreenBox(
        title = "关于",
        onBack = { navigator.popBackStack() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            RoundedColumn {
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
                        viewModel.openBrowser(context, "https://github.com/replica0110/Lyrico")
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
            Spacer(modifier = Modifier.height(SaltTheme.dimens.padding))
        }
    }
}