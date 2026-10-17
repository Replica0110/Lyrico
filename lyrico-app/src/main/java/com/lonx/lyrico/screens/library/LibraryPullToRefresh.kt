package com.lonx.lyrico.screens.library

import com.lonx.lyrico.ui.theme.LocalUiEngine
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lonx.lyrico.data.model.UiEngine
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** Pull-to-refresh behavior shared by all three library presentations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryPullToRefresh(
    isRefreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier,
    contentPadding: PaddingValues, topAppBarScrollBehavior: ScrollBehavior,
    refreshTexts: List<String>, content: @Composable () -> Unit,
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        val state = rememberPullToRefreshState()
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, state = state,
            modifier = modifier,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = state, isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding()),
                    containerColor = SaltTheme.colors.popup, color = SaltTheme.colors.highlight,
                )
            },
        ) { content() }
    } else top.yukonga.miuix.kmp.basic.PullToRefresh(
        isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier,
        contentPadding = contentPadding, topAppBarScrollBehavior = topAppBarScrollBehavior,
        refreshTexts = refreshTexts,
    ) { content() }
}

@Composable
internal fun Modifier.libraryScrollFeedback(behavior: ScrollBehavior): Modifier =
    if (LocalUiEngine.current == UiEngine.MIUIX) {
        scrollEndHaptic().overScrollVertical().nestedScroll(behavior.nestedScrollConnection)
    } else this

@Composable
internal fun rememberLibraryOverscrollEffect(): OverscrollEffect? =
    if (LocalUiEngine.current == UiEngine.SALT) rememberOverscrollEffect() else null
