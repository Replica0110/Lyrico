package com.lonx.lyrico.ui.components.crop

import com.lonx.lyrico.ui.components.icons.LucideIcons
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.base.SaltBottomSheet
import com.lonx.lyrico.ui.components.base.SaltIconButton
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.innerPadding
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** The crop workflow is shared; each engine keeps its own sheet chrome and confirm affordance. */
@Composable
fun ImageCropperSheet(
    show: Boolean,
    bitmap: Bitmap?,
    title: String,
    onCrop: (Bitmap) -> Unit,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit
) {
    val state = bitmap?.let { rememberImageCropperState(it) }
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltBottomSheet(
            show = show,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            // 裁剪画布不应放进 RoundedColumn 分组，也不自滚，直接铺满内容区。
            groupContent = false,
            header = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ItemOuterTitle(text = title) }
                    SaltIconButton(LucideIcons.Check, stringResource(R.string.confirm),
                        enabled = state != null, onClick = {
                            state?.crop()?.let(onCrop)
                            onDismissRequest()
                        })
                }
            },
        ) {
            if (state != null) {
                // 裁剪画布不再贴边：外壳左右和底部带安全区边距，画布铺满内容区时会被截掉四角，
                // 这里留一档与 RoundedColumn 相同的边距，保证裁剪画面和裁剪框的边角完整。
                ImageCropper(state, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            } else Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = SaltTheme.colors.highlight,
                    trackColor = SaltTheme.colors.stroke,
                )
            }
        }
        return
    }
    WindowBottomSheet(
        show = show, title = title, enableNestedScroll = false,
        onDismissRequest = onDismissRequest, onDismissFinished = onDismissFinished,
        endAction = {
            IconButton(enabled = state != null, onClick = {
                state?.crop()?.let(onCrop)
                onDismissRequest()
            }) { Icon(LucideIcons.Check, stringResource(R.string.confirm)) }
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            if (state != null) ImageCropper(state, Modifier.fillMaxWidth())
            else Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
