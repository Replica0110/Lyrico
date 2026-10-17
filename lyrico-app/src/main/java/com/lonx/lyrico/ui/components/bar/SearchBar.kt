package com.lonx.lyrico.ui.components.bar

import com.lonx.lyrico.ui.components.icons.LucideIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun InputField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    onSearch: ((String) -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    autoFocus: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
            com.lonx.lyrico.data.model.UiEngine.SALT

    val textColor = if (salt) com.moriafly.salt.ui.SaltTheme.colors.text
    else MiuixTheme.colorScheme.onSurface
    val textStyle = if (salt) com.moriafly.salt.ui.SaltTheme.textStyles.main.copy(color = textColor)
    else MiuixTheme.textStyles.paragraph.copy(color = textColor)

    // 仅处理自动聚焦，不操作光标
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val defaultLeadingIcon: @Composable () -> Unit = {
        if (salt) {
            com.moriafly.salt.ui.Icon(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                painter = rememberVectorPainter(LucideIcons.Search),
                tint = com.moriafly.salt.ui.SaltTheme.colors.subText,
                contentDescription = null
            )
        } else Icon(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            imageVector = LucideIcons.Search,
            tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
            contentDescription = null
        )
    }

    val defaultTrailingIcon: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = state.text.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                val clearModifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        indication = null,
                        interactionSource = interactionSource
                    ) {
                        // clearText() 内部会正确处理光标
                        state.clearText()
                    }
                if (salt) {
                    com.moriafly.salt.ui.Icon(
                        modifier = clearModifier,
                        painter = rememberVectorPainter(LucideIcons.Close),
                        tint = com.moriafly.salt.ui.SaltTheme.colors.subText,
                        contentDescription = "Clear"
                    )
                } else Icon(
                    modifier = clearModifier,
                    imageVector = LucideIcons.Close,
                    tint = MiuixTheme.colorScheme.onSurfaceContainerHighest,
                    contentDescription = "Clear"
                )
            }
        }
    }

    BasicTextField(
        state = state,
        enabled = enabled,
        textStyle = textStyle,
        cursorBrush = SolidColor(
            if (salt) com.moriafly.salt.ui.SaltTheme.colors.highlight
            else MiuixTheme.colorScheme.primary
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = {
            onSearch?.invoke(state.text.toString())
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        interactionSource = interactionSource,
        modifier = modifier.focusRequester(focusRequester),
        decorator = { innerTextField ->
            val pillShape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .background(
                        color = if (salt) com.moriafly.salt.ui.SaltTheme.colors.subBackground
                        else MiuixTheme.colorScheme.surfaceContainerHigh,
                        shape = pillShape,
                    )
                    .then(
                        if (salt) Modifier.border(
                            1.dp,
                            com.moriafly.salt.ui.SaltTheme.colors.stroke,
                            pillShape
                        ) else Modifier
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    (leadingIcon ?: defaultLeadingIcon)()

                    Box(
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .heightIn(min = 45.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (state.text.isEmpty() && placeholder.isNotEmpty()) {
                            if (salt) {
                                com.moriafly.salt.ui.Text(
                                    text = placeholder,
                                    style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                                    color = com.moriafly.salt.ui.SaltTheme.colors.subText,
                                    maxLines = 1
                                )
                            } else Text(
                                text = placeholder,
                                style = MiuixTheme.textStyles.main.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurfaceContainerHigh
                                ),
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }

                    (trailingIcon ?: defaultTrailingIcon)()
                }
            }
        },
    )
}

@Composable
fun SearchBar(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    actions: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
    autoFocus: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InputField(
            state = state,
            placeholder = placeholder,
            onSearch = onSearch,
            autoFocus = autoFocus,
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth(),
            trailingIcon = trailingIcon
        )
        actions?.invoke()
    }
}
