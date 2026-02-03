package com.lonx.lyrico.ui.components.bar


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.R
import com.moriafly.salt.ui.SaltTheme


/**
 * 搜索栏组件
 *
 * @param value 当前输入的文字
 * @param onValueChange 文字改变时的回调
 * @param placeholder 占位提示文字
 * @param modifier 外部修饰符
 */
@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "搜索"
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        maxLines = 1,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 13.sp,
            color = SaltTheme.colors.text,
            fontWeight = FontWeight.Bold
        ),
        cursorBrush = SolidColor(SaltTheme.colors.highlight),
        modifier = modifier
            .height(36.dp)
            .background(SaltTheme.colors.subBackground, CircleShape)
            .clip(CircleShape),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp) // 内部左右边距
            ) {
                // 1. 左侧搜索图标
                Icon(
                    painter = painterResource(R.drawable.ic_search_24dp),
                    contentDescription = "Search",
                    tint = SaltTheme.colors.subText,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // 2. 中间输入区域 + 占位符
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // 占位符：没有文字时显示
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(
                                color = SaltTheme.colors.subText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                    // 实际输入框
                    innerTextField()
                }

                // 3. 右侧清除按钮：有文字时显示
                if (value.isNotEmpty()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clear_24dp),
                        contentDescription = "Clear",
                        tint = SaltTheme.colors.subText,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                indication = null, // 无水波纹
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                onValueChange("") // 清空逻辑
                            }
                    )
                }
            }
        }
    )
}