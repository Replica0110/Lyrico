package com.lonx.lyrico.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
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
import androidx.core.net.toUri

@OptIn(UnstableSaltUiApi::class)
@Composable
@Destination<RootGraph>(route = "about")
fun AboutScreen(
    navigator: DestinationsNavigator
) {
    val scrollState = rememberScrollState()
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
                        openBrowser(context, "https://github.com/replica0110/Lyrico")
                    },
                    arrowType = ItemArrowType.Link
                )
            }
            Spacer(modifier = Modifier.height(SaltTheme.dimens.padding))
        }
    }
}
private fun openBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}