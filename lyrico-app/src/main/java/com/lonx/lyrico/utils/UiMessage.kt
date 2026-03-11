package com.lonx.lyrico.utils

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiMessage {
    data class DynamicString(val value: String?) : UiMessage

    class StringResource(
        @field:StringRes val resId: Int,
        vararg val args: Any
    ) : UiMessage

    fun asString(context: Context): String ?{
        return when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(resId, *args)
        }
    }

    @Composable
    fun asString(): String? {
        return when (this) {
            is DynamicString -> value
            is StringResource -> stringResource(resId, *args)
        }
    }
}