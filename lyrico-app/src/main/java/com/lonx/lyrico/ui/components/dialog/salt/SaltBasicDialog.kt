@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.dialog.salt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.moriafly.salt.ui.BasicButton
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.ButtonDefaults
import com.moriafly.salt.ui.ButtonIntent
import com.moriafly.salt.ui.LocalContentColor
import com.moriafly.salt.ui.LocalTextStyle
import com.moriafly.salt.ui.ProvideControlSize
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.dialog.BasicAdaptiveDialog
import com.moriafly.salt.ui.dialog.DialogDefaults
import com.moriafly.salt.ui.material.DisableMaterial

/**
 * beta02 BasicDialog has no surface-external inset slot. Keep its native tokens while letting one
 * full-height host position the surface inside the keyboard's available area.
 */
@Composable
internal fun SaltBasicDialog(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAdaptiveDialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var surfaceBounds by remember { mutableStateOf<Rect?>(null) }
        var hostOrigin by remember { mutableStateOf(Offset.Zero) }
        val dismiss by rememberUpdatedState(onDismissRequest)
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .onGloballyPositioned { hostOrigin = it.positionInRoot() }
                    .pointerInput(Unit) {
                        detectTapGestures { point ->
                            if (surfaceBounds?.contains(point + hostOrigin) == false) dismiss()
                        }
                    }
        ) {
            Box(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)),
                contentAlignment = Alignment.Center,
            ) {
                DisableMaterial {
                    ProvideControlSize(DialogDefaults.controlSize) {
                        CompositionLocalProvider(
                            LocalContentColor provides SaltTheme.colors.text,
                            LocalTextStyle provides SaltTheme.textStyles.main.copy(fontWeight = FontWeight.Normal),
                        ) {
                            val shape = RoundedCornerShape(24.dp)
                            val border = DialogDefaults.border
                            Column(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .onGloballyPositioned { surfaceBounds = it.boundsInRoot() }
                                        .dropShadow(shape, DialogDefaults.ambientShadow)
                                        .dropShadow(shape, DialogDefaults.keyShadow)
                                        .clip(shape)
                                        .background(DialogDefaults.containerColor)
                                        .then(
                                            if (border != null) Modifier.border(border, shape)
                                            else Modifier
                                        )
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

/** 共享对话框标题，内容边距由外层容器提供。 */
@Composable
internal fun SaltDialogTitle(text: String) {
    Text(
        text = text,
        style = SaltTheme.textStyles.main.copy(
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = SaltTheme.colors.text,
        modifier = Modifier.padding(bottom = 12.dp).semantics { heading() },
    )
}

/** Keep Salt sizing and colors while reducing pill-shaped dialog actions. */
@Composable
internal fun SaltDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    appearance: ButtonAppearance = ButtonAppearance.Filled,
    intent: ButtonIntent = ButtonIntent.Normal,
) {
    BasicButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.colors(appearance = appearance, intent = intent),
    ) {
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
