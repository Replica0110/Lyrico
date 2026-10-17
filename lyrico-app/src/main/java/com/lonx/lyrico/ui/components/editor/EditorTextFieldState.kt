package com.lonx.lyrico.ui.components.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Bridge existing drafts to a state-based field without resetting selection on each model echo. */
@Composable
internal fun rememberEditorTextFieldState(
    value: String,
    onValueChange: (String) -> Unit,
): TextFieldState {
    // Compose initializes selection at the end and owns selection/IME composition afterwards.
    val state = rememberTextFieldState(initialText = value)
    val onChange by rememberUpdatedState(onValueChange)
    val sync = remember(state) { EditorTextSync(value) }

    LaunchedEffect(value, state) {
        if (value != sync.lastSent && state.text.toString() != value) {
            sync.lastSent = value
            state.edit { replace(0, length, value) }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .collect { text ->
                if (text != sync.lastSent) {
                    sync.lastSent = text
                    onChange(text)
                }
            }
    }
    return state
}

private class EditorTextSync(var lastSent: String)
