@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.library

import com.lonx.lyrico.ui.components.icons.LucideIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun IllustratedEmptyState(
    title: String,
    summary: String? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: () -> Unit = {},
    illustration: (@Composable () -> Unit)? = null,
    icon: ImageVector = LucideIcons.ArrowRight,
    kind: EmptyStateKind = EmptyStateKind.List,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val scrollModifier = if (constraints.hasBoundedHeight) {
            Modifier.verticalScroll(rememberScrollState())
        } else {
            Modifier
        }
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .then(scrollModifier)
                .padding(if (compact) 16.dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (illustration != null) illustration() else EmptyStateAnimation(kind, compact)
            Spacer(Modifier.height(if (compact) 12.dp else 24.dp))

            if (LocalUiEngine.current == UiEngine.SALT) {
                com.moriafly.salt.ui.Text(title, textAlign = TextAlign.Center)
                if (!summary.isNullOrBlank()) com.moriafly.salt.ui.Text(
                    summary, color = SaltTheme.colors.subText,
                    style = SaltTheme.textStyles.sub,
                    modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
            } else {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title4,
                    textAlign = TextAlign.Center
                )

                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

            }

            val salt = LocalUiEngine.current == UiEngine.SALT
            val actionColor = if (salt) SaltTheme.colors.highlight else MiuixTheme.colorScheme.primary
            // Both engines keep the lightweight highlighted-text action.
            if (!actionText.isNullOrBlank()) {
                Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = actionColor),
                            onClick = onAction
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp), // 保证触控舒适度
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (salt) {
                        com.moriafly.salt.ui.Text(
                            text = actionText,
                            style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                            color = actionColor,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        Text(
                            text = actionText,
                            style = MiuixTheme.textStyles.body2,
                            color = actionColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    // Salt 原生 Icon 只接受 Painter，用矢量画笔保持同一图标与尺寸。
                    if (salt) {
                        com.moriafly.salt.ui.Icon(
                            painter = rememberVectorPainter(icon),
                            contentDescription = null,
                            tint = actionColor,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(start = 2.dp)
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = actionColor,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(start = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
