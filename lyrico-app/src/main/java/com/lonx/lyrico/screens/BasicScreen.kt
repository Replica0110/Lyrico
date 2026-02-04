package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.TitleBar
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.ext.safeMainCompat

@OptIn(UnstableSaltUiApi::class)
@Composable
fun BasicScreenBox(
    title: String,
    onBack: (() -> Unit)? = null,
    toolbar: @Composable (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeMainCompat
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            TitleBar(
                onBack = {
                    onBack?.invoke()
                },
                text = title,
                showBackBtn = onBack != null
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
            ) {
                toolbar?.invoke()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            content()
        }
    }
}