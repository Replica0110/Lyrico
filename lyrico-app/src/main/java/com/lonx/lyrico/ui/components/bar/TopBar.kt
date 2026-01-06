package com.lonx.lyrico.ui.components.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.icons.ArrowBack
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.noRippleClickable

@Composable
fun TopBar(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = SaltTheme.colors.background,
    // 默认处理 StatusBars 的 padding，使得组件高度 = 状态栏 + 56dp
    windowInsets: WindowInsets = WindowInsets.statusBars,
    onBack: (() -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {
        if (onBack != null) {
            SaltBackButton(onBack = onBack)
        }
    },
    actions: @Composable RowScope.() -> Unit = {}
) {
    // 外层容器：全屏宽，背景色（透明时可见 Haze），负责承载 HazeEffect
    Box(
        modifier = modifier
            .background(backgroundColor)
            .fillMaxWidth()
    ) {
        // 内容容器：使用 windowInsetsPadding 避让状态栏
        // 这样文字和按钮会显示在状态栏下方，但背景会延伸到状态栏后面
        Box(
            modifier = Modifier
                .windowInsetsPadding(windowInsets)
                .height(56.dp) // 内容区域标准高度
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            // 左侧
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .minimumInteractiveComponentSize(),
                contentAlignment = Alignment.Center
            ) {
                navigationIcon()
            }

            // 中间标题
            Text(
                text = text,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                color = SaltTheme.colors.text
            )

            // 右侧 Actions
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                actions()
            }
        }
    }
}

// 辅助组件：返回按钮
@Composable
fun SaltBackButton(onBack: () -> Unit) {
    Icon(
        modifier = Modifier
            .size(56.dp)
            .noRippleClickable { onBack() }
            .padding(18.dp),
        painter = rememberVectorPainter(SaltIcons.ArrowBack),
        contentDescription = "Back",
        tint = SaltTheme.colors.text
    )
}