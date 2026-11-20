package com.lonx.lyrico.ui.components.scaffold

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.ui.components.bar.SelectionTopBarState
import com.lonx.lyrico.ui.theme.LocalUiEngine
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** 带返回导航的页面骨架，适配两套主题的顶栏、内容区域和 Snackbar 宿主。 */
@Composable
internal fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    selection: SelectionTopBarState? = null,
    actions: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    miuixTopBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val engine = LocalUiEngine.current

    when (engine) {
        UiEngine.SALT ->
            PageScaffoldSalt(title, onBack, selection, actions, snackbarHost, content)
        UiEngine.MIUIX -> PageScaffoldMiuix(miuixTopBar, snackbarHost, content)
    }
}

@Composable
internal fun Modifier.pageScroll(behavior: ScrollBehavior): Modifier =
    if (LocalUiEngine.current == UiEngine.SALT) this else pageScrollMiuix(behavior)
