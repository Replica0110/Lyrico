package com.lonx.lyrico.ui.components.bar


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.theme.MiuixTheme





@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    onSearch: ((String) -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val textColor = MiuixTheme.colorScheme.onSurface
    val textStyle = MiuixTheme.textStyles.paragraph.copy(
        color = textColor
    )

    val actualLeadingIcon = leadingIcon ?: {
        Icon(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            imageVector = MiuixIcons.Basic.Search,
            tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
            contentDescription = null
        )
    }

    val actualTrailingIcon = trailingIcon ?: {
        AnimatedVisibility(
            visible = value.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    modifier = Modifier
                        .clip(Capsule())
                        .clickable(
                            indication = null,
                            interactionSource = interactionSource
                        ) {
                            onValueChange("")
                        },
                    imageVector = MiuixIcons.Basic.SearchCleanup,
                    tint = MiuixTheme.colorScheme.onSurfaceContainerHighest,
                    contentDescription = "Clear"
                )
            }
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { onSearch?.invoke(value) }
        ),
        interactionSource = interactionSource,
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .background(
                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                        shape = Capsule(),
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actualLeadingIcon()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 45.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = MiuixTheme.textStyles.main.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurfaceContainerHigh
                                )
                            )
                        }
                        innerTextField()
                    }

                    actualTrailingIcon()
                }
            }
        },
    )
}
@Composable
fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    actionText: String = "取消",
    onActionClick: () -> Unit,
    onSearch: ((String) -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {

        InputField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            onSearch = onSearch,
            modifier = Modifier.weight(1f)
        )

        AnimatedVisibility(
            visible = true,
            enter = expandHorizontally() + slideInHorizontally { it },
            exit = shrinkHorizontally() + slideOutHorizontally { it }
        ) {
            Text(
                text = actionText,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        onActionClick()
                    },
                style = MiuixTheme.textStyles.main,
                color = MiuixTheme.colorScheme.primary
            )
        }
    }
}