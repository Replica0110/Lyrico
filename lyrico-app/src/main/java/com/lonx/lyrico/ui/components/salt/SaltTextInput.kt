package com.lonx.lyrico.ui.components.salt

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.ui.components.editor.rememberEditorTextFieldState
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/** A stable input decoration; Compose owns text, selection, composition, and password editing. */
@Composable
internal fun SaltTextInput(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    hint: String? = null,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    actionContent: (@Composable () -> Unit)? = null,
    label: String? = null,
    summary: String? = null,
    error: String? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    secure: Boolean = false,
    passwordVisible: Boolean = false,
    inputTransformation: InputTransformation? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val decorator = TextFieldDecorator { innerTextField ->
        Column(
            Modifier.fillMaxWidth()
                .border(
                    1.dp,
                    when {
                        error != null -> SaltTheme.colors.error
                        focused -> SaltTheme.colors.highlight
                        else -> Color.Transparent
                    },
                    SaltTheme.shapes.medium,
                )
        ) {
            if (label != null) {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 4.dp)
                        .heightIn(min = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        Modifier.weight(1f),
                        style = SaltTheme.textStyles.sub.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color =
                            if (error != null) SaltTheme.colors.error else SaltTheme.colors.subText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    actionContent?.invoke()
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = if (label != null) 36.dp else 48.dp)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (label != null) 2.dp else 12.dp,
                        bottom = if (label != null) 10.dp else 12.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopStart) {
                    if (state.text.isEmpty() && !hint.isNullOrBlank()) {
                        Text(
                            hint,
                            color = SaltTheme.colors.subText,
                            style = SaltTheme.textStyles.main,
                            maxLines = when (lineLimits) {
                                is TextFieldLineLimits.MultiLine -> if (secure) 1 else lineLimits.maxHeightInLines
                                else -> 1
                            },
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (label == null) actionContent?.invoke()
            }
            summary
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    Text(
                        it,
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        color = SaltTheme.colors.subText,
                        style = SaltTheme.textStyles.sub,
                    )
                }
            error
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    Text(
                        it,
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        color = SaltTheme.colors.error,
                        style = SaltTheme.textStyles.sub,
                    )
                }
        }
    }
    val fieldModifier =
        modifier.fillMaxWidth().semantics {
            if (label != null) contentDescription = label
        }
    val textStyle = SaltTheme.textStyles.main.copy(color = SaltTheme.colors.text)
    val cursor = SolidColor(SaltTheme.colors.highlight)
    if (secure) {
        BasicSecureTextField(
            state = state,
            modifier = fieldModifier,
            readOnly = readOnly,
            textStyle = textStyle,
            cursorBrush = cursor,
            interactionSource = interaction,
            inputTransformation = inputTransformation,
            decorator = decorator,
            textObfuscationMode =
                if (passwordVisible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
        )
    } else {
        BasicTextField(
            state = state,
            modifier = fieldModifier,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            lineLimits = lineLimits,
            textStyle = textStyle,
            cursorBrush = cursor,
            interactionSource = interaction,
            inputTransformation = inputTransformation,
            decorator = decorator,
        )
    }
}

/** Compatibility with existing drafts; their echoes never recreate the editing state. */
@Composable
internal fun SaltTextInput(
    text: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    actionContent: (@Composable () -> Unit)? = null,
    label: String? = null,
    summary: String? = null,
    error: String? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    secure: Boolean = false,
    passwordVisible: Boolean = false,
    inputTransformation: InputTransformation? = null,
) {
    SaltTextInput(
        state = rememberEditorTextFieldState(text, onChange),
        modifier = modifier,
        hint = hint,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        actionContent = actionContent,
        label = label,
        summary = summary,
        error = error,
        lineLimits = lineLimits,
        secure = secure,
        passwordVisible = passwordVisible,
        inputTransformation = inputTransformation,
    )
}
