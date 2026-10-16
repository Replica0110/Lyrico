@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.DialogProperties
import com.moriafly.salt.ui.LocalContentColor
import com.moriafly.salt.ui.LocalTextStyle
import com.moriafly.salt.ui.ProvideControlSize
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.dialog.BasicAdaptiveDialog
import com.moriafly.salt.ui.dialog.DialogDefaults
import com.moriafly.salt.ui.material.DisableMaterial

/** beta02 BasicDialog has no surface-external inset slot. Keep its native tokens while
 * letting one full-height host position the surface inside the keyboard's available area. */
@Composable
internal fun SaltBasicDialog(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAdaptiveDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var surfaceBounds by remember { mutableStateOf<Rect?>(null) }
        var hostOrigin by remember { mutableStateOf(Offset.Zero) }
        val dismiss by rememberUpdatedState(onDismissRequest)
        Box(
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { hostOrigin = it.positionInRoot() }
                .pointerInput(Unit) {
                    detectTapGestures { point ->
                        if (surfaceBounds?.contains(point + hostOrigin) == false) dismiss()
                    }
                },
        ) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)),
                contentAlignment = Alignment.Center) {
                DisableMaterial {
                    ProvideControlSize(DialogDefaults.controlSize) {
                        CompositionLocalProvider(
                            LocalContentColor provides SaltTheme.colors.text,
                            LocalTextStyle provides SaltTheme.textStyles.main,
                        ) {
                            val shape = DialogDefaults.shape
                            val border = DialogDefaults.border
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .onGloballyPositioned { surfaceBounds = it.boundsInRoot() }
                                    .dropShadow(shape, DialogDefaults.ambientShadow)
                                    .dropShadow(shape, DialogDefaults.keyShadow)
                                    .clip(shape)
                                    .background(DialogDefaults.containerColor)
                                    .then(if (border != null) Modifier.border(border, shape) else Modifier)
                                    .padding(DialogDefaults.contentPadding),
                                content = content,
                            )
                        }
                    }
                }
            }
        }
    }
}
