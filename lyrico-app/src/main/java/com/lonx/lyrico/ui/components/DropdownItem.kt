package com.lonx.lyrico.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DropdownItem(
    text: String,
    optionSize: Int,
    index: Int,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    iconPainter: Painter? = null,
    isSelected: Boolean = false,
    onSelectedIndexChange: (Int) -> Unit
) {
    val currentOnSelectedIndexChange = rememberUpdatedState(onSelectedIndexChange)
    val additionalTopPadding = if (index == 0) 20.dp else 12.dp
    val additionalBottomPadding = if (index == optionSize - 1) 20.dp else 12.dp

    Row(
        modifier = Modifier
            .clickable { currentOnSelectedIndexChange.value(index) }
            .background(dropdownColors.containerColor)
            .padding(horizontal = 20.dp)
            .padding(
                top = additionalTopPadding,
                bottom = additionalBottomPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected)
                dropdownColors.selectedContentColor
            else
                dropdownColors.contentColor,
        )

        if (isSelected && iconPainter != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = dropdownColors.contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}