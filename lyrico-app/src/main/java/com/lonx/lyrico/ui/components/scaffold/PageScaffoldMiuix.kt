package com.lonx.lyrico.ui.components.scaffold

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
internal fun PageScaffoldMiuix(
    topBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    top.yukonga.miuix.kmp.basic.Scaffold(
        topBar = topBar,
        snackbarHost = snackbarHost,
        content = content,
    )
}

@Composable
internal fun Modifier.pageScrollMiuix(behavior: ScrollBehavior): Modifier =
    scrollEndHaptic().overScrollVertical().nestedScroll(behavior.nestedScrollConnection)
