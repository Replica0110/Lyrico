package com.lonx.lyrico.ui.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.data.model.UiEngine
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class PillButtonSize {
    Small,
    Medium,
    Large
}

@Immutable
data class PillButtonStyle(
    val height: Dp,
    val contentPadding: PaddingValues,
    val iconOnlyPadding: PaddingValues,
    val spacing: Dp,
    val shape: Shape,
    val textStyle: TextStyle,
    val selectedTextStyle: TextStyle = textStyle
)

@Immutable
data class PillButtonColors(
    val selectedContainerColor: Color,
    val selectedContentColor: Color,
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color
) {
    fun containerColor(
        selected: Boolean,
        enabled: Boolean
    ): Color {
        return when {
            !enabled -> disabledContainerColor
            selected -> selectedContainerColor
            else -> containerColor
        }
    }

    fun contentColor(
        selected: Boolean,
        enabled: Boolean
    ): Color {
        return when {
            !enabled -> disabledContentColor
            selected -> selectedContentColor
            else -> contentColor
        }
    }
}

object PillButtonDefaults {

    @Composable
    fun style(size: PillButtonSize): PillButtonStyle {
        // SaltUI 的密度更小，字号与字重按 Salt 的语义样式派生。
        val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current == UiEngine.SALT
        val smallBase = if (salt) com.moriafly.salt.ui.SaltTheme.textStyles.sub
        else MiuixTheme.textStyles.body2
        val mediumBase = if (salt) com.moriafly.salt.ui.SaltTheme.textStyles.main
        else MiuixTheme.textStyles.body2
        val largeBase = if (salt) com.moriafly.salt.ui.SaltTheme.textStyles.main
        else MiuixTheme.textStyles.body1
        return when (size) {
            PillButtonSize.Small -> PillButtonStyle(
                height = 28.dp,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                iconOnlyPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                spacing = 4.dp,
                shape = RoundedCornerShape(14.dp),
                textStyle = smallBase.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                selectedTextStyle = smallBase.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )

            PillButtonSize.Medium -> PillButtonStyle(
                height = 32.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                iconOnlyPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                spacing = 5.dp,
                shape = RoundedCornerShape(16.dp),
                textStyle = mediumBase.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                selectedTextStyle = mediumBase.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            PillButtonSize.Large -> PillButtonStyle(
                height = 38.dp,
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 7.dp),
                iconOnlyPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                spacing = 6.dp,
                shape = RoundedCornerShape(19.dp),
                textStyle = largeBase.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                selectedTextStyle = largeBase.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    /** 未选中的胶囊底色：亮/暗主题下都跟随当前引擎的表面色。 */
    @Composable
    fun defaultContainerColor(): Color =
        if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == UiEngine.SALT) {
            com.moriafly.salt.ui.SaltTheme.colors.subBackground
        } else MiuixTheme.colorScheme.surface

    @Composable
    fun colors(
        selectedContainerColor: Color = if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == UiEngine.SALT) {
            com.moriafly.salt.ui.SaltTheme.colors.highlight
        } else MiuixTheme.colorScheme.primary,
        selectedContentColor: Color = if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == UiEngine.SALT) {
            com.moriafly.salt.ui.SaltTheme.colors.onHighlight
        } else MiuixTheme.colorScheme.onPrimary,
        containerColor: Color = defaultContainerColor(),
        contentColor: Color = if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == UiEngine.SALT) {
            com.moriafly.salt.ui.SaltTheme.colors.text
        } else MiuixTheme.colorScheme.onSurface,
        disabledContainerColor: Color = defaultContainerColor(),
        disabledContentColor: Color = if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == UiEngine.SALT) {
            com.moriafly.salt.ui.SaltTheme.colors.subText
        } else MiuixTheme.colorScheme.disabledOnSurface
    ): PillButtonColors {
        return PillButtonColors(
            selectedContainerColor = selectedContainerColor,
            selectedContentColor = selectedContentColor,
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    }

    /**
     * 无底色、强调色文字的胶囊：面包屑、加载更多这类链接式行内操作。
     * Salt 原生 Plain 按钮最小 48dp，和列表/行内密度对不上。
     */
    @Composable
    fun plainColors(): PillButtonColors {
        val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current == UiEngine.SALT
        val accent = if (salt) com.moriafly.salt.ui.SaltTheme.colors.highlight
        else MiuixTheme.colorScheme.primary
        val disabledContent = if (salt) com.moriafly.salt.ui.SaltTheme.colors.subText
        else MiuixTheme.colorScheme.disabledOnSurface
        return colors(
            selectedContainerColor = Color.Transparent,
            selectedContentColor = accent,
            containerColor = Color.Transparent,
            contentColor = accent,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = disabledContent,
        )
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    showText: Boolean = true,
    style: PillButtonStyle = PillButtonDefaults.style(PillButtonSize.Medium),
    colors: PillButtonColors = PillButtonDefaults.colors(),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val containerColor = colors.containerColor(
        selected = selected,
        enabled = enabled
    )

    val contentColor = colors.contentColor(
        selected = selected,
        enabled = enabled
    )

    val hasText = showText && text.isNotBlank()
    val hasLeading = leading != null
    val hasTrailing = trailing != null
    val iconOnly = !hasText && (hasLeading || hasTrailing)

    if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == UiEngine.SALT) {
        com.moriafly.salt.ui.BasicButton(
            onClick = onClick, modifier = modifier.heightIn(min = style.height), enabled = enabled,
            size = com.moriafly.salt.ui.ControlSize.Small,
            contentPadding = if (iconOnly) style.iconOnlyPadding else style.contentPadding,
            colors = com.moriafly.salt.ui.ButtonColors(
                containerColor = if (selected) colors.selectedContainerColor else colors.containerColor,
                contentColor = if (selected) colors.selectedContentColor else colors.contentColor,
                disabledContainerColor = colors.disabledContainerColor,
                disabledContentColor = colors.disabledContentColor,
            ),
        ) {
            leading?.invoke()
            if (hasLeading && hasText) Spacer(Modifier.width(style.spacing))
            // 字号/字重必须跟随 PillButtonStyle 的 Small/Medium/Large 映射，
            // 否则会被 Salt 的正文字号顶回去，胶囊比周围组件大一圈。
            if (hasText) com.moriafly.salt.ui.Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = if (selected) style.selectedTextStyle else style.textStyle)
            if (hasTrailing && hasText) Spacer(Modifier.width(style.spacing))
            trailing?.invoke()
        }
        return
    }

    Box(
        modifier = modifier
            .height(style.height)
            .clip(style.shape)
            .background(containerColor)
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .padding(
                if (iconOnly) {
                    style.iconOnlyPadding
                } else {
                    style.contentPadding
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leading != null) {
                leading()
            }

            if (leading != null && hasText) {
                Spacer(modifier = Modifier.width(style.spacing))
            }

            if (hasText) {
                val textStyle = if (selected) style.selectedTextStyle else style.textStyle
                Text(
                    text = text,
                    style = textStyle,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (trailing != null && hasText) {
                Spacer(modifier = Modifier.width(style.spacing))
            }

            if (trailing != null) {
                trailing()
            }
        }
    }
}
