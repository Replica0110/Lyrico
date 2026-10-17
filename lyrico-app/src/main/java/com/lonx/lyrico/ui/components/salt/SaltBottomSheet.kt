@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.salt

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.dialog.BottomSheetBehaviorProperties
import com.moriafly.salt.ui.dialog.BottomSheetDialog
import com.moriafly.salt.ui.dialog.BottomSheetDialogProperties
import com.moriafly.salt.ui.dialog.NavigationBarProperties

/**
 * Salt 原生底部弹层外壳。职责边界（避免逐页堆偏移补丁）：
 *
 * | 归属                        | 负责方                                           |
 * |---------------------------|-----------------------------------------------|
 * | 圆角、拖拽把手、左右安全区、底部安全区 + 8dp | 本外壳                                           |
 * | 卡片外边距                     | 裸内容用 groupContent；原生行分组由调用方使用 RoundedColumn   |
 * | 内容内边距                     | groupContent 为裸内容提供 padding；原生行由其自身提供，不叠加     |
 * | 纵向滚动出口                    | 只允许一个：内容自带 LazyColumn/verticalScroll 时本外壳不加滚动 |
 *
 * [header] 由调用方决定（例如 [com.moriafly.salt.ui.ItemOuterTitle]，它自带 outerPadding）。
 *
 * [scrollContent]：仅当内容自己不滚动时置 true（菜单行、长文本详情、播放器列表等）， 由本外壳提供唯一的纵向滚动出口；若内容自带
 * LazyColumn/verticalScroll 必须保持 false， 内层若必须独立滚动（如字段列表），必须给它明确的高度上限。
 *
 * [groupContent]：裸内容置 true，提供卡片外边距及内容内边距。 原生 Item 分组应自行使用 RoundedColumn，行组件负责其内边距。 底部大按钮一律通过
 * [footer] 提供，位于 RoundedColumn 外；正文只包含信息和配置。
 *
 * [allowDismiss] = false 时**真正禁止关闭**：系统返回键、点击外部、下拉手势与 hideable 全部关闭（而不是只吞掉 onDismissRequest
 * 回调），用于"进行中不允许打断"的进度弹层。
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
                // 进行中的弹层：返回键、外部点击、下拉手势全部关闭，避免"看起来取消了其实还在跑"。
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
