package com.lonx.lyrico.ui.components.fab

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme


/**
 * 可展开浮动操作按钮菜单的样式配置类
 *
 * 该类定义了 ExpandableFabMenu 组件的所有视觉和布局属性，包括主按钮、子菜单项、标签等的外观和行为。
 * 通过自定义这些属性，可以实现不同风格的浮动操作按钮菜单。
 *
 * @property mainIcon 主按钮图标，默认为加号图标
 * @property mainIconRotationWhenExpanded 展开时主图标的旋转角度（度），默认为45度
 * @property mainFabSize 主按钮尺寸，默认为56dp
 * @property mainIconSize 主按钮内图标尺寸，默认为24dp
 * @property mainContainerColor 主按钮背景颜色
 * @property mainContentColor 主按钮内容颜色（图标颜色）
 *
 * @property itemFabSize 子菜单项按钮尺寸，默认为40dp
 * @property itemIconSize 子菜单项图标尺寸，默认为20dp
 * @property itemContainerColor 子菜单项背景颜色
 * @property itemContentColor 子菜单项内容颜色（图标颜色）
 * @property itemDisabledContentColor 子菜单项禁用状态内容颜色
 *
 * @property labelContainerColor 标签容器背景颜色
 * @property labelTextColor 标签文本颜色
 * @property labelDisabledTextColor 标签禁用状态文本颜色
 * @property labelHorizontalPadding 标签水平内边距，默认为12dp
 * @property labelVerticalPadding 标签垂直内边距，默认为6dp
 * @property labelCornerRadius 标签圆角半径，默认为8dp
 * @property labelShadowElevation 标签阴影高度，默认为2dp
 *
 * @property scrimColor 遮罩层颜色，默认为半透明黑色
 *
 * @property menuItemSpacing 菜单项之间的间距，默认为12dp
 * @property fabToMenuSpacing 主按钮与菜单之间的间距，默认为16dp
 * @property menuToFabPadding 菜单到主按钮的内边距，默认为8dp
 * @property itemEndPadding 菜单项末尾内边距，默认为8dp
 */
data class ExpandableFabMenuStyle(
    val mainIcon: ImageVector = MiuixIcons.Add,
    val mainIconRotationWhenExpanded: Float = 45f,
    val mainFabSize: Dp = 56.dp,
    val mainIconSize: Dp = 24.dp,
    val mainContainerColor: Color,
    val mainContentColor: Color,

    val itemFabSize: Dp = 40.dp,
    val itemIconSize: Dp = 20.dp,
    val itemContainerColor: Color,
    val itemContentColor: Color,
    val itemDisabledContentColor: Color,

    val labelContainerColor: Color,
    val labelTextColor: Color,
    val labelDisabledTextColor: Color,
    val labelHorizontalPadding: Dp = 12.dp,
    val labelVerticalPadding: Dp = 6.dp,
    val labelCornerRadius: Dp = 8.dp,
    val labelShadowElevation: Dp = 2.dp,

    val scrimColor: Color = Color.Black.copy(alpha = 0.2f),

    val menuItemSpacing: Dp = 12.dp,
    val fabToMenuSpacing: Dp = 16.dp,
    val menuToFabPadding: Dp = 8.dp,
    val itemEndPadding: Dp = 8.dp,
    val labelToIconSpacing: Dp = 12.dp
) {
    companion object {
        /**
         * 创建默认的 ExpandableFabMenuStyle 实例
         *
         * 使用 MiuixTheme 的颜色方案来配置样式，确保与应用程序的整体设计风格保持一致。
         *
         * @return 默认的 ExpandableFabMenuStyle 实例
         */
        @Composable
        fun default(): ExpandableFabMenuStyle {
            return ExpandableFabMenuStyle(
                mainContainerColor = MiuixTheme.colorScheme.primary,
                mainContentColor = MiuixTheme.colorScheme.onPrimary,
                itemContainerColor = MiuixTheme.colorScheme.surface,
                itemContentColor = MiuixTheme.colorScheme.primary,
                itemDisabledContentColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                labelContainerColor = MiuixTheme.colorScheme.surface,
                labelTextColor = MiuixTheme.colorScheme.onSurface,
                labelDisabledTextColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}