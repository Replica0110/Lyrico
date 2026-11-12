package com.lonx.lyrico.ui.components.cover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.LucideIcons
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

/**
 * 封面全屏查看，用于长按封面时绕过顶部栏、浮层查看完整图片。
 * 支持双指缩放和双击放大，单击或返回键关闭。
 */
@Composable
fun CoverViewerDialog(
    model: Any?,
    contentDescription: String?,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val zoomState = rememberZoomState(maxScale = 5f)
        var loading by remember(model) { mutableStateOf(true) }
        var failed by remember(model) { mutableStateOf(false) }

        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .zoomable(
                        zoomState = zoomState,
                        zoomEnabled = !loading && !failed,
                        onTap = { onDismissRequest() }
                    ),
                onLoading = { loading = true },
                onSuccess = { state ->
                    loading = false
                    failed = false
                    zoomState.setContentSize(
                        Size(
                            state.result.image.width.toFloat(),
                            state.result.image.height.toFloat()
                        )
                    )
                },
                onError = {
                    loading = false
                    failed = true
                }
            )

            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            if (failed) {
                Icon(
                    imageVector = LucideIcons.Image,
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
            }

            IconButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = LucideIcons.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = Color.White
                )
            }
        }
    }
}
