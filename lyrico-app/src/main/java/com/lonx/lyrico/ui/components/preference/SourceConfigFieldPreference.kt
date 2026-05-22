package com.lonx.lyrico.ui.components.preference

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginConfigFieldType
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SourceConfigFieldPreference(
    field: PluginConfigField,
    value: String,
    values: Map<String, String>,
    error: String?,
    onValueChange: (String) -> Unit
) {
    when (field.type) {
        PluginConfigFieldType.SWITCH -> {
            SwitchPreference(
                title = field.title,
                summary = summaryText(field, error),
                checked = value.toBooleanStrictOrNull() ?: false,
                onCheckedChange = { onValueChange(it.toString()) }
            )
        }
        PluginConfigFieldType.DROPDOWN -> {
            val selectedIndex = field.options.indexOfFirst { it.value == value }.coerceAtLeast(0)
            WindowDropdownPreference(
                title = field.title,
                summary = summaryText(field, error),
                items = field.options.map { it.label },
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { index ->
                    field.options.getOrNull(index)?.let { onValueChange(it.value) }
                }
            )
        }
        PluginConfigFieldType.TEXT,
        PluginConfigFieldType.PASSWORD,
        PluginConfigFieldType.NUMBER -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text = field.title)
                val helper = summaryText(field, error)
                if (helper.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = helper,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = if (error == null) {
                            MiuixTheme.colorScheme.onSurfaceVariantActions
                        } else {
                            Color.Red
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = value,
                    maxLines = 1,
                    onValueChange = { newValue ->
                        onValueChange(
                            if (field.type == PluginConfigFieldType.NUMBER) {
                                newValue.filter(Char::isDigit)
                            } else {
                                newValue
                            }
                        )
                    }
                )
            }
        }
    }
}

private fun summaryText(field: PluginConfigField, error: String?): String {
    return listOfNotNull(field.summary.takeIf { it.isNotBlank() }, error).joinToString("\n")
}
