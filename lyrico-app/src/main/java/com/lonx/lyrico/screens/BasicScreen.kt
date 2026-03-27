package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BasicScreenBox(
    title: String,
    onBack: (() -> Unit)? = null,
    toolbar: @Composable (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    if (onBack != null) {
                        BackNavigationIcon(onClick = onBack)
                    }
                },
                actions = {
                    toolbar?.invoke()
                }
            )
        },
        popupHost = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        ) {
            content()
        }
    }
}

@Composable
private fun BackNavigationIcon(onClick: () -> Unit) {
    val layoutDirection = LocalLayoutDirection.current

    IconButton(onClick = onClick) {
        Icon(
            modifier = Modifier.graphicsLayer {
                if (layoutDirection == LayoutDirection.Rtl) {
                    scaleX = -1f
                }
            },
            imageVector = MiuixIcons.Back,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onBackground
        )
    }
}
