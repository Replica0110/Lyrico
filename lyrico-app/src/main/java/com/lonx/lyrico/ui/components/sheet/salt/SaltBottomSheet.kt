@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.sheet.salt

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.dialog.BottomSheetBehaviorProperties
import com.moriafly.salt.ui.dialog.BottomSheetDialog
import com.moriafly.salt.ui.dialog.BottomSheetDialogProperties
import com.moriafly.salt.ui.dialog.NavigationBarProperties

/**
 * Salt 原生底部弹层外壳。
 *
 * 圆角、拖拽把手和安全区由外壳处理；卡片分组用 [groupContent] 或 [RoundedColumn]，底部按钮走 [footer]。
 * 纵向滚动只留一个出口：[scrollContent] 仅在内容自身不滚动时置 true；内容自带 LazyColumn/verticalScroll
 * 时保持 false，内层独立滚动需自限高度。
 * [allowDismiss] = false 会同时关闭返回键、外部点击和下拉手势，用于进行中的进度弹层。
 * [title] 为居中标题，[header] 为标题下方的工具操作行。
 */
@Composable
internal fun SaltBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    scrollContent: Boolean = false,
    groupContent: Boolean = false,
    allowDismiss: Boolean = true,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var wasVisible by remember { mutableStateOf(show) }
    val finished by rememberUpdatedState(onDismissFinished)
    // Native drag dismissal is already complete. Remove its window without a second exit
    // animation, otherwise beta02 reapplies Expanded while the Compose exit is running.
    LaunchedEffect(show) {
        if (!show && wasVisible) finished()
        wasVisible = show
    }
    if (!show) return
    val maxHeight = LocalWindowInfo.current.containerDpSize.height * 0.9f
    val maxWidth = with(LocalDensity.current) { 640.dp.roundToPx() }
    BottomSheetDialog(
        onDismissRequest = { if (allowDismiss) onDismissRequest() },
        properties =
            BottomSheetDialogProperties(
                dismissOnBackPress = allowDismiss,
                dismissOnClickOutside = allowDismiss,
                behaviorProperties =
                    BottomSheetBehaviorProperties(
                        state = BottomSheetBehaviorProperties.State.Expanded,
                        maxWidth = BottomSheetBehaviorProperties.Size(maxWidth),
                        skipCollapsed = true,
                        isDraggable = allowDismiss,
                        isHideable = allowDismiss,
                    ),
                navigationBarProperties =
                    NavigationBarProperties(color = SaltTheme.colors.background),
            ),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = maxHeight)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(SaltTheme.colors.background)
                // 内容和固定操作区共同消费安全区；IME 与底部系统栏取并集。
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        .union(WindowInsets.ime)
                )
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(32.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SaltTheme.colors.subText.copy(alpha = 0.3f))
            )
            title?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp)
                        .semantics { heading() },
                    style = SaltTheme.textStyles.main.copy(
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = SaltTheme.colors.text,
                    textAlign = TextAlign.Center,
                )
            }
            header?.invoke()
            Column(
                Modifier.fillMaxWidth()
                    .weight(1f, fill = false)
                    .then(
                        if (scrollContent) Modifier.verticalScroll(rememberScrollState())
                        else Modifier
                    )
                    .padding(bottom = 8.dp)
            ) {
                if (groupContent) {
                    com.moriafly.salt.ui.RoundedColumn {
                        Column(Modifier.fillMaxWidth().padding(SaltTheme.dimens.padding)) {
                            content()
                        }
                    }
                } else {
                    content()
                }
            }
            footer?.invoke()
        }
    }
}
