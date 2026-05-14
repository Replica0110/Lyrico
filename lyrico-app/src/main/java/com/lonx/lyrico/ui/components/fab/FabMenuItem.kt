package com.lonx.lyrico.ui.components.fab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FabMenuItem(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    style: ExpandableFabMenuStyle = ExpandableFabMenuStyle.default(),
    iconAlignmentPadding: PaddingValues = PaddingValues(
        end = ((style.mainFabSize - style.itemFabSize) / 2).coerceAtLeast(0.dp)
    ),
    onClick: () -> Unit
) {
    val textColor = if (enabled) {
        style.labelTextColor
    } else {
        style.labelDisabledTextColor
    }

    val iconColor = if (enabled) {
        style.itemContentColor
    } else {
        style.itemDisabledContentColor
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = modifier.padding(iconAlignmentPadding)
    ) {
        Surface(
            shape = RoundedCornerShape(style.labelCornerRadius),
            color = style.labelContainerColor,
            shadowElevation = style.labelShadowElevation,
            modifier = Modifier
                .clip(RoundedCornerShape(style.labelCornerRadius))
                .clickable(enabled = enabled, onClick = onClick)
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.main,
                color = textColor,
                modifier = Modifier.padding(
                    horizontal = style.labelHorizontalPadding,
                    vertical = style.labelVerticalPadding
                )
            )
        }

        Spacer(modifier = Modifier.width(style.labelToIconSpacing))

        // 小尺寸的 FAB
        SmallFloatingActionButton(
            onClick = {
                if (enabled) onClick()
            },
            modifier = Modifier.size(style.itemFabSize),
            containerColor = style.itemContainerColor,
            contentColor = iconColor
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(style.itemIconSize)
            )
        }
    }
}