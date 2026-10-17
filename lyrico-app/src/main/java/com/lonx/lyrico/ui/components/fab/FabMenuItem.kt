package com.lonx.lyrico.ui.components.fab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.SaltTheme

@Composable
fun FabMenuItem(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    style: ExpandableFabMenuStyle = ExpandableFabMenuStyle.default(),
    onClick: () -> Unit
) {
    val contentColor = if (enabled) {
        style.itemContentColor
    } else {
        style.itemDisabledContentColor
    }

    if (LocalUiEngine.current == UiEngine.SALT) {
        Row(
            modifier = modifier.fillMaxWidth().height(style.itemHeight)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.moriafly.salt.ui.Icon(
                painter = rememberVectorPainter(icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(style.itemIconSize),
            )
            Spacer(Modifier.width(SaltTheme.dimens.subPadding))
            com.moriafly.salt.ui.Text(
                text = label,
                style = SaltTheme.textStyles.main,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }

    BasicComponent(
        modifier = modifier.height(style.itemHeight),
        enabled = enabled,
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(style.itemIconSize)
            )
        },
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.main,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
