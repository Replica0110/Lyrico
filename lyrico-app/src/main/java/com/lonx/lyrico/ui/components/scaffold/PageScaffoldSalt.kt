@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.scaffold

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.bar.SelectionTopBarState
import com.lonx.lyrico.ui.components.bar.SelectionTopBarTransition
import com.lonx.lyrico.ui.components.bar.salt.SaltSelectionActions
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.gestures.cupertino.CupertinoOverscrollEffectFactory
import com.moriafly.salt.ui.screen.BasicScreen

@Composable
internal fun PageScaffoldSalt(
    title: String,
    onBack: () -> Unit,
    selection: SelectionTopBarState?,
    actions: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {

    CompositionLocalProvider(
        LocalIndication provides SaltTheme.configs.indication,
        LocalOverscrollFactory provides CupertinoOverscrollEffectFactory(),
    ) {
        BoxWithConstraints {
            // IME changes the viewport, rather than adding scrollable space behind the keyboard.
            // Exclude it here so BasicScreen's bring-into-view spec does not subtract it again.
            val padding = scaffoldPageWindowInsets().exclude(WindowInsets.ime).asPaddingValues()
            val direction = LocalLayoutDirection.current
            val width =
                maxWidth -
                    padding.calculateStartPadding(direction) -
                    padding.calculateEndPadding(direction)
            val compact = width < 360.dp
            BasicScreen(
                modifier = Modifier.background(SaltTheme.colors.background).imePadding(),
                contentPadding = padding,
                actionButton = {
                    SelectionTopBarTransition(selection, Alignment.CenterStart) { selected ->
                        // Include the native title bar's side and slot spacing as well as the 40dp
                        // action.
                        val reserved =
                            if (selected == null) 72.dp else if (compact) 128.dp else 188.dp
                        Row(
                            Modifier.widthIn(max = (width - reserved).coerceAtLeast(40.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selected == null) {
                                SaltIconButton(
                                    LucideIcons.ArrowLeft,
                                    stringResource(R.string.action_back),
                                    onBack,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            com.moriafly.salt.ui.Text(
                                selected?.title ?: title,
                                modifier = Modifier.weight(1f, fill = false),
                                style = SaltTheme.textStyles.main,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                toolButtons = {
                    SelectionTopBarTransition(selection, Alignment.CenterEnd) { selected ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selected == null) actions()
                            else {
                                SaltSelectionActions(selected, compact)
                            }
                        }
                    }
                },
            ) {
                // Salt 没有 Snackbar，宿主由调用方提供，这里只负责底部定位。
                Box(Modifier.fillMaxSize()) {
                    content(it)
                    Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                        snackbarHost()
                    }
                }
            }
        }
    }
}
