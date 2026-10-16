package com.lonx.lyrico.ui.components.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.ui.components.base.SaltBottomSheet
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.data.model.UiEngine
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** beta02 ItemEdit lacks TextFieldState and line limits. Keep the platform editing state
 * while using Salt's native text, cursor and grouped surface. */
@Composable
internal fun SaltEditorField(
    label: String,
    state: TextFieldState,
    modifier: Modifier = Modifier,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    grouped: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    SaltEditorFieldContainer(label, modifier, grouped, actions) {
        BasicTextField(
            state = state,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            textStyle = SaltTheme.textStyles.main.copy(color = SaltTheme.colors.text),
            cursorBrush = SolidColor(SaltTheme.colors.highlight),
            lineLimits = lineLimits,
        )
    }
}

/** String-backed batch drafts share the same layout but keep single-line fields single line. */
@Composable
internal fun SaltEditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    grouped: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    SaltEditorFieldContainer(label, modifier, grouped, actions) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            textStyle = SaltTheme.textStyles.main.copy(color = SaltTheme.colors.text),
            cursorBrush = SolidColor(SaltTheme.colors.highlight),
            singleLine = !multiline,
            minLines = if (multiline) 6 else 1,
            maxLines = if (multiline) 12 else 1,
        )
    }
}

@Composable
private fun SaltEditorFieldContainer(
    label: String, modifier: Modifier, grouped: Boolean,
    actions: @Composable RowScope.() -> Unit, input: @Composable () -> Unit,
) {
    val body: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            com.moriafly.salt.ui.Text(label, Modifier.weight(1f),
                style = SaltTheme.textStyles.sub, color = SaltTheme.colors.subText)
            actions()
        }
        input()
    }
    if (grouped) RoundedColumn(modifier = modifier, content = body)
    else Column(modifier.fillMaxWidth(), content = body)
}

/** Single native window, with the existing Miuix content retained by the caller. */
@Composable
internal fun EditorSheet(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    enableNestedScroll: Boolean = false,
    endAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalUiEngine.current == UiEngine.SALT) SaltBottomSheet(
        show = show, onDismissRequest = onDismissRequest,
        header = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ItemOuterTitle(title) }
                endAction?.invoke()
                Spacer(Modifier.width(12.dp))
            }
        },
        content = content,
    ) else WindowBottomSheet(show = show, title = title,
        enableNestedScroll = enableNestedScroll, onDismissRequest = onDismissRequest,
        endAction = { endAction?.invoke() }) { Column(Modifier.fillMaxWidth(), content = content) }
}
