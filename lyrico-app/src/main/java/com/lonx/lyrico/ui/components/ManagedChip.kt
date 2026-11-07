package com.lonx.lyrico.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 胶囊换行网格，规则类页面共用。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGrid(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** 沿用艺术家拆分规则的胶囊样式，移除边框与勾选圈。 */
@Composable
fun ManagedChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    val background = if (salt) SaltTheme.colors.highlight.copy(alpha = 0.1f)
        else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
    val contentColor = if (salt) SaltTheme.colors.text else MiuixTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 6.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val labelModifier = Modifier.weight(1f, fill = false).padding(horizontal = 6.dp)
        if (salt) {
            com.moriafly.salt.ui.Text(
                text = text,
                modifier = labelModifier,
                style = SaltTheme.textStyles.sub,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = text,
                modifier = labelModifier,
                style = MiuixTheme.textStyles.footnote1,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onDelete != null) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                val description = "${stringResource(R.string.common_delete)} $text"
                if (salt) {
                    com.moriafly.salt.ui.Icon(
                        painter = rememberVectorPainter(LucideIcons.Trash),
                        contentDescription = description,
                        modifier = Modifier.size(16.dp),
                        tint = SaltTheme.colors.subText,
                    )
                } else {
                    Icon(
                        imageVector = LucideIcons.Trash,
                        contentDescription = description,
                        modifier = Modifier.size(16.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            }
        }
    }
}
